// --- Three.js Setup and Map Rendering ---
const container = document.getElementById("minimap-container");
const slamPreviewContainer = document.getElementById("slam-preview-container");

const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2(0x111111, 0.04);

// Cameras
const aspect = container.clientWidth / container.clientHeight;
const camera3D = new THREE.PerspectiveCamera(60, aspect, 0.1, 200);

const frustumSize = 15;
const camera2D = new THREE.OrthographicCamera(
  -frustumSize * aspect,
  frustumSize * aspect,
  frustumSize,
  -frustumSize,
  0.1,
  200,
);
camera2D.position.set(0, 20, 0);
camera2D.lookAt(0, 0, 0);

// v26: Câmera para o Preview SLAM (Estilo ROS)
const cameraSLAM = new THREE.PerspectiveCamera(45, 400/300, 0.1, 100);
cameraSLAM.position.set(0, 8, 8);
cameraSLAM.lookAt(0, 0, 0);

let activeCamera = camera3D;
let is3D = true;

function toggleCamera() {
  is3D = !is3D;
  activeCamera = is3D ? camera3D : camera2D;
  document.getElementById("btn-camera").innerText = is3D
    ? "🔄 Mudar para Mapa 2D"
    : "🔄 Mudar para Visão 3D";
}

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(container.clientWidth, container.clientHeight);
renderer.setClearColor(0x111111);
container.appendChild(renderer.domElement);

// v26: Segundo renderizador para a janelinha SLAM
const rendererSLAM = new THREE.WebGLRenderer({ antialias: true });
rendererSLAM.setSize(400, 300);
rendererSLAM.setClearColor(0x1a1a1a); // Background mais escuro estilo ROS
slamPreviewContainer.appendChild(rendererSLAM.domElement);

// v26: Camadas (Layers) para separar visualizações
// Layer 0: Default (Tudo)
// Layer 1: SLAM Map (Apenas a grade e robô)
const LAYER_SLAM = 1;
cameraSLAM.layers.enable(LAYER_SLAM);
cameraSLAM.layers.disable(0); // SLAM view não vê o mundo high-poly

window.addEventListener("resize", () => {
  const w = container.clientWidth;
  const h = container.clientHeight;
  renderer.setSize(w, h);
  const a = w / h;
  camera3D.aspect = a;
  camera3D.updateProjectionMatrix();
  camera2D.left = -frustumSize * a;
  camera2D.right = frustumSize * a;
  camera2D.updateProjectionMatrix();
});

const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
scene.add(ambientLight);
const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
dirLight.position.set(10, 20, 10);
scene.add(dirLight);

// v26: Luzes para a Layer SLAM (Sem luz, tudo fica preto)
const ambientLightSLAM = new THREE.AmbientLight(0xffffff, 0.8);
ambientLightSLAM.layers.set(LAYER_SLAM);
scene.add(ambientLightSLAM);

const dirLightSLAM = new THREE.DirectionalLight(0xffffff, 0.5);
dirLightSLAM.position.set(-10, 20, -10);
dirLightSLAM.layers.set(LAYER_SLAM);
scene.add(dirLightSLAM);

// Asfalto / Chão Dark FSD
const floorGeo = new THREE.PlaneGeometry(200, 200);
const floorMat = new THREE.MeshStandardMaterial({
  color: 0x161616,
  roughness: 0.9,
  metalness: 0.1,
});
const floor = new THREE.Mesh(floorGeo, floorMat);
floor.rotation.x = -Math.PI / 2;
scene.add(floor);

// Sanbot Elf (Modelagem Suave e Direcionada ao FSD)
const robotGroup = new THREE.Group();

const bodyMat = new THREE.MeshStandardMaterial({
  color: 0xfafafa,
  roughness: 0.1,
  metalness: 0.1,
});
const darkMat = new THREE.MeshStandardMaterial({
  color: 0x111111,
  roughness: 0.8,
});

// 1. Base Rodas (Preto e Cinza Metálico, Perfil Baixo)
const baseGeo = new THREE.CylinderGeometry(0.24, 0.24, 0.08, 32);
const baseMesh = new THREE.Mesh(baseGeo, darkMat);
baseMesh.position.y = 0.04;
robotGroup.add(baseMesh);

