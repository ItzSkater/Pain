#!/bin/sh
# publish_tg.sh ФАЙЛ "подпись" — отправляет файл в телеграм-канал от имени бота.
# Токен и канал лежат в ~/.config/pain/telegram.env (TG_TOKEN=..., TG_CHAT=@канал или -100...).
set -e
. "$HOME/.config/pain/telegram.env"
[ -n "$TG_TOKEN" ] && [ -n "$TG_CHAT" ] || { echo "нет TG_TOKEN или TG_CHAT в telegram.env"; exit 1; }
[ -f "$1" ] || { echo "нет файла: $1"; exit 1; }
size=$(stat -c %s "$1")
[ "$size" -le 52428800 ] || { echo "файл больше 50 МБ — бот такой не отправит"; exit 1; }
curl -sS -m 600 --form-string "chat_id=$TG_CHAT" -F "document=@\"$1\"" --form-string "caption=$2" \
  "https://api.telegram.org/bot$TG_TOKEN/sendDocument" | python3 -c "
import sys,json
r=json.load(sys.stdin)
print('отправлено, сообщение', r['result']['message_id']) if r.get('ok') else (print('ошибка:', r.get('description')), sys.exit(1))"
