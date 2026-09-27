# Tracking Package
Responsável pela percepção do jogador e processamento de sensores.

- **PlayerTracker.java**: Filtra dados de posição (EMA) e gerencia presença do jogador.
- **BodyPoseTracker.java**: Wrapper para ML Kit Pose Detection; processa frames YUV.
- **VisionMediaDecoder.java**: Decodificador H.264 para converter stream do Sanbot em YUV.
- **PoseOverlayView.java**: View customizada para desenhar o esqueleto sobre a preview da câmera.
