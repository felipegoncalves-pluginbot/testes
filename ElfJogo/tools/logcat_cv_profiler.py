#!/usr/bin/env python3
"""
Logcat CV Profiler & Telemetry Analyzer para Sanbot Elf.
Monitora em tempo real o desempenho de visão computacional, latência por quadro,
quedas de FPS e pausas de Garbage Collection via ADB.
"""

import sys
import subprocess
import time
import re
import json

def run_profiler(serial=None):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["logcat", "-v", "time", "-s", "KinectTrackingEngine:V", "Choreographer:I", "art:I", "CV_TELEMETRY:I"]

    print("=" * 60)
    print("🤖 SANBOT ELF - LOGCAT CV & PERFORMANCE PROFILER")
    print(f"📡 Conectando ao logcat do dispositivo...")
    print("=" * 60)

    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    except FileNotFoundError:
        print("❌ Erro: ADB não encontrado no PATH do sistema.")
        sys.exit(1)

    total_frames = 0
    skipped_frames_total = 0
    latencies = []
    start_time = time.time()

    latency_regex = re.compile(r"processingLatencyMs[:=]\s*(\d+)")
    choreographer_regex = re.compile(r"Skipped\s+(\d+)\s+frames")
    gc_regex = re.compile(r"GC freed.*time:\s*(\d+)ms")

    try:
        for line in proc.stdout:
            line = line.strip()
            if not line:
                continue

            # Latência de Processamento da Visão
            lat_match = latency_regex.search(line)
            if lat_match:
                lat = int(lat_match.group(1))
                latencies.append(lat)
                total_frames += 1
                status = "✅ OK" if lat < 33 else ("⚠️ SLOW" if lat < 60 else "🚨 CRITICAL")
                print(f"[{status}] Frame #{total_frames:04d} | Latência: {lat}ms")

            # Quedas de Quadros do Choreographer
            ch_match = choreographer_regex.search(line)
            if ch_match:
                skipped = int(ch_match.group(1))
                skipped_frames_total += skipped
                print(f"⚠️ [JANK] Choreographer: {skipped} frames descartados!")

            # Pausas do Garbage Collector
            gc_match = gc_regex.search(line)
            if gc_match:
                gc_time = gc_match.group(1)
                print(f"🧹 [GC] Garbage Collection: pausa de {gc_time}ms detectada!")

    except KeyboardInterrupt:
        print("\n" + "=" * 60)
        elapsed = time.time() - start_time
        avg_lat = sum(latencies) / len(latencies) if latencies else 0
        fps = total_frames / elapsed if elapsed > 0 else 0

        summary = {
            "elapsed_seconds": round(elapsed, 2),
            "total_frames_processed": total_frames,
            "average_fps": round(fps, 1),
            "average_latency_ms": round(avg_lat, 2),
            "total_skipped_frames": skipped_frames_total,
            "health_status": "EXCELLENT" if avg_lat < 25 and skipped_frames_total == 0 else "NEEDS_OPTIMIZATION"
        }

        print("📊 RELATÓRIO DE TELEMETRIA DE VISÃO:")
        print(json.dumps(summary, indent=2))
        print("=" * 60)
        proc.terminate()

if __name__ == "__main__":
    serial = sys.argv[1] if len(sys.argv) > 1 else None
    run_profiler(serial)
