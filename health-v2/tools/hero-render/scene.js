// 首页中央动画的 three.js 场景（docs/07 第一部分"七、首页中央动画"）。render.mjs 用无头浏览器打开本页，
// 逐帧调用 window.heroFrame(t) 截图，再用 ffmpeg 合成 8 秒无缝循环视频。
//
// 地址参数：
//   scene=dashboard  动态数据页主区域：中央平台 + 全息人体 + 四个悬浮方形平台（心率、血氧、体温、血压）
//   scene=portal     入口页圆环里：全息人体站在发光圆形平台上，周围旋转光环
//   scene=preview    只看人体模型（挑模型用）
//   w、h             画面像素；model  人体模型 .glb 的地址
//
// 无缝循环：所有动画都是时间的周期函数，周期整除 LOOP（8 秒），所以第 0 帧和第 8 秒那一帧完全一样。
// 背景纯黑，页面上用 mix-blend-mode: screen 融掉黑色。
import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'

const q = new URLSearchParams(location.search)
const SCENE = q.get('scene') ?? 'dashboard'
const W = Number(q.get('w') ?? 1280)
const H = Number(q.get('h') ?? 720)
const MODEL = q.get('model') ?? '/models/human_body_wireframe.glb'
const LOOP = 8
const TAU = Math.PI * 2

// 颜色和前端 tokens.css 一致
const ACCENT = new THREE.Color('#19e6b0')
const TEAL = new THREE.Color('#14b8a6')
const CYAN = new THREE.Color('#38e8ff')
const BLUE = new THREE.Color('#2f86f2')

const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true })
renderer.setPixelRatio(1)
renderer.setSize(W, H)
renderer.setClearColor(0x000000, 1)
document.body.appendChild(renderer.domElement)

const scene = new THREE.Scene()
const camera = new THREE.PerspectiveCamera(30, W / H, 0.1, 200)
const updaters = []

// ---------------- 材质 ----------------

/** 全息材质：边缘亮（菲涅尔）、横向扫描细线、一道从下往上扫的亮带 */
function hologramMaterial(color, minY, maxY) {
  return new THREE.ShaderMaterial({
    uniforms: {
      color: { value: color.clone() },
      phase: { value: 0 },
      scanY: { value: minY },
      bandWidth: { value: (maxY - minY) * 0.04 }
    },
    vertexShader: /* glsl */ `
      varying vec3 vN; varying vec3 vV; varying float vY;
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        vY = wp.y;
        vN = normalize(mat3(modelMatrix) * normal);
        vV = normalize(cameraPosition - wp.xyz);
        gl_Position = projectionMatrix * viewMatrix * wp;
      }`,
    fragmentShader: /* glsl */ `
      uniform vec3 color; uniform float phase; uniform float scanY; uniform float bandWidth;
      varying vec3 vN; varying vec3 vV; varying float vY;
      void main() {
        float f = pow(1.0 - abs(dot(normalize(vN), normalize(vV))), 2.2);
        float lines = 0.55 + 0.45 * step(0.45, fract(vY * 36.0 - phase));
        float scan = smoothstep(bandWidth, 0.0, abs(vY - scanY));
        vec3 c = color * (0.03 + 0.75 * f) * lines + vec3(0.5, 0.9, 0.85) * scan * 0.35;
        gl_FragColor = vec4(c, 1.0);
      }`,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    side: THREE.DoubleSide
  })
}

function glow(color, opacity = 1) {
  return new THREE.MeshBasicMaterial({
    color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide
  })
}

function lineGlow(color, opacity = 1) {
  return new THREE.LineBasicMaterial({ color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false })
}