// 2. Tronco / Corpo em Peça Única (Ampulheta Suave e Contínua)
const pointsBody = [];
const n = 40;
for (let i = 0; i <= n; i++) {
  const t = i / n;
  const y = t * 0.75;
  let r;
  if (t < 0.4) r = 0.22 - 0.09 * Math.sin((t / 0.4) * (Math.PI / 2));
  else r = 0.13 + 0.04 * Math.sin(((t - 0.4) / 0.6) * (Math.PI / 2));
  pointsBody.push(new THREE.Vector2(Math.max(r, 0.01), y));
}
const bodyGeo = new THREE.LatheGeometry(pointsBody, 64);
const bodyMesh = new THREE.Mesh(bodyGeo, bodyMat);
bodyMesh.position.y = 0.08;
robotGroup.add(bodyMesh);

// 3. Braços / Flaps Laterais (Cilindros ovais lisos)
const armGeo = new THREE.CylinderGeometry(0.04, 0.02, 0.4, 32);
const armL = new THREE.Mesh(armGeo, bodyMat);
armL.position.set(0.18, 0.58, 0.0);
armL.rotation.z = -0.15;
robotGroup.add(armL);

const armR = new THREE.Mesh(armGeo, bodyMat);
armR.position.set(-0.18, 0.58, 0.0);
armR.rotation.z = 0.15;
robotGroup.add(armR);

// 4. Cabeça (Tablet - Rosto voltado para Frente -Z)
const headGroup = new THREE.Group();
headGroup.position.set(0, 0.94, -0.04);
headGroup.rotation.x = -0.15; // Inclinado levemente para cima pra encarar o humano

const headGeo = new THREE.BoxGeometry(0.26, 0.18, 0.05);
const headMesh = new THREE.Mesh(headGeo, bodyMat);
headGroup.add(headMesh);

const screenGeo = new THREE.BoxGeometry(0.23, 0.15, 0.01);
const screenMat = new THREE.MeshStandardMaterial({
  color: 0x050505,
  roughness: 0.1,
  metalness: 0.9,
});
const screenMesh = new THREE.Mesh(screenGeo, screenMat);
screenMesh.position.set(0, 0, -0.026); // Na FRENTE da cabeça (-Z)
headGroup.add(screenMesh);

// Olhos Neon
const eyeMat = new THREE.MeshBasicMaterial({ color: 0x00e5ff });
const eyeGeo = new THREE.SphereGeometry(0.015, 32, 32);
const eyeL = new THREE.Mesh(eyeGeo, eyeMat);
eyeL.position.set(0.06, 0.02, -0.03);
eyeL.scale.set(1, 0.5, 0.2); // Formato de olho elegante
headGroup.add(eyeL);
const eyeR = new THREE.Mesh(eyeGeo, eyeMat);
eyeR.position.set(-0.06, 0.02, -0.03);
eyeR.scale.set(1, 0.5, 0.2);
headGroup.add(eyeR);

robotGroup.add(headGroup);

// 5. LED do Peito (Coração Sanbot - Frente)
const ledGeo = new THREE.SphereGeometry(0.03, 32, 32);
const ledMat = new THREE.MeshBasicMaterial({ color: 0xff0055 });
const ledMesh = new THREE.Mesh(ledGeo, ledMat);
ledMesh.position.set(0, 0.65, -0.14); // Embutido suavemente na frente
ledMesh.scale.set(1, 1, 0.3); // Achatado ao torso
robotGroup.add(ledMesh);

scene.add(robotGroup);
robotGroup.layers.enable(LAYER_SLAM); // Robô aparece em ambas

let objectsGroup = new THREE.Group();
scene.add(objectsGroup);

// --- Construtores de Modelos Minimalistas Low-Poly ---

function createPersonModel(material) {
  const group = new THREE.Group();
  const headGeo = new THREE.IcosahedronGeometry(0.09, 1);
  const head = new THREE.Mesh(headGeo, material);
  head.position.y = 1.15;
  group.add(head);
  const torsoGeo = new THREE.CylinderGeometry(0.14, 0.11, 0.45, 8);
  const torso = new THREE.Mesh(torsoGeo, material);
  torso.position.y = 0.85;
  group.add(torso);
  const legGeo = new THREE.CylinderGeometry(0.04, 0.03, 0.55, 6);
  const legL = new THREE.Mesh(legGeo, material);
  legL.position.set(0.06, 0.35, 0);
  group.add(legL);
  const legR = new THREE.Mesh(legGeo, material);
  legR.position.set(-0.06, 0.35, 0);
  group.add(legR);
  const armGeo = new THREE.CylinderGeometry(0.03, 0.025, 0.40, 6);
  const armL = new THREE.Mesh(armGeo, material);
  armL.position.set(0.18, 0.82, 0);
  armL.rotation.z = -0.15;
  group.add(armL);
  const armR = new THREE.Mesh(armGeo, material);
  armR.position.set(-0.18, 0.82, 0);
  armR.rotation.z = 0.15;
  group.add(armR);
  return group;
}

