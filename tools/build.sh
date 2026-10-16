#!/usr/bin/env bash
# Безопасная сборка Pain: проверяет свободную память, собирает под жёстким лимитом и кладёт джарку в игру.
# Запуск: tools/build.sh           — собрать и положить в mods
#         tools/build.sh --no-deploy — только собрать
#         FORCE=1 tools/build.sh   — собрать, даже если памяти мало (на свой риск)
set -u
cd "$(dirname "$0")/.."

MIN_FREE_MB=${MIN_FREE_MB:-5000}                      # меньше — не собираем: сборка плюс игра вешают систему
MODS="$HOME/.minecraftx/instances/Pain/mods"
version=$(sed -n 's/^mod_version=//p' gradle.properties)
jar="build/libs/Pain.v$version.jar"

free_mb=$(free -m | awk 'NR==2{print $7}')
if [ "$free_mb" -lt "$MIN_FREE_MB" ] && [ "${FORCE:-0}" != 1 ]; then
   echo "Свободно $free_mb МБ, нужно хотя бы $MIN_FREE_MB. Закрой игру или запусти с FORCE=1."
   exit 2
fi

started=$(date +%s)
log=$(mktemp)
# своп запрещён: если сборка не влезет в лимит, убьётся она сама, а не вся система
systemd-run --user --scope -q -p MemoryMax=2500M -p MemorySwapMax=0 -p CPUQuota=400% \
   nice -n 15 ./gradlew build --offline -q --no-daemon >"$log" 2>&1
code=$?

if [ $code -ne 0 ] || grep -q "error:\|FAILED" "$log"; then
   echo "Сборка не прошла (код $code):"
   grep -A6 "error:\|FAILED\|Killed\|OutOfMemory" "$log" | head -40
   rm -f "$log"
   exit 1
fi
rm -f "$log"

if [ ! -f "$jar" ]; then
   echo "Сборка прошла, но джарки $jar нет — проверь mod_version в gradle.properties."
   exit 1
fi
if ! unzip -tq "$jar" >/dev/null 2>&1; then
   echo "Джарка $jar повреждена, в игру не кладу."
   exit 1
fi

[ "$(stat -c %Y "$jar")" -lt "$started" ] && echo "Исходники не менялись — джарка прежняя."
echo "Собрано: $jar ($(( $(stat -c %s "$jar") / 1048576 )) МБ, $(( $(date +%s) - started )) с)"
if [ "${1:-}" != "--no-deploy" ]; then
   # атомарная подмена: запущенная игра дочитывает старый файл и не ловит битую джарку
   cp "$jar" "$MODS/.Pain.v$version.jar.tmp" && mv -f "$MODS/.Pain.v$version.jar.tmp" "$MODS/Pain.v$version.jar" && echo "Положил в $MODS — перезапусти игру."
fi
