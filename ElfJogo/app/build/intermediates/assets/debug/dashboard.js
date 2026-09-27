// --- UI Logic, Controls, and Developer Mode ---
let waypoints = [];
let activeCommand = "stop";
let isDevMode = false;
const pressedKeys = new Set();
let moveInterval = null;

function toggleDevMode() {
  isDevMode = !isDevMode;
  document.getElementById("dev-panel").style.display = isDevMode ? "block" : "none";
  document.getElementById("btn-dev").classList.toggle("btn-primary", isDevMode);

  if (isDevMode && document.getElementById("ir-grid").children.length === 0) {
      initDevUI();
  }
}

function initDevUI() {
  const grid = document.getElementById("ir-grid");
  const visual = document.getElementById("parking-visual");
  for (let i = 1; i <= 17; i++) {
      const item = document.createElement("div");
      item.className = "ir-item";
      item.innerHTML = `
          <span>IR ${i}</span>
          <span class="ir-val" id="ir-val-${i}">0</span>
          <div class="ir-bar"><div class="ir-fill" id="ir-fill-${i}" style="width: 0%"></div></div>
      `;
      grid.appendChild(item);

      const arc = document.createElement("div");
      arc.className = "ir-arc";
      arc.id = `ir-arc-${i}`;
      const angle = (i / 17) * 360 - 90;
      const radius = 80;
      arc.style.width = `${radius * 2}px`;
      arc.style.height = `${radius * 2}px`;
      arc.style.borderTopColor = "rgba(16, 185, 129, 0.4)";
      arc.style.transform = `rotate(${angle}deg)`;
      visual.appendChild(arc);
  }
}

function updateDevUI(data) {
  if (data.isRecording !== undefined) {
    const btn = document.getElementById("btn-record");
    btn.innerText = data.isRecording ? "⏹️ STOP" : "🔴 REC";
    btn.style.backgroundColor = data.isRecording ? "rgba(244, 63, 94, 0.2)" : "";
  }

  if (data.isNavigating !== undefined) {
    const btn = document.getElementById("btn-play");
    btn.innerText = data.isNavigating ? "⏹️ STOP" : "▶️ PLAY";
    btn.style.backgroundColor = data.isNavigating ? "rgba(16, 185, 129, 0.2)" : "";
  }

  // v27: Status da Astra Cam no Main Dashboard
  if (data.astraActive !== undefined) {
      const astraBtn = document.getElementById("btn-camera");
      if (data.astraActive) {
          astraBtn.style.boxShadow = "0 0 10px #0f0";
          astraBtn.title = `Astra Ativa (${data.astraFrames} frames)`;
      } else {
          astraBtn.style.boxShadow = "none";
          astraBtn.title = "Astra Offline";
      }
  }

  // v20: Status de movimento
  if (data.isStopped !== undefined) {
      const statusSpan = document.getElementById("stat-status");
      statusSpan.innerText = data.isStopped ? "Estacionário" : "Em Movimento";
      statusSpan.style.color = data.isStopped ? "var(--success)" : "var(--primary)";
  }

  if (!isDevMode) return;

  if (data.gyroRaw !== undefined) {
      document.getElementById("stat-gyro").innerText = data.gyroRaw.toFixed(2);
  }

  // Fallback visual: o ícone da bússola na interface deve seguir o Gyro 
  // já que o acelerômetro não está disponível no Sanbot Elf.
  if (data.yaw !== undefined) {
      // data.yaw está em radianos (CCW = Positivo)
      // No CSS, rotate(deg) é CW = Positivo.
      // Então precisamos converter pra graus e inverter o sinal para bater com a tela.
      const yawDeg = data.yaw * (180 / Math.PI);
      const icon = document.getElementById("robot-compass-icon");
      if (icon) {
          icon.style.transform = `rotate(${-yawDeg}deg)`;
      }
      
      // Também podemos mostrar o ângulo legível na HUD
      const statYaw = document.getElementById("stat-yaw");
      if (statYaw) {
          statYaw.innerText = Math.round(yawDeg);
      }
  }

  if (!data.infrared) return;

  data.infrared.forEach((val, id) => {
      if (id === 0 || id > 17) return;
      const valSpan = document.getElementById(`ir-val-${id}`);
      const fill = document.getElementById(`ir-fill-${id}`);
      const arc = document.getElementById(`ir-arc-${id}`);

      if (valSpan) valSpan.innerText = val;

      if (fill) {
          const percent = Math.min(100, (val / 50) * 100);
          fill.style.width = `${percent}%`;

          let color = "var(--success)";
          if (val < 10) color = "var(--danger)";
          else if (val < 25) color = "#f59e0b";

          fill.style.backgroundColor = color;

          if (arc) {
              // FIX: Correct color replacement logic to avoid SyntaxError
              let arcColor = color === "var(--danger)" ? "rgba(239, 68, 68, 0.5)" : "rgba(16, 185, 129, 0.5)";
              if (color === "#f59e0b") arcColor = "rgba(245, 158, 11, 0.5)";

              arc.style.borderTopColor = arcColor;
              const arcRadius = 50 + (val > 50 ? 50 : val);
              arc.style.width = `${arcRadius * 2}px`;
              arc.style.height = `${arcRadius * 2}px`;
          }
      }
  });
}

