#!/usr/bin/env bash
# Coleta logs de debug do Modo Espelho (sessão 938fa7) do Sanbot Elf via adb.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/.cursor/debug-938fa7.log"
PKG="com.felipe.elftemplate"
CACHE_REL="cache/debug-938fa7.log"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb não encontrado — instale android-tools."
  exit 1
fi

DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "${DEVICES}" ]]; then
  echo "Nenhum dispositivo adb conectado. Conecte o Elf via USB e aceite depuração."
  adb devices
  exit 1
fi

echo "Dispositivo: ${DEVICES%%$'\n'*}"
rm -f "${OUT}" "${OUT}.tmp"
echo "Capturando logcat DBG938fa7 (15s) — mova os braços no Modo Espelho..."
adb logcat -c
timeout 15 adb logcat -s DBG938fa7 > "${OUT}.raw" || true

# Logcat prefix: "... I DBG938fa7: {json}" — extrair JSON NDJSON
while IFS= read -r line; do
  case "$line" in
    *DBG938fa7:*)
      json="${line#*DBG938fa7: }"
      echo "$json" >> "${OUT}.tmp"
      ;;
    *'{"sessionId":"938fa7'*)
      echo "$line" >> "${OUT}.tmp"
      ;;
  esac
done < "${OUT}.raw"
if [[ -f "${OUT}.tmp" ]]; then
  mv -f "${OUT}.tmp" "${OUT}"
else
  : > "${OUT}"
fi
rm -f "${OUT}.raw"

echo "Tentando ler cache do app (run-as)..."
if adb exec-out run-as "${PKG}" cat "${CACHE_REL}" >> "${OUT}" 2>/dev/null; then
  echo "Cache via run-as OK."
else
  echo "run-as falhou — tentando adb pull..."
  adb pull "/data/data/${PKG}/${CACHE_REL}" "${OUT}.device" 2>/dev/null || true
  if [[ -f "${OUT}.device" ]]; then
    cat "${OUT}.device" >> "${OUT}"
    rm -f "${OUT}.device"
  fi
fi

LINES="$(wc -l < "${OUT}" 2>/dev/null || echo 0)"
echo "Logs em: ${OUT} (${LINES} linhas)"
if [[ "${LINES}" -eq 0 ]]; then
  echo "Sem linhas — confira Modo Espelho ativo e APK com DebugTrace instalado."
  exit 1
fi
