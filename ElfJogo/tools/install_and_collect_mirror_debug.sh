#!/usr/bin/env bash
# Instala APK debug no Elf e coleta logs DBG938fa7 do Modo Espelho.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  echo "Nenhum dispositivo adb. Conecte o Sanbot Elf via USB e aceite depuração USB."
  echo "  adb kill-server && adb start-server && adb devices"
  echo "  Wi-Fi (se habilitado no robô): adb connect <ip-do-elf>:5555"
  adb devices
  exit 1
fi

echo "Instalando installDebug..."
./gradlew --no-daemon installDebug

echo ""
echo ">>> Abra Modo Espelho no robô AGORA. Coleta em 5s..."
sleep 5

"${ROOT}/tools/pull_elf_debug_logs.sh"

./gradlew --stop 2>/dev/null || true
