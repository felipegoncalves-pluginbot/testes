# DIRECTIVES FOR AI CODING AGENTS (SANBOT ELF / KINECT VISION)

Este repositório contém código de robótica física e visão computacional embarcada (Sanbot Elf S1/B2, Android 6 API 23, Orbbec Astra 3D).

## REGRAS MANDATÓRIAS PARA QUALQUER AGENTE:
1. **Compatibilidade Rígida**:
   - Java puro (Java 8 compatível). Sem `var`, sem `Files.readString()`.
   - Gradle $\le 7.4.2$ e AppCompat 1.4.0.
2. **Zero-Allocation no Tracking**:
   - É terminantemente proibido usar `new` dentro de loops de imagem e frames.
3. **Validação Antes da Entrega**:
   - Sempre execute `./tools/verify_all.sh` ou `./gradlew testDebugUnitTest` antes de declarar tarefa concluída.
4. **Limpeza Obrigatória de Recursos**:
   - Ao terminar a validação, sempre execute `./gradlew --stop` para não sobrecarregar a memória do host Linux.
5. **Specs & Fixtures**:
   - Eixos e yaw: `docs/specs/COORDINATE_FRAMES.md` (fonte da verdade) + `CoordinateContractTest`.
   - Consulte também `docs/specs/CV_TRACKING_SPEC.md`, `docs/specs/MEMORY_BUDGET.md` e `docs/specs/GESTURE_TAXONOMY_SPEC.md`.
   - Testes de visão contra `SyntheticDepthFixtures.java` e `GoldenDatasetTrackingTest.java`.
6. **Vibe coding de tracking**:
   - Uma hipótese por turno. Sem tunar Holt/EMA, sem `1.0f - x`, sem segundo filtro.
   - Zero `new Joint`/`new PoseLandmarkData`/`new TrackingResult` no hot path (`Joint.set`).
   - Evidência: `tools/logcat_cv_profiler.py` ou dump NDJSON **antes** de mudar constante.
7. **`verify_all.sh` é o Accept**: inclui `checkstyleAlloc` + `checkstyleMemory` + testes. Não declare pronto só com unit test verde.
8. **Debug visual no robô (não peça foto)**: `bash tools/sanbot_debug/snapshot.sh` e leia `rgb.jpg` (HD) + `vision.png` (Astra) + `state.json` (`sensors`). Kit: `com.sanbot.debug` + skill `sanbot-debug`.
