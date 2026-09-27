#!/usr/bin/env bash
# Gate único para vibe coding: alocação, memória, contrato de eixos, testes.
set -e

echo "========================================================"
echo "INICIANDO VALIDACAO (harness de visao + qualidade)"
echo "========================================================"

echo "ALLOC/MEM-GUARD (Checkstyle)..."
./gradlew --no-daemon checkstyleAlloc checkstyleMemory

echo "Testes unitarios + contrato de eixos + golden..."
./gradlew --no-daemon testDebugUnitTest

echo "Regressao: fallback de mao no quadril..."
if grep -E 'leftHandY\s*=\s*result\.playerCentroidY\s*\+\s*0\.18' \
  app/src/main/java/com/felipe/elftemplate/tracking/DepthBlobAnatomy.java; then
  echo "FALLBACK PROIBIDO: mao ancorada em playerCentroidY+0.18"
  exit 1
fi

echo "Regressao: EMA de cotovelo..."
if ! grep -q 'smoothJoint(raw.leftElbow' \
  app/src/main/java/com/felipe/elftemplate/tracking/KinectTrackingSmoother.java; then
  echo "KinectTrackingSmoother deve suavizar leftElbow"
  exit 1
fi

echo "Regressao: espelho duplo MoveNet..."
if grep -E '1\.0f\s*-\s*outputArray' \
  app/src/main/java/com/felipe/elftemplate/tracking/TFLitePoseProcessor.java; then
  echo "PROIBIDO 1.0f - x no decoder MoveNet (ver COORDINATE_FRAMES.md)"
  exit 1
fi

echo "Regressao: aux depth-first (corpo local)..."
if ! grep -q 'out.applyFrom(local)' \
  app/src/main/java/com/felipe/elftemplate/tracking/HybridRemoteArms.java; then
  echo "HybridRemoteArms deve copiar esqueleto local antes de mesclar braços"
  exit 1
fi

if ! grep -q 'POSE_EFFICIENT' \
  app/src/main/java/com/felipe/elftemplate/MirrorActivity.java; then
  echo "MirrorActivity deve usar SUB_STREAM 640x480 (POSE_EFFICIENT)"
  exit 1
fi

if ! grep -q 'runOnUiThread' \
  app/src/main/java/com/felipe/elftemplate/HandCursorActivity.java; then
  echo "HandCursorActivity deve postar UI na main thread"
  exit 1
fi

if ! grep -q 'LowLatencyHandListener' \
  app/src/main/java/com/felipe/elftemplate/tracking/KinectTrackingEngine.java; then
  echo "KinectTrackingEngine deve expor LowLatencyHandListener para cursor"
  exit 1
fi

echo "Limpando daemons Gradle..."
./gradlew --stop

echo "========================================================"
echo "OK: alloc, memoria, testes e contratos de visao passaram."
echo "========================================================"