function createDogModel(material) {
  const group = new THREE.Group();
  const bodyGeo = new THREE.CylinderGeometry(0.10, 0.08, 0.40, 8);
  const body = new THREE.Mesh(bodyGeo, material);
  body.rotation.z = Math.PI / 2;
  body.position.set(0, 0.25, 0);
  group.add(body);
  const headGeo = new THREE.IcosahedronGeometry(0.07, 0);
  const head = new THREE.Mesh(headGeo, material);
  head.position.set(0.18, 0.38, 0);
  group.add(head);
  const snoutGeo = new THREE.CylinderGeometry(0.02, 0.04, 0.08, 6);
  const snout = new THREE.Mesh(snoutGeo, material);
  snout.rotation.z = -Math.PI / 2;
  snout.position.set(0.24, 0.36, 0);
  group.add(snout);
  const legGeo = new THREE.CylinderGeometry(0.025, 0.015, 0.18, 6);
  const legFL = new THREE.Mesh(legGeo, material);
  legFL.position.set(0.12, 0.09, 0.06);
  group.add(legFL);
  const legFR = new THREE.Mesh(legGeo, material);
  legFR.position.set(0.12, 0.09, -0.06);
  group.add(legFR);
  const legBL = new THREE.Mesh(legGeo, material);
  legBL.position.set(-0.12, 0.09, 0.06);
  group.add(legBL);
  const legBR = new THREE.Mesh(legGeo, material);
  legBR.position.set(-0.12, 0.09, -0.06);
  group.add(legBR);
  const tailGeo = new THREE.CylinderGeometry(0.01, 0.02, 0.15, 5);
  const tail = new THREE.Mesh(tailGeo, material);
  tail.position.set(-0.22, 0.32, 0);
  tail.rotation.z = -Math.PI / 4;
  group.add(tail);
  return group;
}

function createCarModel(material) {
  const group = new THREE.Group();
  const chassisGeo = new THREE.BoxGeometry(1.0, 0.22, 0.48);
  const chassis = new THREE.Mesh(chassisGeo, material);
  chassis.position.y = 0.18;
  group.add(chassis);
  const cabinGeo = new THREE.BoxGeometry(0.50, 0.18, 0.40);
  const cabin = new THREE.Mesh(cabinGeo, material);
  cabin.position.set(-0.10, 0.38, 0);
  group.add(cabin);
  const glassMat = new THREE.MeshStandardMaterial({color: 0x0a0a0a, roughness: 0.1, metalness: 0.9});
  const windshieldGeo = new THREE.BoxGeometry(0.52, 0.16, 0.42);
  const windshield = new THREE.Mesh(windshieldGeo, glassMat);
  windshield.position.set(-0.10, 0.38, 0);
  group.add(windshield);
  const wheelGeo = new THREE.CylinderGeometry(0.12, 0.12, 0.08, 16);
  const wheelMat = new THREE.MeshStandardMaterial({color: 0x111111, roughness: 0.9});
  const wPositions = [[0.32, 0.12, 0.24], [0.32, 0.12, -0.24], [-0.32, 0.12, 0.24], [-0.32, 0.12, -0.24]];
  wPositions.forEach(pos => {
      const wheel = new THREE.Mesh(wheelGeo, wheelMat);
      wheel.rotation.x = Math.PI / 2;
      wheel.position.set(pos[0], pos[1], pos[2]);
      group.add(wheel);
  });
  return group;
}

function createGenericModel(material) {
  const group = new THREE.Group();
  const boxGeo = new THREE.BoxGeometry(0.3, 0.3, 0.3);
  const box = new THREE.Mesh(boxGeo, material);
  box.position.y = 0.15;
  group.add(box);
  return group;
}

let pathLine = null;
let gridMeshLivre = null; // InstancedMesh para chão cinza
let gridMeshParede = null; // InstancedMesh para paredes pretas
const GRID_SIZE = 200;
const GRID_RES = 0.1;

