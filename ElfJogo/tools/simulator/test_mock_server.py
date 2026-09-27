import unittest
import urllib.request
import json
import threading
import time
import socket
from mock_server import run_server

class TestMockServer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Encontra uma porta livre para o teste
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(('', 0))
        cls.port = s.getsockname()[1]
        s.close()

        # Inicia o servidor mockado em uma thread separada
        cls.server_thread = threading.Thread(target=run_server, args=(cls.port,))
        cls.server_thread.daemon = True
        cls.server_thread.start()
        time.sleep(1) # Aguarda o servidor subir

    def test_map_data_schema(self):
        url = f"http://localhost:{self.port}/map_data"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode())
            
            self.assertIn("x", data)
            self.assertIn("y", data)
            self.assertIn("yaw", data)
            self.assertIn("walls", data)
            self.assertIn("radarObjects", data)
            
            self.assertTrue(isinstance(data["x"], float))
            self.assertTrue(isinstance(data["radarObjects"], list))

    def test_dashboard_html_status(self):
        url = f"http://localhost:{self.port}/"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            html = response.read().decode()
            self.assertIn("<html", html)

    def test_move_endpoint(self):
        url = f"http://localhost:{self.port}/move"
        req = urllib.request.Request(url, data=b"forward")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)

    def test_add_waypoint(self):
        # 1. Adiciona o waypoint
        url = f"http://localhost:{self.port}/add_waypoint"
        req = urllib.request.Request(url, data=b"Cozinha")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            
        # 2. Verifica se ele aparece no map_data
        url_data = f"http://localhost:{self.port}/map_data"
        req_data = urllib.request.Request(url_data)
        with urllib.request.urlopen(req_data) as response:
            data = json.loads(response.read().decode())
            wps = data.get("waypoints", [])
            names = [w["name"] for w in wps]
            self.assertIn("Cozinha", names)

    def test_goto_waypoint(self):
        url = f"http://localhost:{self.port}/goto_waypoint"
        req = urllib.request.Request(url, data=b"Cozinha")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)

    def test_future_path_behavior(self):
        # 1. Test when robot is stopped/manual
        url_move = f"http://localhost:{self.port}/move"
        req_move = urllib.request.Request(url_move, data=b"forward")
        urllib.request.urlopen(req_move)
        time.sleep(0.5)
        
        req_stop = urllib.request.Request(url_move, data=b"stop")
        urllib.request.urlopen(req_stop)
        
        url_data = f"http://localhost:{self.port}/map_data"
        req_data = urllib.request.Request(url_data)
        with urllib.request.urlopen(req_data) as response:
            data = json.loads(response.read().decode())
            path = data.get("path", [])
            self.assertEqual(len(path), 1, "In manual/stop mode, path should only contain the current position.")
            
        # 2. Test when robot is in goto mode
        # First save a waypoint
        url_add_wp = f"http://localhost:{self.port}/add_waypoint"
        req_add_wp = urllib.request.Request(url_add_wp, data=b"TargetWaypoint")
        urllib.request.urlopen(req_add_wp)
        
        # Trigger goto waypoint
        url_goto = f"http://localhost:{self.port}/goto_waypoint"
        req_goto = urllib.request.Request(url_goto, data=b"TargetWaypoint")
        urllib.request.urlopen(req_goto)
        
        # Check that path now contains 2 points (current and target)
        with urllib.request.urlopen(req_data) as response:
            data = json.loads(response.read().decode())
            path = data.get("path", [])
            self.assertEqual(len(path), 2, "In goto mode, path should contain current position and target waypoint.")
            self.assertEqual(path[-1][0], data["target_x"], "The second point should match target_x")
            self.assertEqual(path[-1][1], data["target_y"], "The second point should match target_y")


if __name__ == "__main__":
    unittest.main()
