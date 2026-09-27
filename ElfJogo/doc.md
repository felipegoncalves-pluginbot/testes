# Documentação de Arquitetura e Decisões (doc.md)

## 1. Suíte de Jogos Kinect e Rastreamento 3D

### Câmera 3D Orbbec Astra (OpenNI2)
- **Topologia de Barramento**: A câmera Orbbec Astra está conectada à placa-mãe Android via **USB Host interno** (`VID: 0x2bc5, PID: 0x0401/0x0501`), gerenciada nativamente pela biblioteca `libOpenNI2.so` e classes do `OpenNI.jar`.
- **Fatiamento Espacial (Spatial Depth Slicing)**: Em vez de carregar redes neurais pesadas de Deep Learning (como OpenPose ou MediaPipe pesado) que sobrecarregam o processador quad-core antigo do Sanbot (Android 6), o `KinectTrackingEngine` utiliza fatiamento direto da matriz de profundidade de 16 bits (distâncias métricas Z entre 600mm e 3000mm).
- **Extração de Gestos e Esqueleto**:
  - Centróide normalizado `(X, Y, Z)` para localização corporal e desvios de pista.
  - Extremidades superior-esquerda e superior-direita para localização das mãos.
  - Variação temporal do centróide vertical para detecção de **Pulo** (`JUMP`) e **Agachamento** (`DUCK`).
  - Vetor de deslocamento e velocidade rápida de mão para detecção de **Raquetadas** (`SWING_UP`) e **Varreduras** (`SWIPE_LEFT` / `SWIPE_RIGHT`).

### Feedback Físico e Multimodal (`RobotGameFeedback`)
- O robô Sanbot participa ativamente da gameplay:
  - **Asas (`WingMotionManager`)**: Movimentos sincronizados de comemoração de pontos, aviso de derrota e espelhamento das poses do jogador no Pose Match.
  - **Cabeça (`HeadMotionManager`)**: Rastreamento contínuo da bola ou do centróide do jogador no plano horizontal.
  - **Luzes LED (`HardWareManager`)**: Verde para ponto/acerto, vermelho para erro/bomba, aleatório/arco-íris para combos.
  - **Voz TTS (`SpeechManager`)**: Narração e comentários dinâmicos em português com controle de debounce temporal (evitando sobreposição de áudio).

---

## 2. Navegação e Controle Autônomo

O `NavigationController` implementa um piloto automático em malha fechada (Closed-loop PID) com base na odometria do `SensorFusionEngine`:
- **Suavização de Chegada (Dancinha do Alvo)**: A tolerância de alcance de Waypoints foi definida para `0.15m` a fim de evitar piruetas ao tentar atingir um ponto perfeitamente.
- **Costmap Local por Infravermelho**: Obstáculos móveis e estáticos próximos (<1.0m) são detectados pelos sensores infravermelhos (IDs 3 a 10) forçando "Stops" de segurança via função `isPathBlocked`.

---

## 3. Navegação e Odometria (SensorFusionEngine & RobotEKF)

A arquitetura de navegação do Sanbot Elf unifica o **RTAB-Map** e um Filtro de Kalman Customizado:
1. **Odometria Visual RGB-D (RTAB-Map via JNI)**: Motor C++ que gera pose de translação (VO) e resolve o problema de longo termo (Loop Closure). O processamento é delegado ao `RtabmapWrapper` para prevenir lentidões de Garbage Collection no Android.
2. **Giroscópio / Bússola via RobotEKF**: Fonte Primária de Rotação. A rotação (Yaw) fornecida pelo magnetômetro / giroscópio do Sanbot é injetada no filtro (RobotEKF) com variâncias adaptáveis.
   - *Decisão Crítica:* Quando os motores do robô geram alta distorção magnética, a leitura da bússola passa a ter *alta covariância*, e o EKF passa a ignorá-la temporariamente em favor da VO.
3. **Odometria de Rodas e Triangulação WiFi**: *Removidas*. Como o Sanbot Elf não expõe Encoders de roda precisos e o WiFi possui alta taxa de variação (>9m de erro), o SLAM depende do pipeline `Visão (RTAB-Map) + Inércia (Bússola)`.

---

## 4. Telemetria e Dashboard (RobotWebServer)

O hardware do robô (Sanbot Elf) não exporta um sensor Acelerômetro (`Sensor.TYPE_ACCELEROMETER`) válido da mesma forma que celulares convencionais.
- *Decisão Crítica:* A UI (`dashboard.js`) utiliza exclusivamente o `yaw` fundido pelo `RobotEKF` para girar o ícone da bússola.

---

## 5. Otimizações de Telemetria e Rede (NanoHTTPD)

- **Desativação de GZIP**: Sobrescrevemos o método `useGzipWhenAccepted` para retornar `false`, reduzindo o Garbage Collection repetitivo (20Hz) e prevenindo o erro `EPIPE` no NanoHTTPD.
- **Polling Serializado**: No frontend (`dashboard.js`), loop recursivo com `setTimeout` acoplado ao término previne crash de Sockets por requisições concorrentes pendentes.

---

## 6. Controle Manual (WASD e Joystick)

- **Filtragem de Transição de Estado**: Os comandos de movimentação manuais são transmitidos no frontend apenas quando ocorre uma real mudança de estado das teclas WASD. No backend, a classe `JoystickMovementController` filtra redundâncias.
- **Movimento Contínuo (`duration = 0`)**: Comandos de direção são disparados com duração zero, permitindo tração contínua e máxima velocidade física.
- **Watchdog de Segurança (Heartbeat)**: Temporizador de 500ms (Watchdog) para parada automática do robô se o sinal de controle for interrompido.