function sendAction(act) {
  fetch("/action", { method: "POST", body: act });
}

function resetPose() {
  if (confirm("Deseja resetar a posição para (0,0)?")) {
    fetch("/reset_pose", { method: "POST" });
  }
}

function addWaypoint() {
  const name = document.getElementById("wp-name").value.trim();
  if (!name) return;
  fetch("/add_waypoint", { method: "POST", body: name });
  document.getElementById("wp-name").value = "";
}

function goToWaypoint(name) {
  fetch("/goto_waypoint", { method: "POST", body: name });
}

function sendCommand(cmd) {
  activeCommand = cmd;
  fetch("/move", { method: "POST", body: cmd });
}

function startMoving() {
  if (moveInterval) return;
  moveInterval = setInterval(() => {
    if (activeCommand && activeCommand !== "stop") {
      fetch("/move", { method: "POST", body: activeCommand });
    }
  }, 200);
}

function stopMoving() {
  if (moveInterval) {
    clearInterval(moveInterval);
    moveInterval = null;
  }
  sendCommand("stop");
}

function updateCommandFromKeys() {
  if (pressedKeys.has("w")) activeCommand = "forward";
  else if (pressedKeys.has("s")) activeCommand = "backward";
  else if (pressedKeys.has("a")) activeCommand = "left";
  else if (pressedKeys.has("d")) activeCommand = "right";
  else activeCommand = "stop";
}

document.addEventListener("keydown", (e) => {
  const key = e.key.toLowerCase();
  if (["w", "a", "s", "d"].includes(key)) {
    if (!pressedKeys.has(key)) {
      pressedKeys.add(key);
      document.getElementById(`btn-${key}`).classList.add("active");
      const prevCommand = activeCommand;
      updateCommandFromKeys();
      if (activeCommand !== prevCommand) {
        sendCommand(activeCommand);
      }
      startMoving();
    }
  }
});

document.addEventListener("keyup", (e) => {
  const key = e.key.toLowerCase();
  if (["w", "a", "s", "d"].includes(key)) {
    pressedKeys.delete(key);
    document.getElementById(`btn-${key}`).classList.remove("active");
    const prevCommand = activeCommand;
    updateCommandFromKeys();
    if (pressedKeys.size === 0) {
      stopMoving();
    } else if (activeCommand !== prevCommand) {
      sendCommand(activeCommand);
    }
  }
});

let isCameraVisible = false;
function toggleCamera() {
  isCameraVisible = !isCameraVisible;
  const panel = document.getElementById("camera-panel");
  const btn = document.getElementById("btn-camera");
  const slamTitle = panel.querySelector('h4');

  if (isCameraVisible) {
      panel.style.display = "block";
      btn.innerText = "🔄 Esconder Mapa SLAM";
      slamTitle.innerText = "Mapa SLAM em Tempo Real (Astra Cam)";
      pollCamera();
  } else {
      panel.style.display = "none";
      btn.innerText = "🔄 Visão SLAM";
  }
}

function pollCamera() {
  if (!isCameraVisible) return;
  // v26: A visualização SLAM é feita via Three.js no pollMapData
  // Não precisamos de imagem JPEG aqui se o foco é o mapa de grade
  // Mas vamos manter o indicador de atividade da Astra
  const img = document.getElementById("camera-stream");
  img.style.display = "none"; // Esconde imagem estática
  setTimeout(pollCamera, 500);
}

function updateWaypointUI(newWaypoints) {
  if (!newWaypoints || JSON.stringify(newWaypoints) === JSON.stringify(waypoints)) return;
  waypoints = newWaypoints;
  const list = document.getElementById("waypoint-list");
  list.innerHTML = "";
  waypoints.forEach((wp) => {
    const item = document.createElement("div");
    item.className = "waypoint-item";
    item.innerHTML = `
              <div class="info">
                  <span class="name">${wp.name}</span>
                  <span class="coords">${wp.x.toFixed(2)}, ${wp.y.toFixed(2)}</span>
              </div>
              <button class="btn btn-primary" style="padding: 6px 12px; font-size: 0.85rem" onclick="goToWaypoint('${wp.name}')">Ir</button>
          `;
    list.appendChild(item);
  });
}

// Data Polling Loop - Sequential polling to prevent concurrent overlapping requests (EPIPE)
function pollMapData() {
  fetch("/map_data")
    .then((r) => r.json())
    .then((data) => {
      if (typeof drawMap === 'function') drawMap(data);
      updateDevUI(data);
      setTimeout(pollMapData, 50);
    })
    .catch((e) => {
      console.log("Update fail");
      setTimeout(pollMapData, 50);
    });
}
pollMapData();

