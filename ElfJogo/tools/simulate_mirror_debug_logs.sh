#!/usr/bin/env bash
# Simula pipeline Modo Espelho (depth-blob + EMA) e grava .cursor/debug-938fa7.log
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

OUT="${ROOT}/.cursor/debug-938fa7.log"
BUILD_OUT="${ROOT}/app/build/skeleton-harness-debug-938fa7.log"
rm -f "${OUT}" "${BUILD_OUT}"
mkdir -p "${ROOT}/.cursor" "${ROOT}/app/build"

./gradlew --no-daemon testDebugUnitTest \
  --tests "com.felipe.elftemplate.tracking.SkeletonPipelineHarnessTest" \
  -Pelf.skeleton.harness=1 \
  --rerun-tasks 2>&1 | tail -15

if [[ -f "${BUILD_OUT}" ]]; then
  if cp "${BUILD_OUT}" "${OUT}" 2>/dev/null; then
    echo "Copiado para ${OUT}"
  else
    echo "Log em: ${BUILD_OUT} (copie manualmente se .cursor for somente leitura)"
    OUT="${BUILD_OUT}"
  fi
fi

if [[ ! -f "${OUT}" ]]; then
  LINES=0
else
  LINES="$(wc -l < "${OUT}")"
fi
echo "Harness NDJSON: ${OUT} (${LINES} linhas)"
./gradlew --stop 2>/dev/null || true
