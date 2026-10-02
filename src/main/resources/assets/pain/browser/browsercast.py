#!/usr/bin/env python3
"""Pain Browser3D helper (niri).

Снимает окно уже открытого браузера через org.gnome.Mutter.ScreenCast (его реализует niri)
и PipeWire. Кадры приходят как линейный DMA-BUF: читаем его напрямую через mmap,
уменьшаем и пишем в stdout: 12 байт (ширина, высота, длина; big-endian) + RGBA.
Статус и ошибки — строками в stderr: "STATUS ..." / "ERROR ...".
"""
import argparse
import json
import mmap
import re
import struct
import subprocess
import sys
import time

import gi

gi.require_version("Gst", "1.0")
gi.require_version("Gio", "2.0")
gi.require_version("GstVideo", "1.0")
gi.require_version("GstAllocators", "1.0")
from gi.repository import Gio, GLib, Gst, GstAllocators, GstVideo  # noqa: E402
import numpy as np  # noqa: E402

APPS = {
    "zen": r"zen",
    "firefox": r"firefox|librewolf|floorp",
    "chromium": r"chromium",
    "chrome": r"google-chrome|chrome",
}


def log(kind, msg):
    sys.stderr.write("%s %s\n" % (kind, msg))
    sys.stderr.flush()


def find_window(app):
    out = subprocess.run(["niri", "msg", "-j", "windows"], capture_output=True, text=True, timeout=5)
    pattern = re.compile(APPS.get(app, app), re.I)
    windows = [w for w in json.loads(out.stdout or "[]") if pattern.search(w.get("app_id") or "")]
    if app == "chrome":
        windows = [w for w in windows if "chromium" not in (w.get("app_id") or "").lower()]
    return windows[0] if windows else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--app", default="zen")
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--max-width", type=int, default=1280)
    args = ap.parse_args()
    Gst.init(None)
    try:
        window = find_window(args.app)
    except Exception as e:
        log("ERROR", "niri недоступен: %s" % e)
        sys.exit(2)
    if window is None:
        log("ERROR", "окно браузера %s не найдено — открой его" % args.app)
        sys.exit(2)
    log("STATUS", "окно: %s" % (window.get("title") or "")[:60])

    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    name = "org.gnome.Mutter.ScreenCast"
    try:
        session = bus.call_sync(name, "/org/gnome/Mutter/ScreenCast", name, "CreateSession",
                                GLib.Variant("(a{sv})", ({},)), GLib.VariantType("(o)"),
                                Gio.DBusCallFlags.NONE, 5000, None).unpack()[0]
        stream = bus.call_sync(name, session, name + ".Session", "RecordWindow",
                               GLib.Variant("(a{sv})", ({"window-id": GLib.Variant("t", window["id"])},)),
                               GLib.VariantType("(o)"), Gio.DBusCallFlags.NONE, 5000, None).unpack()[0]
    except GLib.Error as e:
        log("ERROR", "захват окна недоступен: %s" % e.message)
        sys.exit(3)

    loop = GLib.MainLoop()
    out = sys.stdout.buffer
    state = {"pipeline": None, "frames": 0, "last": 0.0, "maps": {}}
    interval = 1.0 / max(1, args.fps)

    def on_sample(sink):
        sample = sink.emit("pull-sample")
        if sample is None:
            return Gst.FlowReturn.OK
        now = time.monotonic()
        if now - state["last"] < interval:
            return Gst.FlowReturn.OK
        state["last"] = now
        buf = sample.get_buffer()
        caps = sample.get_caps().get_structure(0)
        w = caps.get_value("width")
        h = caps.get_value("height")
        meta = GstVideo.buffer_get_video_meta(buf)
        mem = buf.peek_memory(0)
        if meta is None or not GstAllocators.is_dmabuf_memory(mem):
            return Gst.FlowReturn.OK
        fd = GstAllocators.dmabuf_memory_get_fd(mem)
        stride = meta.stride[0]
        offset = meta.offset[0]
        need = offset + stride * h
        try:
            key = (fd, need)
            mm = state["maps"].get(key)
            if mm is None:
                mm = mmap.mmap(fd, need, mmap.MAP_SHARED, mmap.PROT_READ)
                state["maps"][key] = mm
            src = np.frombuffer(mm, dtype=np.uint8, count=stride * h, offset=offset).reshape(h, stride)
            frame = src[:, : w * 4].reshape(h, w, 4)
            # уменьшаем целым шагом — быстро и без зависимостей
            step = max(1, -(-w // args.max_width))
            if step > 1:
                frame = frame[::step, ::step]
            rgba = np.ascontiguousarray(frame[:, :, [2, 1, 0, 3]])
            rgba[:, :, 3] = 255
            fh, fw = rgba.shape[0], rgba.shape[1]
            data = rgba.tobytes()
            out.write(struct.pack(">III", fw, fh, len(data)))
            out.write(data)
            out.flush()
        except (BrokenPipeError, OSError):
            loop.quit()
            return Gst.FlowReturn.EOS
        state["frames"] += 1
        if state["frames"] == 1:
            log("STATUS", "идёт трансляция %dx%d" % (fw, fh))
        return Gst.FlowReturn.OK

    def on_stream(_conn, _sender, _path, _iface, _signal, params):
        node = params.unpack()[0]
        # XR24 без модификатора = линейный буфер, его можно читать через mmap
        desc = ("pipewiresrc path=%d do-timestamp=true ! video/x-raw(memory:DMABuf),format=DMA_DRM,drm-format=XR24 "
                "! appsink name=sink emit-signals=true max-buffers=2 drop=true sync=false" % node)
        pipeline = Gst.parse_launch(desc)
        pipeline.get_by_name("sink").connect("new-sample", on_sample)
        gbus = pipeline.get_bus()
        gbus.add_signal_watch()

        def on_message(_b, msg):
            if msg.type == Gst.MessageType.ERROR:
                err, _dbg = msg.parse_error()
                log("ERROR", "поток: %s" % err.message)
                loop.quit()
            elif msg.type == Gst.MessageType.EOS:
                log("ERROR", "окно браузера закрыто")
                loop.quit()

        gbus.connect("message", on_message)
        pipeline.set_state(Gst.State.PLAYING)
        state["pipeline"] = pipeline

    bus.signal_subscribe(name, name + ".Stream", "PipeWireStreamAdded", stream, None,
                         Gio.DBusSignalFlags.NONE, on_stream)
    bus.call_sync(name, session, name + ".Session", "Start", None, None, Gio.DBusCallFlags.NONE, 5000, None)

    # stdin закрыт — игра закрылась или модуль выключили
    def on_stdin(_chan, _cond):
        loop.quit()
        return False

    GLib.io_add_watch(GLib.IOChannel.unix_new(0), GLib.IO_HUP | GLib.IO_ERR | GLib.IO_IN, on_stdin)
    try:
        loop.run()
    finally:
        if state["pipeline"] is not None:
            state["pipeline"].set_state(Gst.State.NULL)
        try:
            bus.call_sync(name, session, name + ".Session", "Stop", None, None, Gio.DBusCallFlags.NONE, 2000, None)
        except GLib.Error:
            pass


if __name__ == "__main__":
    main()
