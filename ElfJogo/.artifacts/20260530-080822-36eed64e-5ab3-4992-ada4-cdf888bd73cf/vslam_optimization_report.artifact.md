# Relatório Técnico: Otimização 100% do Sistema de Navegação e Localização (v24)

O sistema atual apresenta instabilidade na volta para a base (loop de giro infinito) e latência visual. Este relatório detalha as causas e propõe a migração para uma arquitetura robusta baseada em princípios probabilísticos modernos.

## 1. Diagnóstico do "Loop de Giro Infinito"
Os logs indicam que o robô entra em oscilação quando o erro angular está próximo do limite de decisão.

- **Causa Raiz:** O `NavigationController` usa uma lógica de controle bang-bang/proporcional simples com uma "deadband" (zona morta) de 12 graus. Se a odometria visual ou o giroscópio oscilarem ligeiramente na borda dessa zona enquanto o robô tenta alinhar, ele alterna comandos `left` e `right` ou `left` e `forward` rapidamente.
- **Efeito WiFi:** A triangulação WiFi induz saltos de pose de ~1.2m (Residual), o que altera instantaneamente o `targetYaw`, forçando o robô a girar para compensar um "salto" inexistente na realidade física.

## 2. Pesquisa de Bibliotecas (Android 6.0 / API 23)

### ARCore (Google)
- **Status:** **Incompatível.** O ARCore requer Android 7.0 (API 24) ou superior e certificação de hardware específica (sensores calibrados de fábrica e suporte a NDK avançado). O Sanbot Elf (Android 6.0) não possui o Google Play Services for AR.

### Mediapipe (Google)
- **Status:** **Parcialmente Compatível.** Ótimo para detecção de pose (como já usamos no `FollowActivity`), mas não possui um motor de SLAM visual pronto para odometria de robô.

### OpenCV (Nativo) - Solução Atual
- **Status:** **Melhor Opção.** Continuaremos usando OpenCV via NDK, mas migraremos do LK Optical Flow simples para um **Filter-based Monocular SLAM** ou **ORB-SLAM Lite** (versão simplificada).

## 3. Plano de Melhoria 100% Robustez

### Fase A: Navegação Estável (Fix do Giro)
1.  **Histerese Angular:** Implementar uma margem de segurança maior para sair do estado de giro. Se o robô começar a girar, ele só para quando o erro for < 5 graus (atualmente para com 12).
2.  **PID de Direção:** Substituir a lógica de `if/else` por um controlador PID contínuo que suaviza a velocidade angular conforme se aproxima do alvo.

### Fase B: Visão 3D e SLAM (Astra Camera)
1.  **Integração de Profundidade:** A câmera Orbbec Astra permite medir distância real. Usaremos o buffer de profundidade para:
    *   **Obstacle Avoidance:** Ignorar "movimento visual" que ocorre em objetos muito próximos (ruído).
    *   **Scale Recovery:** O SLAM monocular atual tem erro de escala. Com a profundidade, o `visualScaleFactor` será dinâmico e exato.
2.  **Redução de Delay:**
    *   **Task Decoupling:** Mover o SLAM para uma thread com prioridade `THREAD_PRIORITY_URGENT_DISPLAY`.
    *   **Subsampling Inteligente:** Processar apenas a região central do frame para odometria (onde o movimento é mais linear).

### Fase C: EKF Probabilístico Avançado
1.  **WiFi as Global Anchor:** O WiFi será usado apenas para "teletransportar" o robô se ele estiver completamente perdido (Confiança < 0.2). Enquanto estiver navegando, o WiFi apenas limitará o crescimento da covariância lateral, sem afetar o heading.
2.  **Dynamic Process Noise:** Se o robô está girando rápido, o erro do giroscópio aumenta. Ajustaremos o $Q$ em tempo real.

## 4. Próximos Passos Imediatos
- [ ] Aplicar Histerese e PID no `NavigationController`.
- [ ] Implementar decodificação do stream de profundidade (Depth) no `CameraController`.
- [ ] Otimizar o JNI para processar frames em < 30ms.
