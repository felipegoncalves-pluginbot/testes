# ElfJogo - Hub de Navegação e Suíte de Jogos Kinect para Sanbot Elf

Aplicativo Android integrado ao robô **Sanbot Elf (S1-B2)** combinando:
1. **Suíte de Jogos Kinect Arcade 3D**: Jogos controlados por movimento corporal e gestual no estilo Xbox 360 Kinect via câmera 3D Orbbec Astra.
2. **Sistema de Feedback Físico**: Animação de asas (`WingMotionManager`), rastreamento com a cabeça (`HeadMotionManager`), luzes LED reativas (`HardWareManager`) e narração de voz TTS em português (`SpeechManager`).
3. **Mapeamento e Navegação 2D/3D**: SLAM com RTAB-Map, odometria híbrida e servidor web embarcado para controle WASD.

---

## 🎮 Suíte de Jogos Kinect Arcade

| Jogo | Descrição | Controle por Gestos / Corpo | Reação do Robô |
| :--- | :--- | :--- | :--- |
| 🎾 **Kinect Tennis** | Jogo de tênis/ping-pong interativo | Raquetadas com a mão (`SWING_UP`) e posição horizontal do corpo | Move a cabeça seguindo a bola, bate asas e narra o placar |
| 🍉 **Fruit Slicer** | Ninja das Frutas estilo Kinect | Rastro de corte com as mãos no ar (`processSlice`) | Comemora combos triplos e alerta sobre bombas |
| 🕺 **Pose Match** | Desafio de poses estilo Just Dance | Espelhamento de poses (`T_POSE`, `HANDS_UP`, `JUMP`, `DUCK`, `SWIPE`) | Robô imita as poses com as asas e pontua |
| 🏃 **Dodger Runner** | Corrida com esquiva estilo Kinect Adventures | Inclinar corpo (X), pular barreiras baixas ou agachar sob lasers | Alerta sobre colisões e celebra recordes |

---

## 📷 Câmera 3D Orbbec Astra & Arquitetura de Rastreamento

### Hardware Identificado
* **Modelo:** Orbbec Astra Mini / Astra Embedded S.
* **Barramento:** Conexão via **USB Host interno** da placa-mãe Android (protocolo OpenNI2).
* **Identificadores USB:**
  * **VID:** `0x2bc5` (Decimal `11205`)
  * **PID:** `0x0401` (Astra/Mini) / `0x0501` (Astra Pro).
* **Stream de Profundidade:** Matriz 16-bit com distância em milímetros (Z) a 30 FPS na faixa útil de `0.6m a 3.0m`.

### Motor de Rastreamento (`KinectTrackingEngine`)
* **Fatiamento Espacial (Spatial Depth Slicing):** Segmenta o corpo do jogador sem o overhead de redes neurais pesadas, garantindo 30-60 FPS fluidos no Android 6 com < 5% de uso de CPU.
* **Gestos Suportados:**
  * `T_POSE`: Braços abertos lateralmente.
  * `HANDS_UP`: Braços elevados acima da cabeça.
  * `SWING_UP`: Movimento vertical rápido de raquetada.
  * `SWIPE_LEFT` / `SWIPE_RIGHT`: Aceno/varredura horizontal rápida.
  * `JUMP`: Salto detectado por deslocamento vertical do centróide.
  * `DUCK`: Agachamento corporal.

---

## 📁 Estrutura de Arquivos

```text
app/src/main/java/com/felipe/elftemplate/
├── MainActivity.java                 # Menu principal com cards de jogos e utilitários
├── TennisActivity.java               # Tela do jogo Kinect Tennis
├── FruitSlicerActivity.java          # Tela do jogo Ninja das Frutas
├── PoseMatchActivity.java            # Tela do jogo Just Dance / Pose Match
├── DodgerActivity.java               # Tela do jogo Dodger Runner
├── FollowActivity.java               # Modo Siga-me
├── MapActivity.java                  # Mapeamento 2D / Telemetria
├── logic/
│   ├── TennisGameEngine.java         # Física e regras do tênis
│   ├── FruitSlicerGameEngine.java    # Física, colisões e combos do Fruit Slicer
│   ├── FruitSlicerView.java          # Renderização Canvas 2D e rastro de lâmina neon
│   ├── PoseMatchGameEngine.java      # Sequenciador de poses e pontuação
│   ├── PoseMatchView.java            # Renderização de silhuetas e temporizador
│   ├── DodgerGameEngine.java         # Rolagem de obstáculos e detecção de esquiva
│   └── DodgerView.java               # Renderização da pista e avatar
├── movement/
│   └── RobotGameFeedback.java        # Controlador de asas, cabeça, LEDs e voz TTS
└── tracking/
    ├── AstraDepthController.java     # Driver USB OpenNI2 para câmera Orbbec Astra
    └── KinectTrackingEngine.java     # Motor de fatiamento 3D e extração de gestos
```

---

## 🧪 Testes Automatizados

O projeto possui suíte de testes unitários automatizados cobrindo motores de física, rastreamento e colisões:

```bash
# Executar todos os testes unitários
./gradlew test

# Gerar APK de debug
./gradlew assembleDebug

# Encerrar daemons do Gradle (Boas práticas de memória)
./gradlew --stop
```
