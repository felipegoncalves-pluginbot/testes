# Server Package
Responsável pela interface de comunicação externa e dashboard web.

- **RobotWebServer.java**: Servidor HTTP (NanoHTTPD) que serve o console de controle do robô e API de dados do mapa.
    - Endpoints:
        - `GET /`: Dashboard interativo (HTML/JS).
        - `GET /map_data`: Estado atual do robô (JSON).
        - `POST /move`: Comandos de movimentação manual.
        - `POST /action`: Ações genéricas (home, etc).
        - `POST /add_waypoint`: Salva a posição atual com um nome.
        - `POST /goto_waypoint`: Inicia navegação para um ponto salvo.
