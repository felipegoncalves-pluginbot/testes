# Diagnóstico: rastreamento falhando e jogos travando

Investigação feita sem acesso ao robô: leitura do código, bytecode do `OpenNI.jar` e do SDK Sanbot,
ficha técnica pública do Sanbot Elf e do Orbbec Astra, e experimentos com o renderizador sintético
do próprio projeto (`SyntheticDepthRenderer`). Nada aqui foi medido no Elf. A seção final lista o que
conferir no robô.

## Causas encontradas e corrigidas

| # | Sintoma | Causa | Evidência | Correção |
|---|---------|-------|-----------|----------|
| 1 | Jogador a 1,2–2 m não é rastreado | `PersonGate` exige "pescoço" de até 0,45 m. Com o sensor baixo, a cabeça sai do topo do quadro e a faixa mais estreita visível é o peito (0,48–0,55 m) | Sintético: pessoa a 1,5 m reprovada com "sem pescoco" | Com a cabeça fora do quadro o limite passa a 0,70 m (`MAX_TORSO_BAND_HEAD_CUT_M`) |
| 2 | Esqueleto "gruda" na parede ou em móveis | O corpo principal era o slot 0, ou seja, o primeiro rótulo na varredura de cima para baixo. Recortes da parede do fundo começam mais alto no quadro | Sintético: 8 de 56 cenas com o esqueleto a 3,8–4,2 m, na parede. Depois da correção: 0 | Slot 0 = corpo mais próximo (regra "Closest1Player" do Kinect) |
| 3 | Chão grudado nos pés, corpo reprovado como "largo demais" | O plano do chão só era aceito com o sensor entre 0,75 e 1,45 m, e o valor padrão era 1,05 m. O Sanbot Elf inteiro mede 0,90 m. A medição real de 0,68 m foi descartada como "mesa" | Ficha técnica: 902 mm de altura, sensor 3D na cabeça. Sintético com o sensor a 0,70 m: piso não medido | Faixa 0,45–1,10 m, pitch de −20° a 35°, padrão 0,80 m |
| 4 | Fruit Slicer fecha ao abrir | `ArrayList.removeIf` no `onDraw`. A API só existe a partir da 24, o Elf roda a API 23 e não há core library desugaring | `build.gradle` sem `coreLibraryDesugaringEnabled`; o lint está com `abortOnError false` | Laço com `Iterator` |
| 5 | Jogo fica cada vez mais lento e atrasado | Cada frame fazia `runOnUiThread` com o **mesmo** `TrackingResult` do worker. A UI lia o esqueleto no meio da escrita (pisca para a pose padrão) e, quando atrasava, a fila de runnables crescia | Código de `TennisActivity`, `FruitSlicerActivity` e das outras telas | `BodyTrackingSession.startOnMainThread`: uma entrega pendente no máximo, com cópia própria (`MainThreadFrameRelay`) |
| 6 | Esqueleto dá saltos | Buffer duplo em rodízio: quando o processamento passava de 33 ms, o produtor escrevia no array que o worker ainda lia | Código de `Astra3dTrackingEngine` | Buffer triplo com troca atômica (`DepthFrameExchange`) |
| 7 | Memória cresce a cada partida | O `OpenNI.jar` da Orbbec guarda os listeners de frame num mapa **estático**, e o `stop()` nunca os removia. Cada tela de jogo ficava presa lá. `OpenNI.initialize()` também nunca era pareado com `shutdown()` | Bytecode de `org.openni.VideoStream` (`static ConcurrentHashMap mFrameListeners`) | `removeNewFrameListener` e `OpenNI.shutdown()` no `stop()`, com a ordem de fechamento correta |
| 8 | Ao voltar para a tela, o jogo fica congelado | O SDK Sanbot desconecta no `onStop` e reconecta no `onResume`, mas as flags `stopped`/`isDestroyed` nunca eram zeradas | Bytecode de `BindBaseActivity` (`connService` no `onResume`) | As flags são zeradas no `onMainServiceConnected` |

Regressões novas: `RobotMountingTrackingTest`, `DepthFrameExchangeTest` e `MainThreadFrameRelayTest`.
Os três testes de montagem falham no código antigo.

## Suspeita forte ainda não corrigida: a cabeça move a câmera

A ficha técnica do Sanbot Elf coloca o sensor 3D **na cabeça**. `COORDINATE_FRAMES.md` diz "Astra
no peito", e todos os testes sintéticos assumem um sensor fixo a 1,05 m.

Nos jogos, `RobotGameFeedback.trackTargetX` converte o X do jogador na imagem em um yaw **absoluto**
para a cabeça (`90 + (0,5 − x)·60`). Se o sensor estiver na cabeça, isso é uma malha fechada instável:
a cabeça gira, a pessoa se desloca na imagem, o comando volta, e a cabeça oscila. O esqueleto treme e
o chão muda de lugar a cada movimento. Não mudei isso porque a correção depende de onde o sensor está:

- **Sensor na cabeça:** desligar o `trackTargetX` nos jogos, ou trocá-lo por controle incremental com
  zona morta (`yaw += k·(0,5 − x)`).
- **Sensor no peito:** o controle atual serve, mas `DEFAULT_CAMERA_HEIGHT_M` deve virar a altura
  medida do peito.

## O que conferir no robô

1. **Onde está o sensor:** abra a tela de rastreamento 3D e gire a cabeça com a mão. Se a silhueta
   se mover, o sensor está na cabeça.
2. **Logcat `AstraDepth`:** a linha `[ASTRA-STREAM] Espelhado=... | FOV=...` mostra se o firmware
   entrega a imagem espelhada. O pipeline assume imagem **não** espelhada. Se vier `true`, esquerda
   e direita estão trocadas em todos os jogos.
3. **Logcat `Astra3D` / HUD:** altura e pitch medidos do sensor. Um pitch acima de ~15° para baixo
   significa que a cabeça de um adulto nunca aparece no quadro.
4. **Distância de jogo:** com o sensor abaixo de 0,9 m e sem inclinação, a cabeça de um adulto só
   entra no quadro a partir de ~2,3 m. O Kinect pedia de 1,8 a 2,4 m, com o sensor na altura dos
   olhos e motor de inclinação.
5. `app/release/release/app-release.apk` é de outro app (`com.felipe.flipelf`) e não contém os jogos.

## Limitações da validação

Não há Android SDK neste ambiente, então o Gradle não roda. Compilei as fontes de produção alteradas
com `javac` contra o `android-all` (API 23), o SDK Sanbot, o `OpenNI.jar` e o TFLite. Rodei 209
testes JUnit dos pacotes `tracking`, `tracking3d` e `logic`, mais os guards de alocação, memória,
contrato de eixos e qualidade de teste, e o Checkstyle com as configurações do projeto. As Activities
não compilam fora do Gradle (dependem do `R`), e as mudanças nelas são de uma linha cada.
