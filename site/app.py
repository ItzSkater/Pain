"""Сайт и сервер аккаунтов Pain Client. Только стандартная библиотека Python.

Что делает:
  * раздаёт страницы из public/;
  * хранит аккаунты в SQLite (data/pain.db): регистрация, вход, сессии;
  * выдаёт доступ к версии Alpha по ключам (ключи создаёт admin.py);
  * отдаёт сборки клиента из builds/ — Free любому вошедшему, Alpha только с доступом.

Порт берётся из переменной окружения SERVER_PORT, которую задаёт панель хостинга.
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = os.path.join(HERE, "public")
DATA = os.path.join(HERE, "data")
BUILDS = os.path.join(HERE, "builds")
DB_PATH = os.path.join(DATA, "pain.db")
PORT = int(os.environ.get("SERVER_PORT") or os.environ.get("PORT") or 8080)

SESSION_DAYS = 30
MAX_BODY = 16 * 1024
USERNAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
EDITIONS = ("free", "alpha")
# какие роли какую версию могут скачивать
ACCESS = {"free": ("free", "alpha", "admin"), "alpha": ("alpha", "admin")}

_db_lock = threading.Lock()
_rate_lock = threading.Lock()
_attempts = {}
_build_cache = {}


def db():
    con = sqlite3.connect(DB_PATH, timeout=10)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    os.makedirs(DATA, exist_ok=True)
    os.makedirs(BUILDS, exist_ok=True)
    with _db_lock, db() as con:
        con.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                salt BLOB NOT NULL,
                pass_hash BLOB NOT NULL,
                role TEXT NOT NULL DEFAULT 'free',
                created INTEGER NOT NULL,
                last_login INTEGER
            );
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                created INTEGER NOT NULL,
                expires INTEGER NOT NULL,
                client TEXT
            );
            CREATE TABLE IF NOT EXISTS keys (
                code TEXT PRIMARY KEY,
                role TEXT NOT NULL,
                created INTEGER NOT NULL,
                used_by INTEGER,
                used_at INTEGER
            );
            """
        )


def hash_password(password, salt):
    # scrypt: подбор паролей по украденной базе получается медленным и дорогим
    return hashlib.scrypt(password.encode("utf-8"), salt=salt, n=2 ** 14, r=8, p=1, dklen=32)


def token_hash(token):
    # в базе лежит только хэш токена: по украденной базе в чужой аккаунт не войти
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def too_many(ip, limit=12, window=600):
    """Не больше limit попыток входа или регистрации с одного адреса за window секунд."""
    now = time.time()
    with _rate_lock:
        recent = [t for t in _attempts.get(ip, []) if now - t < window]
        recent.append(now)
        _attempts[ip] = recent
        if len(_attempts) > 5000:
            for key in [k for k, v in _attempts.items() if not v or now - v[-1] > window]:
                _attempts.pop(key, None)
        return len(recent) > limit


def build_info(edition):
    """Версия, размер и контрольная сумма сборки; None, если файла нет."""
    path = os.path.join(BUILDS, edition + ".jar")
    if not os.path.isfile(path):
        return None
    stat = os.stat(path)
    cached = _build_cache.get(edition)
    if cached and cached["mtime"] == stat.st_mtime and cached["size"] == stat.st_size:
        return cached
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    version = "?"
    try:
        with open(os.path.join(BUILDS, "versions.json"), encoding="utf-8") as f:
            version = str(json.load(f).get(edition, "?"))
    except (OSError, ValueError):
        pass
    info = {"edition": edition, "version": version, "size": stat.st_size, "sha256": digest.hexdigest(), "mtime": stat.st_mtime, "path": path}
    _build_cache[edition] = info
    return info


