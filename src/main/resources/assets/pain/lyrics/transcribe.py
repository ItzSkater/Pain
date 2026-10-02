#!/usr/bin/env python3
"""Pain MusicLyrics helper.

Captures the system audio output (default sink monitor), runs speech-to-text on it
and prints one JSON object per line to stdout:
  {"text": "...", "final": true}      recognised phrase
  {"status": "..."}                   progress / info
  {"error": "..."}                    fatal problem (process exits afterwards)

Engines: faster-whisper (better on music) or vosk (lighter). Nothing is stored.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import zipfile
import urllib.request

RATE = 16000
BASE = os.path.join(os.path.expanduser("~"), ".config", "pain", "lyrics")
VOSK_MODELS = {
    "ru": "vosk-model-small-ru-0.22",
    "en": "vosk-model-small-en-us-0.15",
    "de": "vosk-model-small-de-0.15",
    "es": "vosk-model-small-es-0.42",
    "fr": "vosk-model-small-fr-0.22",
}


def emit(**obj):
    try:
        sys.stdout.write(json.dumps(obj, ensure_ascii=False) + "\n")
        sys.stdout.flush()
    except (BrokenPipeError, OSError):
        os._exit(0)  # мод закрыл пайп — тихо выходим


def open_capture():
    """Returns a Popen whose stdout yields raw s16le mono 16 kHz audio."""
    if shutil.which("parec"):
        cmd = ["parec", "-d", "@DEFAULT_MONITOR@", "--format=s16le",
               "--rate=%d" % RATE, "--channels=1", "--latency-msec=100"]
    elif shutil.which("pw-record"):
        cmd = ["pw-record", "--format", "s16", "--rate", str(RATE), "--channels", "1",
               "-P", "{ stream.capture.sink=true }", "-"]
    else:
        emit(error="no audio capture tool (install pipewire-pulse / libpulse for parec)")
        sys.exit(1)
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


def rms(chunk):
    import numpy as np
    data = np.frombuffer(chunk, dtype=np.int16).astype(np.float32) / 32768.0
    if data.size == 0:
        return 0.0
    return float(np.sqrt(np.mean(data * data)))


PAUSED = threading.Event()
RESET = threading.Event()


def commands():
    """Управление от мода через stdin: pause / resume / reset (смена трека)."""
    for cmd in sys.stdin:
        cmd = cmd.strip()
        emit(debug="cmd %s" % cmd)
        if cmd == "pause":
            PAUSED.set()
        elif cmd == "resume":
            PAUSED.clear()
        elif cmd == "reset":
            RESET.set()
    os._exit(0)  # stdin закрыт — игра закрылась, не оставляем сироту


def vosk_model_path(lang):
    return os.path.join(BASE, "models", VOSK_MODELS.get(lang, VOSK_MODELS["ru"]))


def ensure_vosk_model(lang, quiet=False):
    name = VOSK_MODELS.get(lang, VOSK_MODELS["ru"])
    models = os.path.join(BASE, "models")
    path = os.path.join(models, name)
    if os.path.isdir(path):
        return path
    os.makedirs(models, exist_ok=True)
    url = "https://alphacephei.com/vosk/models/%s.zip" % name
    if not quiet:
        emit(status="downloading %s" % name)
    zpath = path + ".zip.part"
    for attempt in range(5):
        try:
            urllib.request.urlretrieve(url, zpath)
            break
        except Exception as e:
            emit(debug="vosk model download failed (%s), retry %d/5" % (e, attempt + 1))
            time.sleep(3)
    else:
        raise RuntimeError("could not download %s (internet?)" % name)
    with zipfile.ZipFile(zpath) as z:
        z.extractall(models)
    os.remove(zpath)
    return path


def run_vosk(args, cap):
    try:
        from vosk import Model, KaldiRecognizer, SetLogLevel
    except ImportError:
        emit(error="vosk not installed: pip install vosk")
        sys.exit(1)
    SetLogLevel(-1)
    recs = {}
    rec_lock = threading.Lock()

    def add(lang, quiet=False):
        rec = KaldiRecognizer(Model(ensure_vosk_model(lang, quiet)), RATE)
        rec.SetWords(True)
        with rec_lock:
            recs[lang] = rec

    if args.lang == "auto":
        # auto: слушаем сразу ru и en, показываем того, кто увереннее
        first = "ru" if os.path.isdir(vosk_model_path("ru")) or not os.path.isdir(vosk_model_path("en")) else "en"
        add(first)
        other = "en" if first == "ru" else "ru"

        def later():
            try:
                add(other, quiet=True)
                emit(debug="vosk: second language %s ready" % other)
            except Exception as e:
                emit(debug="vosk: %s model unavailable: %s" % (other, e))

        threading.Thread(target=later, daemon=True).start()
    else:
        add(args.lang)
    emit(status="ready vosk %s" % args.lang)
    score = {}
    last_partial = ""
    while True:
        chunk = cap.stdout.read(RATE // 5 * 2)  # 200 ms
        if not chunk:
            break
        with rec_lock:
            active = dict(recs)
        if RESET.is_set():
            RESET.clear()
            score.clear()
            last_partial = ""
            for rec in active.values():
                rec.Reset()
        if PAUSED.is_set():
            continue
        best = max(active, key=lambda l: score.get(l, 0.5))
        for lang, rec in active.items():
            if rec.AcceptWaveform(chunk):
                res = json.loads(rec.Result())
                text = res.get("text", "").strip()
                words = res.get("result", [])
                if not text or not words:
                    continue
                conf = sum(w.get("conf", 0.0) for w in words) / len(words)
                score[lang] = 0.6 * score.get(lang, 0.5) + 0.4 * conf
                best = max(active, key=lambda l: score.get(l, 0.5))
                if lang == best and conf >= 0.45:
                    emit(text=text, final=True)
                    last_partial = ""
            elif lang == best:
                partial = json.loads(rec.PartialResult()).get("partial", "").strip()
                if partial and partial != last_partial:
                    last_partial = partial
                    emit(text=partial, final=False)


def load_whisper(name, local):
    from faster_whisper import WhisperModel
    for device in ("auto", "cpu"):
        try:
            return WhisperModel(name, device=device, compute_type="int8", local_files_only=local)
        except Exception:
            pass
    return None


def run_whisper(args, cap):
    try:
        from faster_whisper import WhisperModel  # noqa: F401
        import numpy as np
    except ImportError:
        emit(error="faster-whisper not installed: pip install faster-whisper numpy")
        sys.exit(1)
    emit(status="loading whisper %s" % args.model)
    holder = {"model": load_whisper(args.model, True), "name": args.model}
    if holder["model"] is None:
        # нужной модели ещё нет на диске: работаем на той, что есть, нужную качаем в фоне
        for fallback in ("base", "tiny", "small"):
            if fallback != args.model:
                m = load_whisper(fallback, True)
                if m is not None:
                    holder["model"], holder["name"] = m, fallback
                    emit(debug="whisper %s not downloaded yet, using %s meanwhile" % (args.model, fallback))
                    break

        def fetch():
            for attempt in range(20):
                m = load_whisper(args.model, False)
                if m is not None:
                    holder["model"], holder["name"] = m, args.model
                    emit(debug="whisper %s downloaded, switched" % args.model)
                    emit(status="ready whisper %s" % args.model)
                    return
                emit(debug="whisper %s download failed, retry %d/20" % (args.model, attempt + 1))
                time.sleep(10)

        if holder["model"] is None:
            emit(status="downloading whisper %s" % args.model)
            fetch()
        else:
            threading.Thread(target=fetch, daemon=True).start()
    if holder["model"] is None:
        emit(error="could not load whisper model %s (internet?)" % args.model)
        sys.exit(1)
    emit(status="ready whisper %s" % holder["name"])

    window = max(3.0, min(10.0, args.window))
    need = int(RATE * window) * 2
    buf = bytearray()
    lock = threading.Lock()
    stop = threading.Event()

    def reader():
        while not stop.is_set():
            chunk = cap.stdout.read(RATE // 10 * 2)
            if not chunk:
                stop.set()
                break
            with lock:
                buf.extend(chunk)
                # keep at most two windows so we never fall behind
                if len(buf) > need * 2:
                    del buf[: len(buf) - need * 2]

    threading.Thread(target=reader, daemon=True).start()
    lang = None if args.lang == "auto" else args.lang
    votes = {}
    prev = ""
    empty = 0
    junk = ("субтитр", "продолжение следует", "подписывайтесь", "thank you for watching",
            "thanks for watching", "amara.org", "dimatorzok", "редактор субтитров", "спасибо за просмотр")
    while not stop.is_set():
        if RESET.is_set() or PAUSED.is_set():
            with lock:
                buf.clear()
            if RESET.is_set():
                RESET.clear()
                prev = ""
                empty = 0
                if args.lang == "auto":
                    lang = None  # новый трек — язык определяем заново
                    votes.clear()
            if PAUSED.is_set():
                time.sleep(0.1)
                continue
        with lock:
            ready = len(buf) >= need
            if ready:
                chunk = bytes(buf[:need])
                # overlap 1 s so words on the boundary are not cut in half
                del buf[: need - RATE * 2]
        if not ready:
            time.sleep(0.05)
            continue
        if rms(chunk) < args.silence:
            continue
        audio = np.frombuffer(chunk, dtype=np.int16).astype(np.float32) / 32768.0
        segments, info = holder["model"].transcribe(
            audio, language=lang, beam_size=2, vad_filter=False,
            condition_on_previous_text=False, no_speech_threshold=0.55,
            compression_ratio_threshold=2.2, log_prob_threshold=-1.0,
            initial_prompt=prev[-120:] or None)
        parts = []
        for seg in segments:
            if seg.no_speech_prob > 0.6 or seg.avg_logprob < -1.1 or seg.compression_ratio > 2.4:
                continue
            t = seg.text.strip()
            if t and not any(j in t.lower() for j in junk):
                parts.append(t)
        # auto: lock the language once one wins a few confident windows
        if lang is None and info.language_probability > 0.5:
            votes[info.language] = votes.get(info.language, 0) + 1
            best = max(votes, key=votes.get)
            if votes[best] >= 3:
                lang = best
                emit(status="ready whisper %s lang %s" % (args.model, lang))
        text = " ".join(parts).strip()
        if not text:
            empty += 1
            # громко, а слов нет уже ~4 окна — язык мог залипнуть, определяем заново
            if args.lang == "auto" and lang is not None and empty >= 4:
                emit(debug="language %s unlocked (no words)" % lang)
                lang = None
                votes.clear()
                empty = 0
        else:
            empty = 0
        if text and text != prev:
            prev = text
            emit(text=text, final=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", default="whisper", choices=["whisper", "vosk"])
    ap.add_argument("--lang", default="auto")
    ap.add_argument("--model", default="base")
    ap.add_argument("--window", type=float, default=5.0)
    ap.add_argument("--silence", type=float, default=0.004)
    args = ap.parse_args()
    cap = open_capture()
    threading.Thread(target=commands, daemon=True).start()
    try:
        if args.engine == "vosk":
            run_vosk(args, cap)
        else:
            run_whisper(args, cap)
    except KeyboardInterrupt:
        pass
    except Exception as e:  # report and exit, the mod shows it once
        emit(error=str(e))
    finally:
        cap.kill()


if __name__ == "__main__":
    main()
