#!/usr/bin/env bash
# Importa logcat ou NDJSON manual → .cursor/debug-938fa7.log
# Uso: ./tools/import_debug_logcat.sh logcat.txt
#      adb logcat -d -s DBG938fa7 | ./tools/import_debug_logcat.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/.cursor/debug-938fa7.log"
SRC="${1:-}"

rm -f "${OUT}" "${OUT}.tmp"

extract_lines() {
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      *DBG938fa7:*)
        echo "${line#*DBG938fa7: }" >> "${OUT}.tmp"
        ;;
      *'{"sessionId":"938fa7'*)
        echo "$line" >> "${OUT}.tmp"
        ;;
    esac
  done
}

if [[ -n "${SRC}" && -f "${SRC}" ]]; then
  echo "Importando de: ${SRC}"
  extract_lines < "${SRC}"
elif [[ ! -t 0 ]]; then
  echo "Lendo stdin..."
  extract_lines
else
  echo "Uso: $0 logcat.txt   OU   adb logcat -d -s DBG938fa7 | $0"
  exit 1
fi

if [[ -f "${OUT}.tmp" ]]; then
  mv "${OUT}.tmp" "${OUT}"
else
  : > "${OUT}"
fi

LINES="$(wc -l < "${OUT}")"
echo "NDJSON em: ${OUT} (${LINES} linhas)"
if [[ "${LINES}" -eq 0 ]]; then
  echo "Nenhuma linha DBG938fa7 — filtre tag DBG938fa7 no logcat."
fi
