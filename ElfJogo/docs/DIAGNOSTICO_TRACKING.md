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

## A cabeça move a câmera (Astra na cabeça, confirmado no robô)

`COORDINATE_FRAMES.md` dizia "Astra no peito". Estava errado: Astra e HD ficam na cabeça. Três
coisas dependiam disso:

| # | Problema | Evidência | Correção |
|---|----------|-----------|----------|
| 9 | Nos jogos, a cabeça fica indo e vindo o tempo todo | `trackTargetX` mapeava X em yaw **absoluto**, `(0,5 − x)·60`. Simulação com cabeça de 60–120°/s e visão atrasada 100–250 ms: 51 a 103 reversões de sentido em 17 s, oscilando de 2° a 10°, sem nunca parar | `HeadGazeServo`: zona central de ±0,12, passo de 0,7 × erro (até 20°) e 600 ms entre passos. Na mesma simulação: 0 reversões, jogador dentro da zona central |
| 10 | No espelho, o jogador fica fora do centro, e o esqueleto some quando a cabeça inclina | Absoluto + EMA não oscila, mas para em 8° de 20° (jogador a ~20% do centro). Inclinar a cabeça 3°/6°/9° para baixo tira o jogador por 2/4/5 reestimativas do plano do chão (~2–10 s) | Em pé: o mesmo servo. Sentado: continua imitando o giro da cabeça relativo ao tronco (estável). Pitch nunca é comandado pela visão |
| 11 | No espelho, esqueleto desalinhado da imagem da câmera | O overlay aplicava um pan de `yaw/60` supondo HD na cabeça e Astra fixo: a 8° de yaw, 13% da largura | Pan removido: as duas câmeras giram juntas |

Testes: `HeadGazeServoTest` (inclui malha fechada), `MirrorSessionControllerTest` (pitch sempre 0),
`MirrorDisplayGuardTest` (proíbe pan pelo yaw).

## O que conferir no robô

1. **Cabeça nos jogos:** com o jogador parado no centro, a cabeça não deve se mexer. Ao andar
   para o lado, ela deve girar em um ou dois passos e parar.
2. **Logcat `AstraDepth`:** a linha `[ASTRA-STREAM] Espelhado=... | FOV=...` mostra se o firmware
   entrega a imagem espelhada. O pipeline assume imagem **não** espelhada. Se vier `true`, esquerda
   e direita estão trocadas em todos os jogos.
3. **Logcat `Astra3D` / HUD:** altura e pitch medidos do sensor. Um pitch acima de ~15° para baixo
   significa que a cabeça de um adulto nunca aparece no quadro.
4. **Distância de jogo:** com o sensor abaixo de 0,9 m e sem inclinação, a cabeça de um adulto só
   entra no quadro a partir de ~2,3 m. O Kinect pedia de 1,8 a 2,4 m, com o sensor na altura dos
   olhos e motor de inclinação.
5. `app/release/release/app-release.apk` é de outro app (`com.felipe.flipelf`) e não contém os jogos.

## Pendente

- O classificador de partes (`bodypart_forest.bin`) foi treinado com o sensor sintético entre 0,92 e
  1,18 m (`RandomPoseSampler`), acima da cabeça do Elf. Vale retreinar com 0,65–0,90 m e pitch da
  cabeça real.

## Limitações da validação

Não há Android SDK neste ambiente, então o Gradle não roda. Compilei as fontes de produção alteradas
com `javac` contra o `android-all` (API 23), o SDK Sanbot, o `OpenNI.jar`, o TFLite e o NanoHTTPD.
Rodei 259 testes JUnit dos pacotes `tracking`, `tracking3d`, `logic`, `mirror`, `movement`,
`server` e `debug`, mais os guards de alocação, memória, contrato de eixos, cinemática, espelho e
qualidade de teste, e o Checkstyle com as configurações do projeto. As Activities não compilam fora
do Gradle (dependem do `R`), e as mudanças nelas são pequenas.
