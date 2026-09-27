# Plano de Melhoria de Robustez: Navegação e Mapeamento

O objetivo deste plano é resolver a instabilidade do sistema de navegação e mapeamento (SLAM), que faz com que o robô se perca facilmente. Atualmente, o sistema é muito dependente de ganhos fixos e um fallback de odometria impreciso.

## Análise de Problemas Detectados

1. **Odometria Cega**: Quando a visão falha (blur, pouca luz), o robô usa uma velocidade constante (`0.4m/s`). Se o piso escorregar ou a bateria estiver baixa, a posição no mapa diverge rapidamente da realidade.
2. **Ruído Visual**: O rastreamento de pontos KLT usa uma média simples. Pontos em objetos em movimento (pessoas passando) ou reflexos "puxam" a posição do robô erroneamente.
3. **Controle de Navegação Rígido**: O robô para para girar e depois anda. Pequenos desvios durante a caminhada não são corrigidos de forma fluida, causando um efeito de "zigue-zague" que acumula erro.
4. **Falta de Validação Cruzada**: O giroscópio e a visão não se validam mutuamente para ajustar os ganhos de escala em tempo real.

## Proposta Técnica

### 1. SensorFusionEngine: Filtro de Rejeição de Outliers e Fusão Ponderada

- **Filtro de Mediana/RANSAC Lite**: Em vez de `dx_avg`, usar um filtro que descarta pontos cujo movimento destoa da maioria ou do comando enviado.
- **Fusão Dinâmica**: A confiança (`confidence`) deve cair mais rápido se houver divergência entre o Giroscópio e a Visão lateral (`dx`).
- **Auto-Calibração**: Usar o Giroscópio (que é absoluto a curto prazo) para ajustar o `VISUAL_ROTATION_GAIN` dinamicamente.

### 2. NavigationController: Controle Proporcional e Suavização

- **Correção de Rumo em Movimento**: Permitir pequenos ajustes de rota (`left`/`right`) sem parar completamente o movimento `forward`, se o erro de ângulo for pequeno (< 15 graus).
- **Aceleração Gradual**: Evitar trancos que causam "motion blur" na câmera e fazem o rastreador KLT perder os pontos.

### 3. Melhoria na Detecção de Stall

- **Comparação Visão vs Motor**: Se o comando é `forward`, as rodas dizem que estão movendo, mas a visão e o giroscópio dizem 0 movimento por > 500ms, disparar `STALL` imediatamente.

## Perguntas Técnicas (Ambiguidades)

1. **Iluminação do Ambiente**: O robô opera em locais com muitos reflexos (vidros, pisos brilhantes)? Isso afeta drasticamente o KLT.

Sim o robô opera em ambiente com muito reflexo e pouca iluminação.

2. **Velocidade de Resposta**: Podemos reduzir o loop de navegação de 200ms para 100ms para correções mais rápidas, ou o processador do Sanbot (Android 5.1 antigo) começa a travar?

Thread separada.

3. **Uso de Giroscópio**: O giroscópio do Sanbot costuma ter drift longo. Você prefere priorizar a Visão (mais lenta, mas sem drift cumulativo) ou o Giroscópio (rápido, mas "entorta" com o tempo) para a rotação?

Os dois.

## Plano de Testes (TDD)

1. **Teste de Rejeição de Outliers**: Simular pontos KLT movendo-se em direções opostas e garantir que a posição não seja afetada.
2. **Teste de Stall por Visão**: Simular comando `forward` com `dy = 0` e validar queda de confiança.
3. **Teste de Navegação com Erro de Ângulo**: Garantir que o robô aplique comandos de correção proporcionais à distância do alvo.

---

HALT: Aguardando aprovação para iniciar implementação e testes.
