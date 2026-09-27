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
| Floresta de partes do corpo | ~332 KB | asset `bodypart_forest.bin`, 3 árvores / 6273 nós; folhas em byte |
| Rótulos por célula (`BodyPartLabeler`) | ~61 KB | `byte[]` + `float[]` em grade 128×96, realocados só se a grade mudar |
| OpenCV native | extra | só se o profiler mostrar Java > 25 ms/frame |

### Classificador de partes do corpo (`tracking3d`)

O modelo passa dos 100 KB, então registro o que foi e o que **não** foi medido.

Medido no host: 3 árvores, profundidade 13, 6273 nós, 6276 folhas, 332 KB em disco e em RAM
(arrays primitivos, sem objeto por nó). As folhas guardam probabilidade quantizada em 1 byte por
parte, o que já economiza 4× sobre `float`.

Custo de CPU por frame, por conta algorítmica: ~78 leituras de profundidade por célula (3 árvores ×
~13 níveis × 2 amostras) sobre as células do corpo segmentado, tipicamente 1.100 a 3.000. Dá 85k a
230k leituras por frame, contra os 307k pixels que o pipeline antigo já varria por outros motivos.

**Não medido no device.** Falta rodar `tools/logcat_cv_profiler.py` no Elf para confirmar latência e
FPS reais. Se o profiler mostrar folga apertada, a primeira alavanca é reduzir a profundidade das
árvores (corta leituras proporcionalmente) e a segunda é classificar uma célula a cada duas.

Para regenerar o modelo:

```bash
./gradlew :app:testDebugUnitTest --tests '*BodyPartForestGenerator*' \
    -Pbodypart.train=true -Pbodypart.frames=420
```

Semente e hiperparâmetros ficam registrados em `app/src/main/assets/bodypart_forest.md`.

## Regras

- Zero `new Joint` / `new PoseLandmarkData` / `new TrackingResult` no hot path.
- `TrackingResult` e 15 joints nascem uma vez (`DepthFrameProcessor`, `KinectTrackingSmoother`).
- Default jogável = depth-blob. MoveNet é caminho opcional, nunca 2 threads.
- Dual câmera: fechar HD streams antigos antes da Astra (`closeStream`).
