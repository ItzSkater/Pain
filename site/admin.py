"""Управление аккаунтами и ключами Pain. Запускается в консоли сервера.

  python admin.py keys alpha 5        создать 5 ключей на версию Alpha
  python admin.py keys                показать все ключи
  python admin.py users               показать аккаунты
  python admin.py role НИК alpha      выдать роль (free, alpha, admin)
  python admin.py delete НИК          удалить аккаунт
"""
import secrets
import sqlite3
import sys
import time

import app

ROLES = ("free", "alpha", "admin")


def when(ts):
    return time.strftime("%d.%m.%Y %H:%M", time.localtime(ts)) if ts else "—"


def main(argv):
    app.init_db()
    con = sqlite3.connect(app.DB_PATH)
    con.row_factory = sqlite3.Row
    cmd = argv[1] if len(argv) > 1 else ""
    if cmd == "keys" and len(argv) >= 3:
        role = argv[2]
        count = int(argv[3]) if len(argv) > 3 else 1
        if role not in ("alpha",):
            return print("Ключи выдаются только на роль alpha")
        for _ in range(count):
            # без похожих символов (0/O, 1/I), чтобы ключ можно было перепечатать с экрана
            alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            code = "PAIN-" + "-".join("".join(secrets.choice(alphabet) for _ in range(5)) for _ in range(3))
            con.execute("INSERT INTO keys (code, role, created) VALUES (?, ?, ?)", (code, role, int(time.time())))
            print(code)
        con.commit()
    elif cmd == "keys":
        for row in con.execute("SELECT k.code, k.role, k.created, k.used_at, u.username FROM keys k LEFT JOIN users u ON u.id = k.used_by ORDER BY k.created"):
            state = "использован: %s, %s" % (row["username"], when(row["used_at"])) if row["used_at"] else "свободен"
            print("%s  %-5s  создан %s  %s" % (row["code"], row["role"], when(row["created"]), state))
    elif cmd == "users":
        for row in con.execute("SELECT username, role, created, last_login FROM users ORDER BY created"):
            print("%-16s  %-5s  создан %s  последний вход %s" % (row["username"], row["role"], when(row["created"]), when(row["last_login"])))
    elif cmd == "role" and len(argv) == 4:
        if argv[3] not in ROLES:
            return print("Роли: " + ", ".join(ROLES))
        cur = con.execute("UPDATE users SET role = ? WHERE username = ?", (argv[3], argv[2]))
        con.commit()
        print("Готово" if cur.rowcount else "Нет такого аккаунта")
    elif cmd == "delete" and len(argv) == 3:
        row = con.execute("SELECT id FROM users WHERE username = ?", (argv[2],)).fetchone()
        if row is None:
            return print("Нет такого аккаунта")
        con.execute("DELETE FROM sessions WHERE user_id = ?", (row["id"],))
        con.execute("DELETE FROM users WHERE id = ?", (row["id"],))
        con.commit()
        print("Удалён")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
