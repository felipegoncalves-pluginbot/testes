# Orçamento de memória — Sanbot Elf (Android 6, RAM ~2 GB, ~1 GB livre)

Medido no Elf (`octopus_qh106`, Android 6.0.1, `armeabi-v7a`):

| | Valor | Fonte |
|-|-------|-------|
| RAM física | ~2.0 GB (`MemTotal` 2066924 kB) | `/proc/meminfo` |
| Livre + cache | ~1.3–1.4 GB | `MemFree` + `Cached` (kernel sem `MemAvailable`) |
| Heap Java default | 192 MB | `dalvik.vm.heapgrowthlimit` (apps **sem** `largeHeap`) |
| Heap Java deste app | **512 MB** | `dalvik.vm.heapsize` (`android:largeHeap="true"` no manifest) |

192 MB **não** é a RAM do robô nem o teto deste APK. Native (OpenNI, TFLite, Bitmap)
compete pela mesma RAM física; 512 MB de heap **não** autoriza `new` no hot path.

Buffers residentes do pipeline de visão. PR que somar **>100 KB** precisa de
profiler (`tools/logcat_cv_profiler.py`) no device.

| Recurso | Tamanho | Notas |
|---------|---------|-------|
| NV21 640×480 | ~460 KB | 1 buffer reutilizado (`rgbWorkBuffer`) |
| Depth 640×480 × 2 holders | ~1.2 MB | ping-pong, sem `clone()` |
| MoveNet INT32 192×192×3 | ~442 KB | 1 thread (`setNumThreads(1)`) |
| Workspace TFLite | vários MB | mmap do `.tflite` + interpreter |
| Overlay debug ARGB | extra | não clonar por frame |
| Debug hub 2× 160×120 ARGB + `int[19200]` | ~230 KB | Astra PNG + HD JPEG ≤2 Hz; sem cópia NV21 640×480 |
| Floresta de partes do corpo | ~386 KB | asset `bodypart_forest.bin`, 3 árvores / 7278 nós; folhas em byte |
| Rótulos por célula (`BodyPartLabeler`) | ~61 KB | `byte[]` + `float[]` em grade 128×96, realocados só se a grade mudar |
| OpenCV native | extra | só se o profiler mostrar Java > 25 ms/frame |

### Classificador de partes do corpo (`tracking3d`)

O modelo passa dos 100 KB, então registro o que foi e o que **não** foi medido.

Medido no host: 3 árvores, profundidade 13, 7278 nós, 7281 folhas, 386 KB em disco e em RAM
(arrays primitivos, sem objeto por nó). As folhas guardam probabilidade quantizada em 1 byte por
parte, o que já economiza 4× sobre `float`.

O retreino com o Astra na cabeça (0,62–0,92 m, pitch de −12° a 20°) usa 600 quadros em vez de 420
e somou 68 KB ao modelo anterior (317 KB, 5988 nós), abaixo do limite de 100 KB por PR. A
profundidade 14 ganhava 0,7 ponto de acerto por pixel e custaria mais 63 KB e um nível de leitura
por célula, então ficou 13. A conta de CPU abaixo não muda: mesmas 3 árvores e 13 níveis.

Custo de CPU por frame, por conta algorítmica: ~78 leituras de profundidade por célula (3 árvores ×
~13 níveis × 2 amostras) sobre as células do corpo segmentado, tipicamente 1.100 a 3.000. Dá 85k a
230k leituras por frame, contra os 307k pixels que o pipeline antigo já varria por outros motivos.

**Não medido no device.** Falta rodar `tools/logcat_cv_profiler.py` no Elf para confirmar latência e
FPS reais. Se o profiler mostrar folga apertada, a primeira alavanca é reduzir a profundidade das
árvores (corta leituras proporcionalmente) e a segunda é classificar uma célula a cada duas.

Para regenerar o modelo:

```bash
./gradlew :app:testDebugUnitTest --tests '*BodyPartForestGenerator*' -Pbodypart.train=true
```

Os padrões do gerador reproduzem o asset embarcado byte a byte. Semente, montagem do sensor e todos
os hiperparâmetros ficam registrados em `app/src/main/assets/bodypart_forest.md`.

## Regras

- Zero `new Joint` / `new PoseLandmarkData` / `new TrackingResult` no hot path.
- `TrackingResult` e 15 joints nascem uma vez (`DepthFrameProcessor`, `KinectTrackingSmoother`).
- Default jogável = depth-blob. MoveNet é caminho opcional, nunca 2 threads.
- Dual câmera: fechar HD streams antigos antes da Astra (`closeStream`).
