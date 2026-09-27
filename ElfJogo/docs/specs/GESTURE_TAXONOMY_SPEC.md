# TAXONOMIA & CONTRATOS MATEMÁTICOS DE GESTOS KINECT ARCADE

Todas as classificações de gestos devem utilizar **Máquinas de Estado Finitas (FSM)** e filtros com histerese **Schmitt-Trigger**. É proibido utilizar comparações ingênuas sem memória temporal (`Prev`).

---

## 1. Gestos Estáticos / Posturas

| Gesto | Condição de Entrada (Trigger) | Condição de Saída (Release) | Descrição Anatômica |
| :--- | :--- | :--- | :--- |
| **`T_POSE`** | `isLeftHandRaised && isRightHandRaised` <br> `|leftHandY - shoulderY| < 0.12` <br> `|rightHandY - shoulderY| < 0.12` <br> `(rightHandX - leftHandX) > 0.45` | Braços fechados ou abaixados | Ambos os braços abertos na horizontal na altura do ombro. |
| **`HANDS_UP`** | `isLeftHandRaised && isRightHandRaised` <br> `leftHandY < shoulderY - 0.05` <br> `rightHandY < shoulderY - 0.05` | Qualquer mão descer abaixo do ombro | Ambas as mãos erguidas acima da cabeça. |
| **`LEFT_HAND_UP`** | `isLeftHandRaised && !isRightHandRaised` | Mão esquerda descer abaixo de `shoulderY + 0.10` | Apenas mão esquerda levantada. |
| **`RIGHT_HAND_UP`** | `isRightHandRaised && !isLeftHandRaised` | Mão direita descer abaixo de `shoulderY + 0.10` | Apenas mão direita levantada. |
| **`DUCK`** | $\Delta Y_{centroid} > +0.12$ (desceu $\ge 12\%$ da tela) | $\Delta Y_{centroid} < +0.08$ | Jogador agachado em relação à linha de base calibrada. |
| **`JUMP`** | $\Delta Y_{centroid} < -0.09$ (subiu $\ge 9\%$ da tela) | $\Delta Y_{centroid} > -0.05$ | Jogador pulando no ar. |

---

## 2. Gestos Dinâmicos / Trajetórias Temporais

| Gesto | Vetor de Velocidade | Janela de Tempo ($\Delta t$) | Cooldown | Uso no Jogo |
| :--- | :--- | :--- | :--- | :--- |
| **`SWING_UP`** | $V_y < -2.0 \text{ telas/s}$ e $\|V_x\| < 1.8$ | $15ms \le \Delta t \le 250ms$ | $250ms$ | Raquetada no Kinect Tennis. |
| **`SWIPE_LEFT`** | $V_x < -2.2 \text{ telas/s}$ e $\|V_y\| < 1.8$ | $15ms \le \Delta t \le 250ms$ | $250ms$ | Corte/Golpe para a esquerda. |
| **`SWIPE_RIGHT`**| $V_x > +2.2 \text{ telas/s}$ e $\|V_y\| < 1.8$ | $15ms \le \Delta t \le 250ms$ | $250ms$ | Corte/Golpe para a direita. |
