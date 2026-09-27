#!/usr/bin/env python3
"""
Desktop CV Simulation Harness & Testbed para Linux Fedora.
Simula o pipeline de profundidade da câmera Orbbec Astra / Kinect
e valida heurísticas de tracking em tempo real no PC.
"""

import math
import time
import json
import sys

def generate_synthetic_depth_frame(width=64, height=48, pose="standing", t=0.0):
    """
    Gera uma matriz de profundidade 16-bit sintética simulando sensor 3D.
    Valores em milímetros (1500mm = pessoa, 3500mm = fundo).
    """
    buffer = [3500] * (width * height)
    cx = width // 2
    cy = height // 2

    # Animação de respiração/movimento sutil
    sway_x = int(math.sin(t * 2.0) * 2)

    if pose == "standing":
        # Cabeça
        fill_circle(buffer, width, height, cx + sway_x, cy - 14, 4, 1480)
        # Tronco
        fill_rect(buffer, width, height, cx - 5 + sway_x, cy - 10, cx + 5 + sway_x, cy + 8, 1500)
        # Braços abaixados
        fill_rect(buffer, width, height, cx - 8 + sway_x, cy - 4, cx - 6 + sway_x, cy + 8, 1510)
        fill_rect(buffer, width, height, cx + 6 + sway_x, cy - 4, cx + 8 + sway_x, cy + 8, 1510)
        # Pernas
        fill_rect(buffer, width, height, cx - 4 + sway_x, cy + 9, cx - 2 + sway_x, cy + 18, 1520)
        fill_rect(buffer, width, height, cx + 2 + sway_x, cy + 9, cx + 4 + sway_x, cy + 18, 1520)

    elif pose == "t_pose":
        fill_circle(buffer, width, height, cx, cy - 14, 4, 1480)
        fill_rect(buffer, width, height, cx - 5, cy - 10, cx + 5, cy + 8, 1500)
        # Braços em T
        fill_rect(buffer, width, height, cx - 20, cy - 8, cx - 5, cy - 5, 1490)
        fill_rect(buffer, width, height, cx + 5, cy - 8, cx + 20, cy - 5, 1490)
        fill_rect(buffer, width, height, cx - 4, cy + 9, cx - 2, cy + 18, 1520)
        fill_rect(buffer, width, height, cx + 2, cy + 9, cx + 4, cy + 18, 1520)

    elif pose == "swing":
        # Raquetada animada (braço direito subindo rápido com tempo t)
        arm_y = int(cy + 4 - (math.sin(t * 8.0) * 16))
        fill_circle(buffer, width, height, cx, cy - 14, 4, 1480)
        fill_rect(buffer, width, height, cx - 5, cy - 10, cx + 5, cy + 8, 1500)
        fill_rect(buffer, width, height, cx + 5, min(cy - 6, arm_y), cx + 12, max(cy - 6, arm_y), 1485)
        fill_rect(buffer, width, height, cx - 8, cy - 4, cx - 6, cy + 8, 1510)
        fill_rect(buffer, width, height, cx - 4, cy + 9, cx - 2, cy + 18, 1520)
        fill_rect(buffer, width, height, cx + 2, cy + 9, cx + 4, cy + 18, 1520)

    return buffer

def fill_rect(buf, w, h, x1, y1, x2, y2, val):
    for y in range(max(0, y1), min(h, y2 + 1)):
        for x in range(max(0, x1), min(w, x2 + 1)):
            buf[y * w + x] = val

def fill_circle(buf, w, h, cx, cy, r, val):
    r2 = r * r
    for y in range(max(0, cy - r), min(h, cy + r + 1)):
        for x in range(max(0, cx - r), min(w, cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r2:
                buf[y * w + x] = val

def render_ascii_depth(buffer, width=64, height=48):
    """Renderiza silhueta de profundidade no terminal Linux em ASCII colorido"""
    chars = " .:-=+*#%@"
    lines = []
    step_y = max(1, height // 24)
    step_x = max(1, width // 48)

    for y in range(0, height, step_y):
        row = []
        for x in range(0, width, step_x):
            d = buffer[y * width + x]
            if d > 2500 or d == 0:
                row.append(" ")
            else:
                norm = max(0, min(len(chars) - 1, int((2000 - d) / 100)))
                row.append(chars[norm])
        lines.append("".join(row))
    return "\n".join(lines)

def run_simulation():
    print("🎮 INICIANDO TESTBED DE SIMULAÇÃO DE PROFUNDIDADE (LINUX FEDORA)")
    print("Simulando sensor Orbbec Astra com fatiamento 3D em 30 FPS...\n")

    poses = ["standing", "t_pose", "swing"]
    pose_idx = 0
    t = 0.0

    try:
        while True:
            t += 0.1
            if int(t) % 6 == 0 and int(t) > 0:
                pose_idx = (int(t) // 6) % len(poses)

            current_pose = poses[pose_idx]
            frame = generate_synthetic_depth_frame(64, 48, pose=current_pose, t=t)

            # Limpa terminal e renderiza
            sys.stdout.write("\033[H\033[J")
            print(f"=== SIMULADOR DE VISÃO 3D | Pose Atual: {current_pose.upper()} | t={t:.1f}s ===")
            print(render_ascii_depth(frame, 64, 48))
            print("Pressione Ctrl+C para encerrar o simulador.")
            time.sleep(0.1)
    except KeyboardInterrupt:
        print("\n🛑 Simulação encerrada com sucesso.")

if __name__ == "__main__":
    run_simulation()