function updateOccupancyGrid(base64Data) {
  if (!base64Data) {
      console.warn("SLAM -> gridBase64 está nulo!");
      return;
  }

  const binaryString = window.atob(base64Data);
  const len = binaryString.length;
  const grid = new Uint8Array(len);
  for (let i = 0; i < len; i++) grid[i] = binaryString.charCodeAt(i);

  if (!gridMeshLivre) {
    const floorGeo = new THREE.PlaneGeometry(GRID_RES * 1.1, GRID_RES * 1.1); // v27: Overlap para remover gaps
    const floorMat = new THREE.MeshStandardMaterial({ color: 0xcccccc });
    gridMeshLivre = new THREE.InstancedMesh(floorGeo, floorMat, GRID_SIZE * GRID_SIZE);
    gridMeshLivre.layers.set(LAYER_SLAM); // SÓ Aparece na visão SLAM
    scene.add(gridMeshLivre);

    const wallGeo = new THREE.BoxGeometry(GRID_RES * 1.1, 0.2, GRID_RES * 1.1); // v27: Paredes visíveis
    const wallMat = new THREE.MeshStandardMaterial({ color: 0x111111 });
    gridMeshParede = new THREE.InstancedMesh(wallGeo, wallMat, GRID_SIZE * GRID_SIZE);
    gridMeshParede.layers.set(LAYER_SLAM);
    scene.add(gridMeshParede);

    console.log("InstancedMesh SLAM Criada");
  }

  renderGridInstanced(grid);
}

function renderGridInstanced(grid) {
  const offset = (GRID_SIZE * GRID_RES) / 2;
  const dummy = new THREE.Object3D();

  let livreCount = 0;
  let paredeCount = 0;

  // v26: Otimização RADICAL - Limita o processamento para caber na InstancedMesh
  const totalCells = GRID_SIZE * GRID_SIZE;
  const maxInstances = GRID_SIZE * GRID_SIZE;

  for (let i = 0; i < totalCells; i++) {
    const val = grid[i];
    if (val === 0) continue;

    const gx = i % GRID_SIZE;
    const gy = Math.floor(i / GRID_SIZE);

    const worldX = gx * GRID_RES - offset;
    const worldY = gy * GRID_RES - offset;

    // v27: Altura ligeiramente maior para garantir visibilidade sobre o plano de fundo
    dummy.position.set(-worldY, val === 2 ? 0.1 : 0.05, -worldX);

    if (val === 1) {
      if (livreCount < maxInstances) {
        dummy.rotation.x = -Math.PI / 2;
        dummy.scale.set(1, 1, 1);
        dummy.updateMatrix();
        gridMeshLivre.setMatrixAt(livreCount++, dummy.matrix);
      }
    } else if (val === 2) {
      if (paredeCount < maxInstances) {
        dummy.rotation.x = 0;
        dummy.scale.set(1, 10, 1); // Paredes mais altas para efeito 2.5D
        dummy.updateMatrix();
        gridMeshParede.setMatrixAt(paredeCount++, dummy.matrix);
      }
    }
  }

  gridMeshLivre.count = livreCount; // v27: Garante que o Three.js saiba quantos desenhar
  gridMeshLivre.instanceMatrix.needsUpdate = true;
  gridMeshParede.count = paredeCount;
  gridMeshParede.instanceMatrix.needsUpdate = true;
}

// Estado cinemático local para interpolação suave (60 FPS)
let currentX = 0, currentZ = 0, currentYaw = 0;
let targetX = 0, targetZ = 0, targetYaw = 0;

