<template>
  <div ref="host" class="body-3d" :aria-label="label" role="img" :data-state="state">
    <p v-if="state === 'loading'" class="body-3d__status" role="status">正在加载人体模型……</p>
    <p v-else-if="state === 'error'" class="body-3d__status body-3d__status--error" role="status">{{ errorText }}</p>
  </div>
</template>

<script setup lang="ts">
// 3D 人体（docs/07 公共组件 BodyModel3D）：老项目的全息线框人体 human_body_wireframe.glb 站在发光的圆形平台上，
// 缓慢自转，按住拖动可以转，拖动时暂停自转（docs/07 第一部分"六、动画"）。材质和首页动画（tools/hero-render/scene.js）
// 同一套全息着色：边缘亮、横向细线、一道从下往上的扫描带。
//
// 生命周期：
// - three.js 体积大，只在个人档案页面用到，页面用 defineAsyncComponent 引入本组件，切到页面时才下载；
// - paused 为 true（切到"预警记录"标签）、页面切到后台、系统开启"减少动态效果"时停止逐帧绘制，恢复后接着画；
// - 卸载时停掉动画、移除监听、释放所有几何体/材质/贴图、释放 WebGL 上下文（forceContextLoss）并移除 canvas；
//   模型还没下载完就卸载的，下载完立刻释放，不挂到场景上。反复进出个人档案不会留下 WebGL 上下文和显存。
import { inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { SCREEN_SCALE_KEY } from './screen-scale'

const props = withDefaults(defineProps<{ paused?: boolean; label?: string; modelUrl?: string }>(), {
  paused: false,
  label: '3D 人体模型，可拖动旋转',
  modelUrl: '/models/human_body_wireframe.glb'
})
const emit = defineEmits<{ ready: [] }>()

const host = ref<HTMLElement | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const errorText = ref('')
const screenScale = inject(SCREEN_SCALE_KEY, null)

/** 人体高度（场景单位）；平台在 y = 0，相机按它取景 */
const BODY_HEIGHT = 2
/** 截图 4：人体从主区域顶部往下 30 px 到 504 px（高 574），平台中心在 516 px，平台宽约 490 px（docs/09 第七节） */
const FRAME = { top: 30 / 574, feet: 504 / 574, platformRadiusPx: 245 }
const AUTO_SPIN = 0.18 // 弧度/秒
/** 镜头：视角小（长焦）透视弱，平台不会被拉得太大；略微俯视，平台成扁椭圆（截图里约 490×78） */
const FOV = 16
const TILT_DEG = 5
const TAU = Math.PI * 2

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let holder: THREE.Group | null = null
let platform: THREE.Group | null = null
let observer: ResizeObserver | null = null
let frame = 0
let lastTime = 0
let disposed = false
let dragging = false
let lastX = 0
const holoMaterials: THREE.ShaderMaterial[] = []
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

function cssColor(name: string): THREE.Color {
  const v = getComputedStyle(host.value ?? document.documentElement).getPropertyValue(name).trim()
  return new THREE.Color(v || '#38e8ff')
}

/** 全息材质：边缘亮（菲涅尔）、横向扫描细线、一道从下往上扫的亮带（和 tools/hero-render/scene.js 一样） */
function hologramMaterial(color: THREE.Color): THREE.ShaderMaterial {
  return new THREE.ShaderMaterial({
    uniforms: {
      color: { value: color },
      phase: { value: 0 },
      scanY: { value: 0 },
      bandWidth: { value: BODY_HEIGHT * 0.04 }
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
        vec3 c = color * (0.06 + 0.9 * f) * lines + vec3(0.5, 0.9, 0.85) * scan * 0.35;
        gl_FragColor = vec4(c, 1.0);
      }`,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    side: THREE.DoubleSide
  })
}

function glow(color: THREE.Color, opacity: number): THREE.MeshBasicMaterial {
  return new THREE.MeshBasicMaterial({
    color, transparent: true, opacity, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide
  })
}

/** 发光圆形平台：底盘 + 几道光环 + 中心的光晕 */
function buildPlatform(radius: number): THREE.Group {
  const g = new THREE.Group()
  const teal = cssColor('--body-platform')
  const ring = cssColor('--body-platform-ring')
  const cyan = cssColor('--body-holo')

  const disc = new THREE.Mesh(new THREE.CircleGeometry(radius, 96), glow(teal, 0.12))
  disc.rotation.x = -Math.PI / 2
  g.add(disc)
  const ringAt = (r: number, w: number, color: THREE.Color, opacity: number, y: number) => {
    const m = new THREE.Mesh(new THREE.RingGeometry(r - w, r, 128), glow(color, opacity))
    m.rotation.x = -Math.PI / 2
    m.position.y = y
    g.add(m)
    return m
  }
  ringAt(radius, radius * 0.07, ring, 0.85, 0.004)
  ringAt(radius * 0.78, radius * 0.02, cyan, 0.7, 0.006)
  ringAt(radius * 0.55, radius * 0.05, teal, 0.45, 0.006)
  ringAt(radius * 1.12, radius * 0.015, ring, 0.5, 0.002)

  // 外圈的刻度虚线：24 段，跟着平台慢慢转
  const ticks = new THREE.Group()
  for (let i = 0; i < 24; i++) {
    const seg = new THREE.Mesh(new THREE.RingGeometry(radius * 0.9, radius * 0.95, 8, 1, (i / 24) * TAU, TAU / 60),
      glow(cyan, 0.8))
    seg.rotation.x = -Math.PI / 2
    seg.position.y = 0.008
    ticks.add(seg)
  }
  ticks.name = 'ticks'
  g.add(ticks)
  return g
}

async function loadBody(color: THREE.Color): Promise<THREE.Group | null> {
  const gltf = await new GLTFLoader().loadAsync(props.modelUrl)
  const root = gltf.scene
  if (disposed) {
    disposeTree(root)
    return null
  }
  root.updateMatrixWorld(true)
  const box = new THREE.Box3().setFromObject(root)
  const size = box.getSize(new THREE.Vector3())
  root.scale.setScalar(BODY_HEIGHT / size.y)
  root.updateMatrixWorld(true)
  const box2 = new THREE.Box3().setFromObject(root)
  const c = box2.getCenter(new THREE.Vector3())
  root.position.set(-c.x, -box2.min.y, -c.z)
  root.traverse((o) => {
    const mesh = o as THREE.Mesh
    if (mesh.isMesh) {
      disposeMaterial(mesh.material)
      const m = hologramMaterial(color)
      mesh.material = m
      holoMaterials.push(m)
    }
  })
  const g = new THREE.Group()
  g.add(root)
  return g
}

/** 相机：让人体在画面里的上下位置和截图一样，平台按透视自然变成扁椭圆 */
function placeCamera() {
  if (!camera || !host.value) return
  const w = host.value.clientWidth
  const h = Math.max(1, host.value.clientHeight)
  camera.aspect = w / h
  const fov = THREE.MathUtils.degToRad(camera.fov)
  // 人体占画面高度的比例 → 相机距离
  const frac = FRAME.feet - FRAME.top
  const dist = BODY_HEIGHT / frac / (2 * Math.tan(fov / 2))
  // 画面中心对应的高度：人体中线比画面中线高多少
  const unitsPerFrac = BODY_HEIGHT / frac
  const bodyMidFrac = (FRAME.top + FRAME.feet) / 2
  const centerY = BODY_HEIGHT / 2 - (0.5 - bodyMidFrac) * unitsPerFrac
  const tilt = THREE.MathUtils.degToRad(TILT_DEG)
  camera.position.set(0, centerY + Math.sin(tilt) * dist, Math.cos(tilt) * dist)
  camera.lookAt(0, centerY, 0)
  camera.updateProjectionMatrix()
  if (platform) {
    // 平台宽约 490 px：按画面高度换算成场景单位
    const radius = (FRAME.platformRadiusPx / h) * unitsPerFrac
    platform.scale.setScalar(radius)
  }
  const ratio = Math.min((window.devicePixelRatio || 1) * (screenScale?.value ?? 1), 1.5)
  renderer?.setPixelRatio(ratio)
  renderer?.setSize(w, h, false)
}

function running(): boolean {
  return !disposed && !props.paused && !document.hidden
}

function tick(now: number) {
  frame = 0
  if (!renderer || !scene || !camera || !running()) return
  const dt = lastTime ? Math.min(0.1, (now - lastTime) / 1000) : 0
  lastTime = now
  const t = now / 1000
  const still = reducedMotion.matches
  if (holder && !dragging && !still) holder.rotation.y += AUTO_SPIN * dt
  for (const m of holoMaterials) {
    m.uniforms.phase.value = still ? 0 : t * 1.5
    // 扫描带 6 秒从脚下扫到头顶
    m.uniforms.scanY.value = still ? -1 : (((t / 6) % 1) * 1.2 - 0.1) * BODY_HEIGHT
  }
  const ticks = platform?.getObjectByName('ticks')
  if (ticks && !still) ticks.rotation.y = t * 0.15
  renderer.render(scene, camera)
  if (!still) frame = requestAnimationFrame(tick)
}

/** 开始逐帧绘制（已经在画就不重复）；减少动态效果时只画一帧 */
function start() {
  if (frame || !running()) return
  lastTime = 0
  frame = requestAnimationFrame(tick)
}

function stop() {
  if (frame) cancelAnimationFrame(frame)
  frame = 0
}

function onVisibility() {
  if (document.hidden) stop()
  else start()
}

function onPointerDown(e: PointerEvent) {
  dragging = true
  lastX = e.clientX
  renderer?.domElement.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging || !holder) return
  // 画布被缩放过，按屏幕像素算转角，手感和缩放无关
  holder.rotation.y += (e.clientX - lastX) * 0.01
  lastX = e.clientX
  if (reducedMotion.matches) start()
}

function onPointerUp(e: PointerEvent) {
  dragging = false
  if (renderer?.domElement.hasPointerCapture(e.pointerId)) renderer.domElement.releasePointerCapture(e.pointerId)
}

function disposeMaterial(material: THREE.Material | THREE.Material[]) {
  for (const m of Array.isArray(material) ? material : [material]) {
    for (const value of Object.values(m)) {
      if (value instanceof THREE.Texture) value.dispose()
    }
    m.dispose()
  }
}

function disposeTree(root: THREE.Object3D) {
  root.traverse((o) => {
    const mesh = o as THREE.Mesh
    mesh.geometry?.dispose()
    if (mesh.material) disposeMaterial(mesh.material)
  })
}

onMounted(async () => {
  const el = host.value
  if (!el) return
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' })
  } catch {
    state.value = 'error'
    errorText.value = '浏览器不支持 WebGL，无法显示 3D 人体'
    return
  }
  renderer.setClearColor(0x000000, 0)
  const canvas = renderer.domElement
  canvas.className = 'body-3d__canvas'
  el.appendChild(canvas)
  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  canvas.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('pointercancel', onPointerUp)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(FOV, 1, 0.1, 100)
  platform = buildPlatform(1)
  scene.add(platform)
  placeCamera()

  observer = new ResizeObserver(() => {
    placeCamera()
    if (reducedMotion.matches || props.paused) renderer?.render(scene!, camera!)
  })
  observer.observe(el)
  document.addEventListener('visibilitychange', onVisibility)

  try {
    holder = await loadBody(cssColor('--body-holo'))
  } catch {
    if (!disposed) {
      state.value = 'error'
      errorText.value = '人体模型加载失败'
    }
    return
  }
  if (!holder || disposed || !scene) return
  scene.add(holder)
  state.value = 'ready'
  emit('ready')
  start()
  if (props.paused || reducedMotion.matches) renderer.render(scene, camera)
})

watch(() => props.paused, (p) => (p ? stop() : start()))

onBeforeUnmount(() => {
  disposed = true
  stop()
  observer?.disconnect()
  observer = null
  document.removeEventListener('visibilitychange', onVisibility)
  if (scene) disposeTree(scene)
  holoMaterials.length = 0
  if (renderer) {
    const canvas = renderer.domElement
    canvas.removeEventListener('pointerdown', onPointerDown)
    canvas.removeEventListener('pointermove', onPointerMove)
    canvas.removeEventListener('pointerup', onPointerUp)
    canvas.removeEventListener('pointercancel', onPointerUp)
    renderer.renderLists.dispose()
    renderer.dispose()
    renderer.forceContextLoss()
    canvas.remove()
  }
  renderer = null
  scene = null
  camera = null
  holder = null
  platform = null
})
</script>

<style scoped>
.body-3d {
  position: relative;
  width: 100%;
  height: 100%;
}

.body-3d :deep(.body-3d__canvas) {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
}

.body-3d :deep(.body-3d__canvas:active) {
  cursor: grabbing;
}

.body-3d__status {
  position: absolute;
  left: 50%;
  top: 45%;
  margin: 0;
  transform: translateX(-50%);
  color: var(--text-secondary);
  font-size: 15px;
}

.body-3d__status--error {
  color: var(--warn);
}
</style>