/** 画在 canvas 上的贴图：网格、图标 */
function canvasTexture(size, draw) {
  const c = document.createElement('canvas')
  c.width = c.height = size
  const g = c.getContext('2d')
  draw(g, size)
  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

const gridTexture = canvasTexture(256, (g, s) => {
  g.strokeStyle = 'rgba(56, 232, 255, 0.55)'
  g.lineWidth = 2
  for (let i = 0; i <= 8; i++) {
    const p = (i / 8) * s
    g.beginPath(); g.moveTo(p, 0); g.lineTo(p, s); g.stroke()
    g.beginPath(); g.moveTo(0, p); g.lineTo(s, p); g.stroke()
  }
})

// 图标和前端 ScreenIcon.vue 用同一套线条（24×24 的 SVG 路径）
const ICONS = {
  heart: ['M12 20s-7.5-4.6-7.5-10.1A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z',
    'M7 12h2.5l1.2-2.2 1.8 4.4 1.3-2.2H17'],
  drop: ['M12 3.5s6 6.4 6 10.6a6 6 0 0 1-12 0C6 9.9 12 3.5 12 3.5z', 'M9.5 14.5a2.6 2.6 0 0 0 2.5 2.4'],
  thermometer: ['M10 13.5V5a2 2 0 0 1 4 0v8.5a4 4 0 1 1-4 0z', 'M12 9v7'],
  pressure: ['M19.5 13a7.5 7.5 0 1 1-15 0a7.5 7.5 0 1 1 15 0z', 'M12 13l3.5-3.5', 'M10 3.5h4']
}

function iconTexture(name) {
  return canvasTexture(256, (g, s) => {
    g.translate(s * 0.14, s * 0.14)
    g.scale((s * 0.72) / 24, (s * 0.72) / 24)
    g.lineCap = 'round'
    g.lineJoin = 'round'
    g.shadowColor = '#38e8ff'
    g.shadowBlur = 6
    g.strokeStyle = '#d8fff5'
    g.lineWidth = 1.6
    for (const d of ICONS[name]) g.stroke(new Path2D(d))
  })
}

// ---------------- 部件 ----------------

/** 悬浮方形平台：发光边框的方板 + 顶面网格 + 底部方框 + 悬浮的图标方块 */
function squarePlatform(pos, icon, phaseOffset) {
  const group = new THREE.Group()
  group.position.copy(pos)
  const size = 2.3

  const slab = new THREE.Mesh(new THREE.BoxGeometry(size, 0.28, size), glow(TEAL, 0.07))
  slab.position.y = 0.14
  group.add(slab)
  const edges = new THREE.LineSegments(new THREE.EdgesGeometry(slab.geometry), lineGlow(CYAN, 0.9))
  edges.position.copy(slab.position)
  group.add(edges)

  const top = new THREE.Mesh(new THREE.PlaneGeometry(size * 0.9, size * 0.9),
    new THREE.MeshBasicMaterial({ map: gridTexture, transparent: true, opacity: 0.55, blending: THREE.AdditiveBlending, depthWrite: false }))
  top.rotation.x = -Math.PI / 2
  top.position.y = 0.29
  group.add(top)

  // 底部一圈稍大的方框，像截图里平台下的光边
  const frame = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(size * 1.22, 0.02, size * 1.22)), lineGlow(ACCENT, 0.7))
  group.add(frame)
  const halo = new THREE.Mesh(new THREE.PlaneGeometry(size * 1.5, size * 1.5),
    new THREE.MeshBasicMaterial({ map: haloTexture, transparent: true, opacity: 0.25, blending: THREE.AdditiveBlending, depthWrite: false }))
  halo.rotation.x = -Math.PI / 2
  halo.position.y = 0.01
  group.add(halo)

  // 悬浮的图标方块
  const cube = new THREE.Group()
  const boxGeo = new THREE.BoxGeometry(1.25, 0.9, 1.25)
  cube.add(new THREE.Mesh(boxGeo, glow(CYAN, 0.05)))
  cube.add(new THREE.LineSegments(new THREE.EdgesGeometry(boxGeo), lineGlow(CYAN, 0.85)))
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({
    map: iconTexture(icon), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false
  }))
  sprite.scale.set(1.0, 1.0, 1)
  sprite.position.y = 0.05
  cube.add(sprite)
  group.add(cube)

  updaters.push((t) => {
    const a = TAU * (t / LOOP) + phaseOffset
    cube.position.y = 1.15 + Math.sin(a) * 0.12
    cube.rotation.y = Math.sin(a) * 0.12
    frame.material.opacity = 0.55 + 0.25 * Math.sin(a * 2)
  })
  scene.add(group)
  return group
}

