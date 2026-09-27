import http.server
import socketserver
import json
import time
import math
import os
import threading

# Estado do robô fantasma
state = {
    "x": 0.0,
    "y": 0.0,
    "yaw": 0.0,
    "active_action": "stop",
    "waypoints": [{"name": "Base", "x": 0.0, "y": 0.0}],
    "target_x": 0.0,
    "target_y": 0.0
}

# Mapa Fixo Simulado (Paredes e Objetos)
mock_walls = [
    [2.0, -2.0], [2.0, -1.8], [2.0, -1.6], [2.0, -1.4], [2.0, -1.2],
    [2.0, 1.2], [2.0, 1.4], [2.0, 1.6], [2.0, 1.8], [2.0, 2.0],
    [-2.0, -2.0], [-2.0, -1.8], [-2.0, -1.6], [-2.0, -1.4], [-2.0, -1.2],
]

mock_radar = [
    {"type": 1, "x": 1.5, "y": 0.0, "label": "Pessoa 1"}, # Pessoa à frente
    {"type": 2, "x": -1.0, "y": 1.5, "label": "Cachorro"}, # Cachorro atrás/esquerda
    {"type": 3, "x": 0.0, "y": -1.8, "label": "Mesa/Cadeira"} # Cadeira à direita
]

class MockRobotHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Silenciar logs no console

    def do_GET(self):
        if self.path == '/':
            # Serve o HTML nativo do projeto Android
            html_path = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/dashboard.html')
            try:
                with open(html_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                self.send_response(200)
                self.send_header('Content-type', 'text/html; charset=utf-8')
                self.end_headers()
                self.wfile.write(content.encode('utf-8'))
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(str(e).encode('utf-8'))
        
        elif self.path == '/map_data':
            # Serve o JSON do SLAM
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            if state["active_action"] == "goto":
                path_points = [
                    [state["x"], state["y"]],
                    [state.get("target_x", state["x"]), state.get("target_y", state["y"])]
                ]
            else:
                path_points = [[state["x"], state["y"]]]
            
            data = {
                "x": state["x"],
                "y": state["y"],
                "yaw": state["yaw"],
                "walls": mock_walls,
                "radarObjects": mock_radar,
                "path": path_points,
                "waypoints": state["waypoints"],
                "target_x": state.get("target_x", 0.0),
                "target_y": state.get("target_y", 0.0),
                "is_navigating": state["active_action"] == "goto"
            }
            self.wfile.write(json.dumps(data).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == '/move':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length).decode('utf-8')
            state["active_action"] = post_data.strip()
            self.send_response(200)
            self.end_headers()
        elif self.path == '/action':
            content_length = int(self.headers['Content-Length'])
            act = self.rfile.read(content_length).decode('utf-8')
            if act == 'home':
                state["x"] = 0.0
                state["y"] = 0.0
                state["yaw"] = 0.0
                state["active_action"] = "stop"
            self.send_response(200)
            self.end_headers()
        elif self.path == '/add_waypoint':
            content_length = int(self.headers['Content-Length'])
            wp_name = self.rfile.read(content_length).decode('utf-8').strip()
            state["waypoints"].append({
                "name": wp_name,
                "x": state["x"],
                "y": state["y"]
            })
            self.send_response(200)
            self.end_headers()
        elif self.path == '/goto_waypoint':
            content_length = int(self.headers['Content-Length'])
            wp_name = self.rfile.read(content_length).decode('utf-8').strip()
            for wp in state["waypoints"]:
                if wp["name"] == wp_name:
                    state["target_x"] = wp["x"]
                    state["target_y"] = wp["y"]
                    state["active_action"] = "goto"
                    break
            self.send_response(200)
            self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

def physics_loop():
    while True:
        action = state["active_action"]
        speed = 0.5 # metros por segundo realístico
        rot_speed = 1.0 # rad/sec
        
        if action == 'forward':
            state["x"] += math.cos(state["yaw"]) * speed * 0.1
            state["y"] += math.sin(state["yaw"]) * speed * 0.1
        elif action == 'backward':
            state["x"] -= math.cos(state["yaw"]) * speed * 0.1
            state["y"] -= math.sin(state["yaw"]) * speed * 0.1
        elif action == 'left':
            state["yaw"] += rot_speed * 0.1
        elif action == 'right':
            state["yaw"] -= rot_speed * 0.1
        elif action == 'goto':
            dx = state.get("target_x", state["x"]) - state["x"]
            dy = state.get("target_y", state["y"]) - state["y"]
            dist = math.hypot(dx, dy)
            if dist > 0.1:
                target_yaw = math.atan2(dy, dx)
                diff_yaw = target_yaw - state["yaw"]
                while diff_yaw > math.pi: diff_yaw -= 2 * math.pi
                while diff_yaw < -math.pi: diff_yaw += 2 * math.pi
                if abs(diff_yaw) > 0.1:
                    state["yaw"] += math.copysign(rot_speed * 0.1, diff_yaw)
                else:
                    state["x"] += math.cos(state["yaw"]) * speed * 0.1
                    state["y"] += math.sin(state["yaw"]) * speed * 0.1
            else:
                state["active_action"] = "stop"
            
        # Normaliza Yaw
        while state["yaw"] > math.pi: state["yaw"] -= 2 * math.pi
        while state["yaw"] < -math.pi: state["yaw"] += 2 * math.pi
        
        time.sleep(0.1)

def run_server(port=8080):
    handler = MockRobotHandler
    with socketserver.TCPServer(("", port), handler) as httpd:
        print(f"Mock Server rodando em http://localhost:{port}")
        threading.Thread(target=physics_loop, daemon=True).start()
        httpd.serve_forever()

if __name__ == "__main__":
    run_server(8080)