function drawMap(data) {
  // v26: Atualiza Grade de Ocupação SLAM
  if (data.gridBase64) {
    updateOccupancyGrid(data.gridBase64);
  }

  // CORREÇÃO CINEMÁTICA
  targetX = -data.y;
  targetZ = -data.x;

  // Normalize o Yaw alvo para evitar giro de 360 ao passar de PI para -PI
  let nYaw = data.yaw;
  while (nYaw - currentYaw > Math.PI) nYaw -= 2 * Math.PI;
  while (nYaw - currentYaw < -Math.PI) nYaw += 2 * Math.PI;
  targetYaw = nYaw;

  // Limpa Dinâmicos
  while (objectsGroup.children.length > 0) {
    objectsGroup.remove(objectsGroup.children[0]);
  }
  if (pathLine) scene.remove(pathLine);

  // Paredes FSD (Discos cinza fosco de baixo perfil no chão)
  if (data.walls) {
    const wallMat = new THREE.MeshStandardMaterial({
      color: 0x242424,
      roughness: 0.95,
    });
    const wallGeo = new THREE.CylinderGeometry(0.1, 0.1, 0.01, 12);
    data.walls.forEach((pt) => {
      const disk = new THREE.Mesh(wallGeo, wallMat);
      disk.position.set(-pt[1], 0.005, -pt[0]);
      objectsGroup.add(disk);
    });
  }

  // Radar Objects (Modelos Minimalistas Low-Poly FSD)
  if (data.radarObjects) {
    const personMat = new THREE.MeshStandardMaterial({
      color: 0x60a5fa,
      transparent: true,
      opacity: 0.6,
      roughness: 0.3,
      metalness: 0.1,
    });
    const carMat = new THREE.MeshStandardMaterial({
      color: 0x475569,
      transparent: true,
      opacity: 0.7,
      roughness: 0.3,
      metalness: 0.2,
    });
    const dogMat = new THREE.MeshStandardMaterial({
      color: 0xf59e0b,
      transparent: true,
      opacity: 0.6,
      roughness: 0.3,
      metalness: 0.1,
    });

    data.radarObjects.forEach((obj) => {
      let model;
      if (obj.type === 1) {
        model = createPersonModel(personMat);
        model.position.set(-obj.y, 0, -obj.x);
      } else if (obj.type === 2) {
        model = createDogModel(dogMat);
        model.position.set(-obj.y, 0, -obj.x);
      } else if (obj.type === 5) {
        model = createCarModel(carMat);
        model.position.set(-obj.y, 0, -obj.x);
        model.rotation.y = 0.5;
      } else {
        model = createGenericModel(carMat);
        model.position.set(-obj.y, 0, -obj.x);
      }
      objectsGroup.add(model);
    });
  }

  // Caminho Azul Tubo (Tesla Path)
  if (data.path && data.path.length > 1) {
    const points = data.path.map((pt) => new THREE.Vector3(-pt[1], 0.05, -pt[0]));
    let curvePoints = [];
    if (points.length === 2) {
      const p0 = points[0];
      const p2 = points[1];
      const dist = p0.distanceTo(p2);
      const fwd = new THREE.Vector3(-Math.sin(currentYaw), 0, -Math.cos(currentYaw));
      const p1 = new THREE.Vector3().copy(p0).addScaledVector(fwd, dist * 0.4);
      const numSteps = 24;
      for (let i = 0; i <= numSteps; i++) {
        const t = i / numSteps;
        const pt = new THREE.Vector3();
        pt.x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x;
        pt.y = 0.05;
        pt.z = (1 - t) * (1 - t) * p0.z + 2 * (1 - t) * t * p1.z + t * t * p2.z;
        curvePoints.push(pt);
      }
    } else {
      curvePoints = points;
    }
    const validPoints = [curvePoints[0]];
    for (let i = 1; i < curvePoints.length; i++) {
      if (curvePoints[i].distanceTo(validPoints[validPoints.length - 1]) > 0.02) {
        validPoints.push(curvePoints[i]);
      }
    }
    if (validPoints.length > 1) {
      const pathCurve = new THREE.CatmullRomCurve3(validPoints);
      const pathGeo = new THREE.TubeGeometry(pathCurve, validPoints.length * 4, 0.08, 8, false);
      const pathMat = new THREE.MeshBasicMaterial({ color: 0x3b82f6 });
      pathLine = new THREE.Mesh(pathGeo, pathMat);
      scene.add(pathLine);
    }
  }

  document.getElementById("stat-x").innerText = data.x.toFixed(2);
  document.getElementById("stat-y").innerText = data.y.toFixed(2);
  document.getElementById("stat-yaw").innerText = ((data.yaw * 180) / Math.PI).toFixed(0);
  if (typeof updateWaypointUI === 'function') updateWaypointUI(data.waypoints);
}

function animate() {
  requestAnimationFrame(animate);
  currentX = THREE.MathUtils.lerp(currentX, targetX, 0.2);
  currentZ = THREE.MathUtils.lerp(currentZ, targetZ, 0.2);
  currentYaw = THREE.MathUtils.lerp(currentYaw, targetYaw, 0.25);
  robotGroup.position.set(currentX, 0, currentZ);
  robotGroup.rotation.y = currentYaw;

  if (is3D) {
    const camDist = 4.5;
    const camHeight = 2.5;
    const offsetZ = Math.cos(currentYaw) * camDist;
    const offsetX = Math.sin(currentYaw) * camDist;
    camera3D.position.set(currentX + offsetX, camHeight, currentZ + offsetZ);
    const lookZ = currentZ - Math.cos(currentYaw) * 5;
    const lookX = currentX - Math.sin(currentYaw) * 5;
    camera3D.lookAt(lookX, 0, lookZ);
  } else {
    camera2D.position.x = currentX;
    camera2D.position.z = currentZ;
  }

  // Renderiza cena principal
  renderer.render(scene, activeCamera);

  // v26: Renderiza janela SLAM (Preview persistente)
  if (document.getElementById("camera-panel").style.display !== "none") {
      // v27: Câmera SLAM sempre focada no robô mas de cima (Orthographic ou Top-down)
      cameraSLAM.position.set(currentX, 10, currentZ + 0.1); // Leve offset para evitar gimbal lock
      cameraSLAM.lookAt(currentX, 0, currentZ);
      rendererSLAM.render(scene, cameraSLAM);
  }
}
animate();