const haloTexture = canvasTexture(256, (g, s) => {
  const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2)
  r.addColorStop(0, 'rgba(25, 230, 176, 0.55)')
  r.addColorStop(0.5, 'rgba(20, 184, 166, 0.18)')
  r.addColorStop(1, 'rgba(0, 0, 0, 0)')
  g.fillStyle = r
  g.fillRect(0, 0, s, s)
})

/** 发光圆形平台：几层圆盘 + 刻度环 + 旋转的虚线环 + 向外扩散的波纹 */
function roundPlatform(pos, radius) {
  const group = new THREE.Group()
  group.position.copy(pos)

  const disc = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius * 1.04, 0.3, 96, 1, true), glow(TEAL, 0.1))
  disc.position.y = 0.15
  group.add(disc)
  const topDisc = new THREE.Mesh(new THREE.CircleGeometry(radius, 96), glow(TEAL, 0.05))
  topDisc.rotation.x = -Math.PI / 2
  topDisc.position.y = 0.3
  group.add(topDisc)

  const ring = (r, w, color, opacity, y) => {
    const m = new THREE.Mesh(new THREE.RingGeometry(r - w, r, 128), glow(color, opacity))
    m.rotation.x = -Math.PI / 2
    m.position.y = y
    group.add(m)
    return m
  }
  ring(radius, 0.06, CYAN, 1, 0.31)
  ring(radius * 0.72, 0.04, ACCENT, 0.9, 0.31)
  ring(radius * 1.25, 0.05, ACCENT, 0.6, 0.02)

  // 虚线环：8 段，每个周期转 1/8 圈正好接上
  const dashes = new THREE.Group()
  for (let i = 0; i < 8; i++) {
    const seg = new THREE.Mesh(new THREE.RingGeometry(radius * 1.1, radius * 1.15, 32, 1, (i / 8) * TAU, TAU / 16), glow(CYAN, 0.9))
    seg.rotation.x = -Math.PI / 2
    dashes.add(seg)
  }
  dashes.position.y = 0.05
  group.add(dashes)

  // 蓝色弧段，反向转
  const arcs = new THREE.Group()
  for (let i = 0; i < 3; i++) {
    const a = new THREE.Mesh(new THREE.RingGeometry(radius * 1.32, radius * 1.36, 64, 1, (i / 3) * TAU, TAU / 7), glow(BLUE, 1))
    a.rotation.x = -Math.PI / 2
    arcs.add(a)
  }
  arcs.position.y = 0.02
  group.add(arcs)

  // 向外扩散的波纹（三道，错开相位）
  const ripples = [0, 1, 2].map(() => ring(radius, 0.03, ACCENT, 0.6, 0.32))

  const halo = new THREE.Mesh(new THREE.PlaneGeometry(radius * 2.8, radius * 2.8),
    new THREE.MeshBasicMaterial({ map: haloTexture, transparent: true, opacity: 0.3, blending: THREE.AdditiveBlending, depthWrite: false }))
  halo.rotation.x = -Math.PI / 2
  halo.position.y = 0.0
  group.add(halo)

  updaters.push((t) => {
    const p = t / LOOP
    dashes.rotation.y = p * (TAU / 8)
    arcs.rotation.y = -p * (TAU / 3)
    ripples.forEach((r, k) => {
      const f = (p * 2 + k / 3) % 1
      const s = 0.4 + f * 0.75
      r.scale.set(s, s, 1)
      r.material.opacity = 0.7 * (1 - f)
    })
  })
  scene.add(group)
  return group
}

