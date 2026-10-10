#!/bin/sh
# rpc.sh METHOD 'JSON_PARAMS' — вызов моста тестовой копии игры (порт 25598)
G=$HOME/.local/share/pain-launcher/game
T=$(python3 -c "import json;print(json.load(open('$G/config/mcpfabric.config.json'))['token'])")
curl -s -m 15 -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d "{\"method\":\"$1\",\"params\":${2:-{\}}}" http://127.0.0.1:25598/rpc
