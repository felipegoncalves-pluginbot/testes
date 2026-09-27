# GUIA DE PROMPTS PARA VIBECODERS (COMO OBTER CÓDIGO PERFEITO DA IA)

Use estes templates de prompt para interagir com assistentes de IA (Antigravity, Cursor, Claude, ChatGPT) neste projeto. Eles impedem que a IA crie código quebrado ou sem testes.

---

### Template 1: Solicitar um Novo Gesto ou Ajuste no Tracking 3D
```text
Contexto: Projeto Sanbot Elf com Câmera Orbbec Astra (Android 6, Java 8).
Objetivo: Implementar o gesto [NOME_DO_GESTO] no KinectTrackingEngine.
Requisitos Obrigatórios:
1. Crie primeiro o método sintético no SyntheticDepthFixtures.java gerando o quadro de teste correspondente.
2. Adicione o caso de teste no GoldenDatasetTrackingTest.java validando a detecção e a histerese Schmitt-Trigger.
3. Não aloque nenhum objeto no loop processDepthFrame (Zero-Allocation policy).
4. Rode `./gradlew testDebugUnitTest` e garanta 100% de aprovação antes de finalizar.
```

---

### Template 2: Criar um Novo Mini-Jogo Arcade
```text
Contexto: Mini-jogos Kinect Arcade para Sanbot Elf.
Objetivo: Criar o jogo [NOME_DO_JOGO] (ex: Esquiva de Obstáculos / Dança).
Regras de Arquitetura:
1. Isole a física e pontuação em uma classe pura Java (ex: [Nome]GameEngine.java) sem dependências Android/Context.
2. Crie testes unitários abrangentes cobrindo pontuação, colisão e resets de estado.
3. Conecte o loop de renderização na View SurfaceView com zero ANR e 60 FPS estáveis.
4. Conecte os feedbacks físicos do robô via RobotGameFeedback (cabeça, asas, LEDs e TTS).
```

---

### Template 3: Investigação de Queda de Performance / Bug no Robô
```text
NÃO implemente no primeiro turno. Hipótese única: [ex.: X RGB espelhado vs depth].
Evidência anexa: [20 linhas NDJSON ou saída de tools/logcat_cv_profiler.py] + [PNG overlay se houver].
Contrato: docs/specs/COORDINATE_FRAMES.md
Objetivo:
1. Mostre o caminho de dados (MoveNet x → PoseDepthFusion → MirrorGameEngine yaw → hardwareYaw).
2. Proponha UM teste que falha hoje e passaria se a hipótese for verdadeira.
3. Proibido: tunar Holt/EMA/Schmitt, 1.0f - x, new Joint no loop, segundo detector.
4. Se for patch, rode ./tools/verify_all.sh (checkstyleAlloc + testes).
```

### Template 4: Recusar loop de correção
```text
Pare. Não empilhe filtro. O teste unitário verde com robô errado significa golden sintético insuficiente.
Grave 2s de depth+joints no device e adicione golden em src/test/resources antes de qualquer constante.
```