/** 上升的光点：位置按周期取模，循环无缝 */
function particles(center, radius, height, count) {
  const pos = new Float32Array(count * 3)
  const seeds = []
  let s = 12345
  const rnd = () => ((s = (s * 16807) % 2147483647) / 2147483647)
  for (let i = 0; i < count; i++) {
    const a = rnd() * TAU
    const r = Math.sqrt(rnd()) * radius
    seeds.push({ x: Math.cos(a) * r, z: Math.sin(a) * r, y0: rnd(), speed: 1 + Math.floor(rnd() * 2) })
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  const pts = new THREE.Points(geo, new THREE.PointsMaterial({
    color: CYAN, size: Math.max(2, W / 700), sizeAttenuation: false, transparent: true, opacity: 0.85,
    blending: THREE.AdditiveBlending, depthWrite: false
  }))
  pts.position.copy(center)
  scene.add(pts)
  updaters.push((t) => {
    seeds.forEach((sd, i) => {
      const y = ((sd.y0 + (t / LOOP) * sd.speed) % 1) * height
      pos[i * 3] = sd.x
      pos[i * 3 + 1] = y
      pos[i * 3 + 2] = sd.z
    })
    geo.attributes.position.needsUpdate = true
  })
}

/** 竖直的光柱（平台上方的淡光），用渐变贴图 */
function beam(center, radius, height) {
  const tex = canvasTexture(128, (g, s) => {
    const l = g.createLinearGradient(0, s, 0, 0)
    l.addColorStop(0, 'rgba(25, 230, 176, 0.5)')
    l.addColorStop(1, 'rgba(25, 230, 176, 0)')
    g.fillStyle = l
    g.fillRect(0, 0, s, s)
  })
  const m = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, height, 64, 1, true),
    new THREE.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.14, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }))
  m.position.copy(center).add(new THREE.Vector3(0, height / 2, 0))
  scene.add(m)
}

/** 全息人体：读 .glb，统一缩放到 height，脚踩在 base 上；所有网格换成全息材质 */
async function hologramBody(base, height, swayDeg) {
  const gltf = await new GLTFLoader().loadAsync(MODEL)
  const root = gltf.scene
  root.updateMatrixWorld(true)
  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  const scale = height / size.y
  root.scale.setScalar(scale)
  root.updateMatrixWorld(true)
  const box2 = new THREE.Box3().setFromObject(root)
  const c = box2.getCenter(new THREE.Vector3())
  root.position.set(-c.x, -box2.min.y, -c.z)

  const holder = new THREE.Group()
  holder.position.copy(base)
  holder.add(root)
  scene.add(holder)

  const minY = base.y
  const maxY = base.y + height
  const mats = []
  root.traverse((o) => {
    if (o.isMesh || o.isSkinnedMesh) {
      const m = hologramMaterial(CYAN, minY, maxY)
      o.material = m
      mats.push(m)
    } else if (o.isLine || o.isLineSegments) {
      o.material = lineGlow(CYAN, 0.6)
    } else if (o.isPoints) {
      o.material = new THREE.PointsMaterial({ color: CYAN, size: 2, sizeAttenuation: false, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false })
    }
  })
  updaters.push((t) => {
    const p = t / LOOP
    holder.rotation.y = Math.sin(p * TAU) * THREE.MathUtils.degToRad(swayDeg)
    for (const m of mats) {
      m.uniforms.phase.value = p * 12
      // 扫描带从脚下外面扫到头顶外面：开头和结尾都看不见，循环接得上
      m.uniforms.scanY.value = minY + (p * 1.2 - 0.1) * (maxY - minY)
    }
  })
  return { holder, minY, maxY }
}

/** 画面上的像素 (u, v) 落在地面 y = 0 的哪个点（用来把平台放到页面文字对应的位置） */
function groundAt(u, v) {
  const ndc = new THREE.Vector2((u / W) * 2 - 1, -(v / H) * 2 + 1)
  const ray = new THREE.Raycaster()
  ray.setFromCamera(ndc, camera)
  const hit = new THREE.Vector3()
  ray.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 1, 0), 0), hit)
  return hit
}

