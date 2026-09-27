# Guia de Localização Absoluta com AprilTags

## 1. Motivação
A triangulação WiFi por RSSI e a Odometria de Rodas foram oficialmente **removidas** do Sanbot Elf devido à extrema instabilidade (saltos superiores a 9 metros no EKF). 
Para corrigir a degradação acumulada da odometria visual (drift), a solução de Localização Absoluta deve ser migrada para **Marcadores Fiduciários (AprilTags)** utilizando a câmera Astra Orbbec.

## 2. Conceito (Arquitetura Proposta)
O robô Sanbot possui o SDK da Orbbec (Astra) embarcado. As AprilTags (códigos 2D de alto contraste desenhados especificamente para robótica) deverão ser espalhadas pelo ambiente de trabalho.
Quando o robô visualiza uma AprilTag:
1. O algoritmo extrai os 4 cantos do marcador e seu ID único na imagem (2D).
2. Conhecendo o tamanho físico real da AprilTag, a biblioteca resolve o problema de **Perspective-n-Point (PnP)**.
3. O PnP retorna a translação e rotação relativa da Câmera para a AprilTag (Pose 3D).
4. Como sabemos a posição global (X, Y, Yaw) de cada AprilTag ancorada na parede, podemos converter a pose relativa para atualizar a pose global do robô instantaneamente.

## 3. Implementação Recomendada

1. **Biblioteca JNI**: Devido às limitações de performance do Sanbot (Android 6.0, 32-bits), integre a biblioteca oficial em C++ da Universidade de Michigan (`apriltag`) via NDK/JNI, passando apenas os frames monocromáticos do Android para o código nativo.
2. **Atualização no EKF**: Ao obter uma pose absoluta via AprilTag, ela não deve substituir a posição `(x, y)` instantaneamente para evitar pulos indesejados. Ao invés disso, passe a medição para o `ExtendedKalmanFilter` com alto grau de confiança (baixo ruído de covariância).
   ```java
   // Exemplo: Se o PnP calculou que o robô está em (X=5.0, Y=3.0) com Yaw=0.5rad
   ekf.correctAprilTag(5.0, 3.0, 0.5, 0.95 /* confiança de 95% */);
   ```
3. **Redução de Custo de CPU**: O detector não deve rodar a cada frame (não precisamos processar a 30fps). Deve-se rodar o extrator a 2 ou 3 fps, aliviando a CPU principal e prevenindo *Thermal Throttling*.
4. **Iluminação**: Utilize as tags da família `tag36h11`, que possuem a melhor taxa de correção de erros em ambientes de luminosidade variável.
