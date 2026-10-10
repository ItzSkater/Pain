#!/bin/sh
# publish_discord.sh "текст" [ФАЙЛ] — пишет в канал Discord через вебхук.
# Адрес вебхука лежит в ~/.config/pain/discord.env (DISCORD_WEBHOOK=https://discord.com/api/webhooks/...).
# Файл прикладывается, только если он не больше 10 МБ — это предел Discord для серверов без буста.
set -e
. "$HOME/.config/pain/discord.env"
[ -n "$DISCORD_WEBHOOK" ] || { echo "нет DISCORD_WEBHOOK в discord.env"; exit 1; }
payload=$(python3 -c 'import json,sys; print(json.dumps({"content": sys.argv[1], "username": "Pain Client"}))' "$1")
if [ -n "$2" ] && [ -f "$2" ] && [ "$(stat -c %s "$2")" -le 10485760 ]; then
  code=$(curl -sS -m 600 -o /dev/null -w '%{http_code}' -F "payload_json=$payload" -F "file=@\"$2\"" "$DISCORD_WEBHOOK")
else
  [ -n "$2" ] && echo "файл не приложен (нет или больше 10 МБ) — отправляю только текст"
  code=$(curl -sS -m 60 -o /dev/null -w '%{http_code}' -H "Content-Type: application/json" -d "$payload" "$DISCORD_WEBHOOK")
fi
case "$code" in 2*) echo "отправлено ($code)";; *) echo "ошибка Discord: $code"; exit 1;; esac