// ---------------- 两个场景 ----------------

async function buildDashboard() {
  camera.fov = 26
  camera.aspect = W / H
  camera.position.set(0, 15, 19)
  camera.lookAt(0, 0, 0)
  camera.updateProjectionMatrix()
  camera.updateMatrixWorld()

  // 主区域 1352×566 里的坐标（DisplayStage.vue 的文字位置）换算到画面像素
  const sx = W / 1352
  const sy = H / 566
  const at = (x, y) => groundAt(x * sx, y * sy)
  squarePlatform(at(336, 262), 'heart', 0)
  squarePlatform(at(1028, 262), 'drop', TAU / 4)
  squarePlatform(at(484, 492), 'thermometer', TAU / 2)
  squarePlatform(at(824, 492), 'pressure', (3 * TAU) / 4)

  const centre = at(676, 330)
  roundPlatform(centre, 2.2)
  beam(centre, 2.0, 5.5)
  particles(centre, 2.6, 6, 220)
  await hologramBody(centre.clone().setY(0.3), 5.4, 20)
}

async function buildPortal() {
  camera.fov = 30
  camera.aspect = W / H
  camera.position.set(0, 6.5, 17.5)
  camera.lookAt(0, 2.3, 0)
  camera.updateProjectionMatrix()

  const centre = new THREE.Vector3(0, 0, 0)
  roundPlatform(centre, 2.4)
  beam(centre, 2.1, 6)
  particles(centre, 3, 7, 260)
  const body = await hologramBody(centre.clone().setY(0.3), 5.0, 25)

  // 绕着人体转的几道倾斜光环
  const rings = []
  ;[
    { r: 2.7, y: 1.5, tilt: 0.2, color: CYAN, turns: 1 },
    { r: 3.0, y: 3.0, tilt: -0.16, color: ACCENT, turns: -1 },
    { r: 2.3, y: 4.4, tilt: 0.1, color: BLUE, turns: 2 }
  ].forEach((cfg) => {
    const g = new THREE.Group()
    const ringMesh = new THREE.Mesh(new THREE.TorusGeometry(cfg.r, 0.014, 8, 160), glow(cfg.color, 0.55))
    ringMesh.rotation.x = Math.PI / 2
    g.add(ringMesh)
    // 环上的亮点
    const dot = new THREE.Mesh(new THREE.SphereGeometry(0.07, 12, 12), glow(new THREE.Color('#ffffff'), 1))
    dot.position.set(cfg.r, 0, 0)
    g.add(dot)
    g.position.y = cfg.y
    g.rotation.z = cfg.tilt
    scene.add(g)
    rings.push({ g, cfg })
  })
  updaters.push((t) => {
    for (const { g, cfg } of rings) g.rotation.y = (t / LOOP) * TAU * cfg.turns
  })
  return body
}

async function buildPreview() {
  camera.position.set(0, 2.5, 9)
  camera.lookAt(0, 2.3, 0)
  camera.updateProjectionMatrix()
  await hologramBody(new THREE.Vector3(0, 0, 0), 4.6, 0)
}

// ---------------- 合成与导出 ----------------

const composer = new EffectComposer(renderer)
composer.setPixelRatio(1)
composer.setSize(W, H)
composer.addPass(new RenderPass(scene, camera))
composer.addPass(new UnrealBloomPass(new THREE.Vector2(W, H), 0.55, 0.4, 0.35))
composer.addPass(new OutputPass())

function frame(t) {
  for (const u of updaters) u(t)
  composer.render()
}

window.heroLoop = LOOP
window.heroReady = (async () => {
  if (SCENE === 'portal') await buildPortal()
  else if (SCENE === 'preview') await buildPreview()
  else await buildDashboard()
  frame(0)
  return true
})()
window.heroFrame = frame
