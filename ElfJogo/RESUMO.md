# RESUMO DO PROJETO - HANDOVER

## 📌 Contexto Atualizado
O projeto integra o processamento de visão espacial 3D, controle de navegação e uma suíte completa de **Jogos Kinect Arcade** para o robô Sanbot Elf S1-B2.

### Tecnologias em Uso:
- **Android 6.0** (Marshmallow - API 23).
- **Sanbot OpenSDK v2.0.1.10** (Integração de motores, LEDs, cabeça, asas e TTS).
- **Câmera 3D Orbbec Astra Mini / Embedded S**: Conectada via USB Host interno (`VID: 0x2bc5, PID: 0x0401/0x0501`) via OpenNI2.
- **Kinect Tracking Engine**: Motor de fatiamento espacial de profundidade 16-bit (`Spatial Depth Slicing`) com baixíssimo consumo de CPU (< 5%).
- **Suíte de 4 Mini-Jogos Kinect Arcade**:
  1. *Kinect Tennis*: Raquetadas no ar e controle de cabeça/asas.
  2. *Fruit Slicer*: Corte de frutas no ar com efeito de rastro neon.
  3. *Pose Match / Just Dance*: Espelhamento de poses e imitação com asas.
  4. *Dodger Runner*: Esquiva com inclinação do corpo, pulo e agachamento.
- **vSLAM / RTAB-Map & EKF**: Mapeamento 2D/3D e fusão inercial.

---

## 🚀 O que foi Implementado:
1. **Ativação e Estabilização da Câmera Astra**:
   - Correção dos recursos corrompidos no AAPT2.
   - Driver [`AstraDepthController`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/tracking/AstraDepthController.java) gerenciando permissões USB via `OpenNIHelper`.
2. **Motor de Rastreamento de Gestos Kinect**:
   - Extração de centróide, mãos levantadas, pulo, agachamento, raquetada (`SWING_UP`), acenos (`SWIPE_LEFT`/`SWIPE_RIGHT`) e `T_POSE` em [`KinectTrackingEngine`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/tracking/KinectTrackingEngine.java).
3. **Suíte de Jogos & Telas**:
   - [`FruitSlicerActivity`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/FruitSlicerActivity.java) + [`FruitSlicerView`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/FruitSlicerView.java) + [`FruitSlicerGameEngine`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/FruitSlicerGameEngine.java).
   - [`PoseMatchActivity`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/PoseMatchActivity.java) + [`PoseMatchView`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/PoseMatchView.java) + [`PoseMatchGameEngine`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/PoseMatchGameEngine.java).
   - [`DodgerActivity`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/DodgerActivity.java) + [`DodgerView`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/DodgerView.java) + [`DodgerGameEngine`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/logic/DodgerGameEngine.java).
   - [`TennisActivity`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/TennisActivity.java) e [`MainActivity`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/MainActivity.java) integradas.
4. **Feedback Físico do Robô**:
   - [`RobotGameFeedback`](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/movement/RobotGameFeedback.java) unificando asas, cabeça, LEDs e falas TTS.
5. **Cobertura de Testes Automatizados**:
   - Testes unitários para todos os motores de física e rastreamento executando com 100% de sucesso via `./gradlew test`.

---

## 📂 Arquivos Chave:
- [KinectTrackingEngine.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/tracking/KinectTrackingEngine.java): Motor de rastreamento 3D espacial.
- [RobotGameFeedback.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/movement/RobotGameFeedback.java): Integração física com o robô.
- [MainActivity.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/MainActivity.java): Menu principal dos jogos e funções.
- [README.md](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/README.md): Guia completo do projeto.
