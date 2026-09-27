# Movement Package
Responsável pela lógica de locomoção, fusão de sensores e navegação autônoma.

- **SensorFusionEngine.java**: Realiza SLAM visual e odometria. Implementa **Motion Confidence 2.0** e um **Filtro de Mediana (Robust KLT)** que ignora ruídos visuais (reflexos e pessoas passando). Possui **Auto-Calibração Dinâmica**, utilizando o giroscópio para ajustar os ganhos de escala visual em tempo real. O sistema detecta bloqueios físicos (stalled) comparando comandos de roda vs movimento visual real.
- **NavigationController.java**: Piloto automático assíncrono (Executa em Thread separada a 10Hz). Implementa **Controle Proporcional de Rumo**, permitindo ajustes finos durante a navegação sem paradas bruscas.
- **MapView.java**: Componente UI customizado para renderizar o mapa. Sincronizado para evitar `ConcurrentModificationException` durante o ciclo de desenho.
- **Waypoint.java**: Modelo de dados para pontos de destino nomeados.
- **FollowMovementController.java**: Orquestra o movimento de seguimento suave.
- **MovementSafetyGuard.java**: Proteção contra colisões e limites físicos.
- **RobotGameMovementHandler.java**: Lógica de movimentação específica para o jogo de tênis.

### Instruções para novos módulos
Para criar novos tipos de movimento:
1. Use a interface `RobotMovement` para enviar comandos ao chassi.
2. Utilize o `SensorFusionEngine` para obter a localização atual (`getX()`, `getY()`, `getYawRad()`).
3. Injete o `Logger` para garantir testabilidade.
4. Sempre escreva testes unitários em `src/test` usando fakes para as interfaces acima.