class Handler(SimpleHTTPRequestHandler):
    server_version = "Pain"
    sys_version = ""

    # ---------- служебное ----------

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (self.client_ip(), fmt % args))
        sys.stdout.flush()

    def client_ip(self):
        return self.client_address[0]

    def end_headers(self):
        if not self.path.startswith("/api/"):
            static = self.path.endswith((".png", ".jpg", ".webp", ".svg", ".woff2"))
            self.send_header("Cache-Control", "public, max-age=86400" if static else "no-cache")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        super().end_headers()

    def list_directory(self, path):
        self.send_error(404)
        return None

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def fail(self, status, message):
        self.send_json(status, {"ok": False, "error": message})

    def read_json(self):
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY:
            return None
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return None
        return data if isinstance(data, dict) else None

    def current_user(self):
        header = self.headers.get("Authorization") or ""
        if not header.startswith("Bearer "):
            return None
        token = header[7:].strip()
        if not token or len(token) > 200:
            return None
        with _db_lock, db() as con:
            row = con.execute(
                "SELECT u.id, u.username, u.role, u.created, s.expires FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?",
                (token_hash(token),),
            ).fetchone()
            if row is None:
                return None
            if row["expires"] < time.time():
                con.execute("DELETE FROM sessions WHERE token_hash = ?", (token_hash(token),))
                return None
            return dict(row)

    def new_session(self, con, user_id):
        token = secrets.token_urlsafe(32)
        now = int(time.time())
        client = (self.headers.get("User-Agent") or "")[:80]
        con.execute(
            "INSERT INTO sessions (token_hash, user_id, created, expires, client) VALUES (?, ?, ?, ?, ?)",
            (token_hash(token), user_id, now, now + SESSION_DAYS * 86400, client),
        )
        # у одного аккаунта остаются десять последних входов, старые сессии гаснут
        con.execute(
            "DELETE FROM sessions WHERE user_id = ? AND token_hash NOT IN (SELECT token_hash FROM sessions WHERE user_id = ? ORDER BY created DESC LIMIT 10)",
            (user_id, user_id),
        )
        return token

    @staticmethod
    def public_user(user):
        return {"username": user["username"], "role": user["role"], "created": user["created"], "alpha": user["role"] in ACCESS["alpha"]}

    # ---------- маршруты ----------

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/me":
            user = self.current_user()
            if user is None:
                return self.fail(401, "Нужно войти")
            return self.send_json(200, {"ok": True, "user": self.public_user(user)})
        if path == "/api/builds":
            out = {}
            for edition in EDITIONS:
                info = build_info(edition)
                if info:
                    out[edition] = {"version": info["version"], "size": info["size"], "sha256": info["sha256"]}
            return self.send_json(200, {"ok": True, "builds": out})
        if path.startswith("/api/download/"):
            return self.download(path[len("/api/download/"):])
        if path.startswith("/api/"):
            return self.fail(404, "Нет такого метода")
        if path.startswith(("/data", "/builds")) or "/." in path:
            return self.send_error(404)
        return super().do_GET()

    def do_HEAD(self):
        if self.path.startswith("/api/"):
            return self.send_error(405)
        return super().do_HEAD()

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        routes = {"/api/register": self.register, "/api/login": self.login, "/api/logout": self.logout, "/api/redeem": self.redeem, "/api/password": self.change_password}
        handler = routes.get(path)
        if handler is None:
            return self.fail(404, "Нет такого метода")
        return handler()

    def register(self):
        if too_many(self.client_ip()):
            return self.fail(429, "Слишком много попыток, подожди десять минут")
        data = self.read_json()
        if data is None:
            return self.fail(400, "Неверный запрос")
        username = str(data.get("username") or "").strip()
        password = str(data.get("password") or "")
        if not USERNAME_RE.match(username):
            return self.fail(400, "Ник: 3–16 символов, латиница, цифры и _")
        if len(password) < 8 or len(password) > 128:
            return self.fail(400, "Пароль: от 8 до 128 символов")
        salt = secrets.token_bytes(16)
        hashed = hash_password(password, salt)
        with _db_lock, db() as con:
            try:
                cur = con.execute("INSERT INTO users (username, salt, pass_hash, role, created) VALUES (?, ?, ?, 'free', ?)", (username, salt, hashed, int(time.time())))
            except sqlite3.IntegrityError:
                return self.fail(409, "Такой ник уже занят")
            token = self.new_session(con, cur.lastrowid)
            user = dict(con.execute("SELECT id, username, role, created FROM users WHERE id = ?", (cur.lastrowid,)).fetchone())
        self.send_json(200, {"ok": True, "token": token, "user": self.public_user(user)})

    def login(self):
        if too_many(self.client_ip()):
            return self.fail(429, "Слишком много попыток, подожди десять минут")
        data = self.read_json()
        if data is None:
            return self.fail(400, "Неверный запрос")
        username = str(data.get("username") or "").strip()
        password = str(data.get("password") or "")
        with _db_lock, db() as con:
            row = con.execute("SELECT id, username, role, created, salt, pass_hash FROM users WHERE username = ?", (username,)).fetchone()
        # хэш считаем и для несуществующего ника, чтобы по времени ответа нельзя было узнать, есть ли такой аккаунт
        salt = row["salt"] if row else b"\0" * 16
        hashed = hash_password(password[:128], salt)
        if row is None or not hmac.compare_digest(hashed, row["pass_hash"]):
            return self.fail(401, "Неверный ник или пароль")
        with _db_lock, db() as con:
            con.execute("UPDATE users SET last_login = ? WHERE id = ?", (int(time.time()), row["id"]))
            token = self.new_session(con, row["id"])
        self.send_json(200, {"ok": True, "token": token, "user": self.public_user(dict(row))})

    def logout(self):
        header = self.headers.get("Authorization") or ""
        if header.startswith("Bearer "):
            with _db_lock, db() as con:
                con.execute("DELETE FROM sessions WHERE token_hash = ?", (token_hash(header[7:].strip()),))
        self.send_json(200, {"ok": True})

    def change_password(self):
        user = self.current_user()
        if user is None:
            return self.fail(401, "Нужно войти")
        if too_many(self.client_ip()):
            return self.fail(429, "Слишком много попыток, подожди десять минут")
        data = self.read_json() or {}
        old = str(data.get("old") or "")
        new = str(data.get("new") or "")
        if len(new) < 8 or len(new) > 128:
            return self.fail(400, "Пароль: от 8 до 128 символов")
        with _db_lock, db() as con:
            row = con.execute("SELECT salt, pass_hash FROM users WHERE id = ?", (user["id"],)).fetchone()
            if not hmac.compare_digest(hash_password(old[:128], row["salt"]), row["pass_hash"]):
                return self.fail(401, "Старый пароль неверный")
            salt = secrets.token_bytes(16)
            con.execute("UPDATE users SET salt = ?, pass_hash = ? WHERE id = ?", (salt, hash_password(new, salt), user["id"]))
            # после смены пароля все прежние входы перестают действовать
            con.execute("DELETE FROM sessions WHERE user_id = ?", (user["id"],))
            token = self.new_session(con, user["id"])
        self.send_json(200, {"ok": True, "token": token})

    def redeem(self):
        user = self.current_user()
        if user is None:
            return self.fail(401, "Нужно войти")
        if too_many(self.client_ip()):
            return self.fail(429, "Слишком много попыток, подожди десять минут")
        data = self.read_json() or {}
        code = str(data.get("key") or "").strip().upper()
        if not code or len(code) > 64:
            return self.fail(400, "Введи ключ")
        with _db_lock, db() as con:
            row = con.execute("SELECT code, role, used_by FROM keys WHERE code = ?", (code,)).fetchone()
            if row is None or row["used_by"] is not None:
                return self.fail(404, "Ключ не найден или уже использован")
            if user["role"] == "admin":
                return self.fail(400, "У администратора уже есть весь доступ")
            con.execute("UPDATE keys SET used_by = ?, used_at = ? WHERE code = ?", (user["id"], int(time.time()), code))
            con.execute("UPDATE users SET role = ? WHERE id = ?", (row["role"], user["id"]))
            user["role"] = row["role"]
        self.send_json(200, {"ok": True, "user": self.public_user(user)})

    def download(self, edition):
        if edition not in EDITIONS:
            return self.fail(404, "Нет такой версии")
        user = self.current_user()
        if user is None:
            return self.fail(401, "Нужно войти")
        if user["role"] not in ACCESS[edition]:
            return self.fail(403, "У аккаунта нет доступа к этой версии")
        info = build_info(edition)
        if info is None:
            return self.fail(404, "Сборка ещё не выложена")
        self.send_response(200)
        self.send_header("Content-Type", "application/java-archive")
        self.send_header("Content-Length", str(info["size"]))
        self.send_header("Content-Disposition", 'attachment; filename="Pain-%s-%s.jar"' % (edition.capitalize(), info["version"]))
        self.send_header("X-Pain-Sha256", info["sha256"])
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        with open(info["path"], "rb") as f:
            while True:
                chunk = f.read(1 << 16)
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return


def main():
    init_db()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), partial(Handler, directory=PUBLIC))
    server.daemon_threads = True
    print("Pain site: http://0.0.0.0:%d  (files: %s)" % (PORT, PUBLIC), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
