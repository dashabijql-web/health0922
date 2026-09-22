<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { GLTF } from 'three/examples/jsm/loaders/GLTFLoader.js'

interface Vitals {
  heartRate?: number | string | null
  bloodOxygen?: number | string | null
  bloodPressure?: string | null
  temperature?: number | string | null
  stress?: number | string | null
}

const props = withDefaults(defineProps<{
  personName?: string
  vitals?: Vitals
  freshnessStatus?: string
  lastCollected?: string
  reducedMotion?: boolean
  autoRotate?: boolean
  variant?: 'card' | 'immersive'
}>(), {
  personName: '',
  vitals: () => ({}),
  freshnessStatus: 'no_data',
  lastCollected: '',
  reducedMotion: false,
  autoRotate: true,
  variant: 'card',
})



const canvasHost = ref<HTMLElement | null>(null)
const modelLoading = ref(true)
const sceneError = ref('')

let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let renderer: THREE.WebGLRenderer | undefined
let modelPivot: THREE.Group | undefined
let particleField: THREE.Points | undefined
let scanLine: THREE.Mesh | undefined
let resizeObserver: ResizeObserver | undefined
let animationFrame = 0
let dragging = false
let lastPointerX = 0
let particleVelocity = new Float32Array(0)
const orbitRings: THREE.Mesh[] = []

const statusTone = computed(() => {
  if (props.freshnessStatus === 'fresh') return 'normal'
  if (props.freshnessStatus === 'stale') return 'warning'
  if (props.freshnessStatus === 'offline') return 'offline'
  return 'unknown'
})

const statusLabel = computed(() => ({
  fresh: '数据新鲜',
  stale: '数据陈旧',
  offline: '设备离线',
  no_data: '暂无有效数据',
}[props.freshnessStatus] || '暂无有效数据'))

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '--'
  return value
}

function hasValue(value: unknown) {
  return value !== null && value !== undefined && value !== '' && value !== '--'
}

function initScene() {
  if (!canvasHost.value) return
  try {
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100)
    camera.position.set(0, 1.48, 6)
    camera.lookAt(0, 1.42, 0)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch {
    modelLoading.value = false
    sceneError.value = '当前浏览器未提供可用的 WebGL。'
    return
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6))
  renderer.setClearColor(0x000000, 0)
  canvasHost.value.appendChild(renderer.domElement)

  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  canvas.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('pointercancel', onPointerUp)

  scene.add(new THREE.HemisphereLight(0x9bdcff, 0x061120, 1.6))
  const rim = new THREE.PointLight(0x35c7ff, 3.2, 8)
  rim.position.set(2.2, 3.1, 2.5)
  scene.add(rim)
  const fill = new THREE.PointLight(0x2655c8, 2, 7)
  fill.position.set(-2.5, 1.2, -2)
  scene.add(fill)

  addPlatform()
  addOrbitRings()
  addParticleField()
  addScanLine()
  loadModel()

  resizeObserver = new ResizeObserver(resizeScene)
  resizeObserver.observe(canvasHost.value)
  resizeScene()
  animate()
}

function addPlatform() {
  if (!scene) return
  const isImm = props.variant === 'immersive'
  const group = new THREE.Group()
  group.position.y = isImm ? -0.01 : 0.03
  scene.add(group)
  group.add(new THREE.Mesh(
    new THREE.CylinderGeometry(isImm ? 2.05 : 1.62, isImm ? 2.22 : 1.76, 0.12, 96),
    new THREE.MeshStandardMaterial({ color: 0x071728, metalness: 0.8, roughness: 0.32, emissive: 0x061a31, emissiveIntensity: 0.6 }),
  ))
  const radii = isImm ? [1.42, 1.05, 0.68] : [1.05, 0.78, 0.52]
  radii.forEach((radius, index) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(radius, 0.018, 8, 96),
      new THREE.MeshBasicMaterial({ color: index === 1 ? 0x58d7f4 : 0x1674a8, transparent: true, opacity: index === 1 ? 0.82 : 0.58 }),
    )
    ring.rotation.x = Math.PI / 2
    ring.position.y = 0.085 + index * 0.018
    group.add(ring)
  })
}

function addOrbitRings() {
  if (!scene) return
  const isImm = props.variant === 'immersive'
  const ringDefs = isImm
    ? [
        { radius: 1.62, y: 1.25, tilt: 0.08, color: 0x1c94c9, opacity: 0.44 },
        { radius: 1.32, y: 1.65, tilt: -0.28, color: 0x4bd7ef, opacity: 0.32 },
        { radius: 1.05, y: 0.85, tilt: 0.24, color: 0x55d6b7, opacity: 0.28 },
      ]
    : [
        { radius: 1.32, y: 1.52, tilt: 0.08, color: 0x1c94c9, opacity: 0.42 },
        { radius: 1.08, y: 2.12, tilt: -0.28, color: 0x4bd7ef, opacity: 0.28 },
        { radius: 0.88, y: 1.04, tilt: 0.24, color: 0x55d6b7, opacity: 0.24 },
      ]
  ringDefs.forEach((item) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(item.radius, isImm ? 0.014 : 0.012, 8, 96),
      new THREE.MeshBasicMaterial({ color: item.color, transparent: true, opacity: item.opacity }),
    )
    ring.rotation.x = Math.PI / 2 + item.tilt
    ring.position.y = item.y
    scene!.add(ring)
    orbitRings.push(ring)
  })
}

function addParticleField() {
  if (!scene) return
  const isImm = props.variant === 'immersive'
  const count = isImm ? 360 : 150
  const positions = new Float32Array(count * 3)
  const colors = new Float32Array(count * 3)
  particleVelocity = new Float32Array(count)
  const palette = [new THREE.Color(0x39d7ff), new THREE.Color(0x63a5ff), new THREE.Color(0x6de3c1)]
  for (let i = 0; i < count; i += 1) {
    if (isImm) {
      // 沉浸模式：粒子覆盖全屏视野，包括左卡背后 (X: -3.6 ~ -1.5) 与底部走势条背后 (Y: -0.2 ~ 0.5)
      positions[i * 3] = (Math.random() - 0.5) * 7.4
      positions[i * 3 + 1] = -0.2 + Math.random() * 3.8
      positions[i * 3 + 2] = (Math.random() - 0.5) * 3.2
      particleVelocity[i] = 0.035 + Math.random() * 0.08
    } else {
      const angle = Math.random() * Math.PI * 2
      const radius = 1.05 + Math.random() * 1.45
      positions[i * 3] = Math.cos(angle) * radius
      positions[i * 3 + 1] = 0.18 + Math.random() * 3.35
      positions[i * 3 + 2] = Math.sin(angle) * radius * 0.52
      particleVelocity[i] = 0.035 + Math.random() * 0.07
    }
    const color = palette[i % palette.length]
    colors[i * 3] = color.r
    colors[i * 3 + 1] = color.g
    colors[i * 3 + 2] = color.b
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  particleField = new THREE.Points(geometry, new THREE.PointsMaterial({
    size: isImm ? 0.048 : 0.042,
    vertexColors: true,
    transparent: true,
    opacity: isImm ? 0.88 : 0.76,
    depthWrite: false,
  }))
  scene.add(particleField)
}

function addScanLine() {
  if (!scene) return
  const isImm = props.variant === 'immersive'
  const width = isImm ? 8.6 : 2.8
  scanLine = new THREE.Mesh(
    new THREE.BoxGeometry(width, isImm ? 0.012 : 0.009, 0.012),
    new THREE.MeshBasicMaterial({ color: 0x42d9ff, transparent: true, opacity: isImm ? 0.42 : 0.34 }),
  )
  scanLine.position.set(0, isImm ? 0.1 : 0.62, 0.35)
  scene.add(scanLine)
}

function loadModel() {
  if (!scene) return
  new GLTFLoader().load('/models/wireframe_man.glb', (gltf: GLTF) => {
    const model = gltf.scene
    const box = new THREE.Box3().setFromObject(model)
    const height = box.max.y - box.min.y || 1
    const isImm = props.variant === 'immersive'
    const targetScale = isImm ? 2.22 : 2.48
    model.scale.setScalar(targetScale / height)
    const after = new THREE.Box3().setFromObject(model)
    model.position.set(-((after.min.x + after.max.x) / 2), (isImm ? 0.06 : 0.18) - after.min.y, -((after.min.z + after.max.z) / 2))
    model.traverse((child: THREE.Object3D) => {
      if (!(child as THREE.Mesh).isMesh) return
      const mesh = child as THREE.Mesh
      mesh.material = new THREE.MeshStandardMaterial({
        color: 0x35d9ff,
        emissive: 0x0c9fc5,
        emissiveIntensity: 1.42,
        metalness: 0.2,
        roughness: 0.42,
        transparent: true,
        opacity: 0.93,
      })
    })
    modelPivot = new THREE.Group()
    modelPivot.add(model)
    scene?.add(modelPivot)
    modelLoading.value = false
  }, undefined, () => {
    modelLoading.value = false
    sceneError.value = '人体模型资源加载失败，请检查静态资源服务。'
  })
}

function onPointerDown(event: PointerEvent) {
  if (!renderer) return
  dragging = true
  lastPointerX = event.clientX
  renderer.domElement.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging || !modelPivot) return
  modelPivot.rotation.y += (event.clientX - lastPointerX) * 0.008
  lastPointerX = event.clientX
}

function onPointerUp(event: PointerEvent) {
  dragging = false
  if (event.pointerId !== undefined && renderer?.domElement.hasPointerCapture(event.pointerId)) {
    renderer.domElement.releasePointerCapture(event.pointerId)
  }
}

function animate() {
  animationFrame = requestAnimationFrame(animate)
  const time = performance.now() * 0.001
  const quiet = props.reducedMotion
  const isImm = props.variant === 'immersive'
  if (modelPivot && props.autoRotate && !quiet && !dragging) modelPivot.rotation.y += 0.0032
  if (particleField) {
    const positions = particleField.geometry.attributes.position.array as Float32Array
    const resetY = isImm ? -0.2 : 0.18
    const maxY = isImm ? 3.6 : 3.7
    for (let i = 0; i < particleVelocity.length; i += 1) {
      const yIndex = i * 3 + 1
      positions[yIndex] += (quiet ? particleVelocity[i] * 0.12 : particleVelocity[i]) * 0.016
      if (positions[yIndex] > maxY) positions[yIndex] = resetY
    }
    particleField.geometry.attributes.position.needsUpdate = true
    particleField.rotation.y = quiet ? 0 : time * (isImm ? 0.009 : 0.018)
  }
  if (scanLine) {
    const minY = isImm ? -0.15 : 0.5
    const sweepRange = isImm ? 3.35 : 2.65
    scanLine.position.y = minY + ((time * (quiet ? 0.03 : 0.11)) % sweepRange)
    const material = scanLine.material as THREE.MeshBasicMaterial
    material.opacity = quiet ? 0.16 : (isImm ? 0.40 : 0.28) + Math.sin(time * 0.55) * 0.06
  }
  orbitRings.forEach((ring, index) => {
    ring.rotation.z += (quiet ? 0.0006 : 0.0022) * (index % 2 ? -1 : 1)
  })
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function resizeScene() {
  if (!renderer || !camera || !canvasHost.value) return
  const width = canvasHost.value.clientWidth
  const height = Math.max(canvasHost.value.clientHeight, 1)
  const aspect = width / height
  const fov = THREE.MathUtils.degToRad(camera.fov * 0.5)
  const verticalDistance = 3.42 / (2 * Math.tan(fov))
  const horizontalDistance = 3.82 / (2 * Math.tan(fov) * Math.max(aspect, 0.1))
  camera.aspect = aspect

  if (props.variant === 'immersive') {
    // 沉浸模式：全身完整入画（头、手、脚均清晰完整呈现，头顶绝不裁切）
    // 头顶到顶栏留出安全留白，脚部到底部走势条留出呼吸空间
    // 垂直视锥高 2.85，中心 y: 1.18，让 2.22 高度人体与旋转底盘完整优雅呈现
    const verticalDistance = 2.85 / (2 * Math.tan(fov))
    camera.position.set(0.12, 1.18, verticalDistance)
    camera.lookAt(0.12, 1.18, 0)
  } else {
    const verticalDistance = 3.42 / (2 * Math.tan(fov))
    const horizontalDistance = 3.82 / (2 * Math.tan(fov) * Math.max(aspect, 0.1))
    camera.position.set(0, 1.48, Math.max(verticalDistance, horizontalDistance))
    camera.lookAt(0, 1.42, 0)
  }

  camera.updateProjectionMatrix()
  renderer.setSize(width, height, true)
}

function disposeObject(object?: THREE.Object3D) {
  object?.traverse((child: THREE.Object3D) => {
    const mesh = child as THREE.Mesh
    mesh.geometry?.dispose?.()
    const material = mesh.material
    if (Array.isArray(material)) material.forEach((item) => item.dispose?.())
    else material?.dispose?.()
  })
}

function resetView() {
  if (modelPivot) modelPivot.rotation.set(0, 0, 0)
}

defineExpose({ resetView })

onMounted(async () => {
  await nextTick()
  initScene()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  renderer?.domElement.removeEventListener('pointerdown', onPointerDown)
  renderer?.domElement.removeEventListener('pointermove', onPointerMove)
  renderer?.domElement.removeEventListener('pointerup', onPointerUp)
  renderer?.domElement.removeEventListener('pointercancel', onPointerUp)
  disposeObject(scene)
  renderer?.dispose()
})
</script>

<template>
  <section :class="['portrait-hologram', `is-${variant}`]" aria-label="三维人体健康信号展示">
    <div class="hologram-grid"></div>
    <div class="hologram-vignette"></div>
    <!-- 四角精密标尺微标记 -->
    <template v-if="variant !== 'immersive'">
      <div class="hologram-corner hologram-corner--tl"></div>
      <div class="hologram-corner hologram-corner--tr"></div>
      <div class="hologram-corner hologram-corner--bl"></div>
      <div class="hologram-corner hologram-corner--br"></div>
    </template>

    <header v-if="variant !== 'immersive'" class="hologram-header">
      <div class="hologram-person">
        <span class="hologram-eyebrow">3D BIOMETRIC HOLOGRAM</span>
        <div class="hologram-person-name">
          <span :class="['hologram-status-dot', `is-${statusTone}`]"></span>
          <strong>{{ personName || '未选择人员' }}</strong>
        </div>
      </div>
      <div class="hologram-sys-badge">
        <span class="hud-pill">360° SPATIAL HUD</span>
      </div>
    </header>

    <div ref="canvasHost" class="hologram-canvas">
      <div v-if="sceneError" class="hologram-error" role="status">
        <strong>三维人体视图暂不可用</strong>
        <span>{{ sceneError }}</span>
      </div>
      <div v-else-if="modelLoading" class="hologram-loading" role="status">正在加载三维人体模型…</div>
    </div>

    <!-- 工业精密仪表体征读数牌（环绕人体布局，高对比度等宽数字） -->
    <!-- 1. 心率 (左胸) -->
    <div class="hologram-label hologram-label--heart" :class="{ 'has-val': hasValue(vitals?.heartRate) }">
      <span class="hologram-dot hologram-dot--heart"></span>
      <div class="hologram-label__metric">
        <b>心率 <span>HEART RATE</span></b>
        <span class="hologram-label__val-group">
          <strong class="val-heart">{{ formatValue(vitals?.heartRate) }}</strong>
          <small>BPM</small>
        </span>
      </div>
    </div>

    <!-- 2. 血压 (左臂/下侧) -->
    <div class="hologram-label hologram-label--bp" :class="{ 'has-val': hasValue(vitals?.bloodPressure) }">
      <span class="hologram-dot hologram-dot--bp"></span>
      <div class="hologram-label__metric">
        <b v-if="variant === 'immersive'">收缩压/舒张压</b>
        <b v-else>血压 <span>BLOOD PRESSURE</span></b>
        <span class="hologram-label__val-group">
          <strong class="val-bp">{{ formatValue(vitals?.bloodPressure) }}</strong>
          <small>mmHg</small>
        </span>
      </div>
    </div>

    <!-- 3. 血氧 (右上/肺部) -->
    <div class="hologram-label hologram-label--oxygen" :class="{ 'has-val': hasValue(vitals?.bloodOxygen) }">
      <span class="hologram-dot hologram-dot--oxygen"></span>
      <div class="hologram-label__metric">
        <b>血氧 <span>SPO2</span></b>
        <span class="hologram-label__val-group">
          <strong class="val-oxygen">{{ formatValue(vitals?.bloodOxygen) }}</strong>
          <small>%</small>
        </span>
      </div>
    </div>

    <!-- 4. 体温 (右中/核心) -->
    <div class="hologram-label hologram-label--temp" :class="{ 'has-val': hasValue(vitals?.temperature) }">
      <span class="hologram-dot hologram-dot--temp"></span>
      <div class="hologram-label__metric">
        <b>体温 <span>BODY TEMP</span></b>
        <span class="hologram-label__val-group">
          <strong class="val-temp">{{ formatValue(vitals?.temperature) }}</strong>
          <small>°C</small>
        </span>
      </div>
    </div>

    <!-- 5. 压力 (右下/负荷) -->
    <div class="hologram-label hologram-label--stress" :class="{ 'has-val': hasValue(vitals?.stress) }">
      <span class="hologram-dot hologram-dot--stress"></span>
      <div class="hologram-label__metric">
        <b>压力 <span v-if="variant !== 'immersive'">STRESS INDEX</span></b>
        <span class="hologram-label__val-group">
          <strong class="val-stress">{{ formatValue(vitals?.stress) }}</strong>
          <small v-if="variant !== 'immersive'">LV</small>
        </span>
      </div>
    </div>

    <footer v-if="variant !== 'immersive'" class="hologram-footer">
      <div class="hologram-footer__info">
        <span class="hologram-footer__time">采集时间: {{ lastCollected || '暂无记录' }}</span>
        <span :class="['hologram-status', `is-${statusTone}`]">{{ statusLabel }}</span>
      </div>
      <div class="hologram-footer__hint">按住鼠标左键可 360° 旋转人体视角</div>
    </footer>
  </section>
</template>

<style scoped>
.portrait-hologram {
  position: relative;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--border-dim);
  border-radius: var(--radius-md);
  background: var(--bg-canvas);
  box-shadow: inset 0 0 60px rgba(0, 0, 0, 0.7), 0 8px 32px rgba(0, 0, 0, 0.4);
}

.portrait-hologram.is-immersive {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}

.portrait-hologram.is-immersive .hologram-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.portrait-hologram.is-immersive .hologram-vignette {
  display: none;
}

.portrait-hologram.is-immersive .hologram-grid {
  mask-image: none;
  opacity: 0.12;
}

.portrait-hologram.is-immersive .hologram-label {
  background: rgba(8, 12, 18, 0.24);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
}

.portrait-hologram.is-immersive .hologram-label--heart {
  left: 20%;
  top: 24%;
}

.portrait-hologram.is-immersive .hologram-label--bp {
  left: 20%;
  top: 56%;
}

.portrait-hologram.is-immersive .hologram-label--oxygen {
  right: 8%;
  top: 20%;
}

.portrait-hologram.is-immersive .hologram-label--temp {
  right: 8%;
  top: 45%;
}

.portrait-hologram.is-immersive .hologram-label--stress {
  right: 8%;
  top: 70%;
}

@media (max-width: 860px) {
  .portrait-hologram.is-immersive .hologram-label {
    display: none;
  }
}

.hologram-grid {
  position: absolute;
  inset: 0;
  opacity: 0.28;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(circle at 50% 50%, rgba(0, 0, 0, 0.9) 30%, transparent 85%);
}

.hologram-vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  box-shadow: inset 0 0 100px rgba(0, 0, 0, 0.65);
}

/* 四角工程标尺装饰 */
.hologram-corner {
  position: absolute;
  width: 10px;
  height: 10px;
  border-color: rgba(255, 255, 255, 0.16);
  pointer-events: none;
  z-index: 2;
}

.hologram-corner--tl {
  top: 6px;
  left: 6px;
  border-top: 1px solid;
  border-left: 1px solid;
}

.hologram-corner--tr {
  top: 6px;
  right: 6px;
  border-top: 1px solid;
  border-right: 1px solid;
}

.hologram-corner--bl {
  bottom: 34px;
  left: 6px;
  border-bottom: 1px solid;
  border-left: 1px solid;
}

.hologram-corner--br {
  bottom: 34px;
  right: 6px;
  border-bottom: 1px solid;
  border-right: 1px solid;
}

.hologram-header {
  position: absolute;
  z-index: 3;
  top: 14px;
  left: 16px;
  right: 16px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.hologram-person {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hologram-eyebrow {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.hologram-person-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hologram-person-name strong {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.hologram-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-standby);
}

.hologram-status-dot.is-normal {
  background: var(--status-normal);
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.6);
}

.hologram-status-dot.is-warning {
  background: var(--status-warning);
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.6);
}

.hologram-status-dot.is-offline {
  background: var(--status-standby);
}

.hud-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border: 1px solid rgba(56, 189, 248, 0.24);
  border-radius: var(--radius-sm);
  background: rgba(56, 189, 248, 0.06);
  color: var(--signal-cyan);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.hologram-canvas {
  position: absolute;
  inset: 0 0 34px 0;
}

.hologram-canvas canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.hologram-canvas canvas:active {
  cursor: grabbing;
}

.hologram-loading,
.hologram-error {
  position: absolute;
  inset: 50% auto auto 50%;
  z-index: 4;
  transform: translate(-50%, -50%);
  display: grid;
  gap: 6px;
  width: min(80%, 260px);
  padding: 12px 16px;
  border: 1px solid var(--border-dim);
  border-radius: var(--radius-md);
  background: rgba(13, 17, 23, 0.94);
  backdrop-filter: blur(8px);
  color: var(--text-secondary);
  text-align: center;
  font-size: 12px;
}

.hologram-error strong {
  color: var(--status-warning);
  font-size: 13px;
}

/* 工业精密仪表卡片（深色磨砂、等宽数字、精准状态指示） */
.hologram-label {
  position: absolute;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 12px;
  min-width: 114px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-left: 2px solid rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-sm);
  background: rgba(9, 14, 23, 0.88);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
  pointer-events: none;
  transition: all 0.2s ease;
}

.hologram-label.has-val.hologram-label--heart { border-left-color: var(--gauge-heart, #f87171); }
.hologram-label.has-val.hologram-label--bp { border-left-color: var(--gauge-bp, #38bdf8); }
.hologram-label.has-val.hologram-label--oxygen { border-left-color: var(--gauge-oxygen, #34d399); }
.hologram-label.has-val.hologram-label--temp { border-left-color: var(--gauge-temp, #fbbf24); }
.hologram-label.has-val.hologram-label--stress { border-left-color: var(--gauge-stress, #a78bfa); }

.hologram-label__metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hologram-label b {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.hologram-label b span {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 9px;
  opacity: 0.7;
  font-weight: 500;
}

.hologram-label__val-group {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.hologram-label strong {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 19px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.hologram-label.has-val .val-heart { color: var(--gauge-heart, #f87171); }
.hologram-label.has-val .val-bp { color: var(--gauge-bp, #38bdf8); }
.hologram-label.has-val .val-oxygen { color: var(--gauge-oxygen, #34d399); }
.hologram-label.has-val .val-temp { color: var(--gauge-temp, #fbbf24); }
.hologram-label.has-val .val-stress { color: var(--gauge-stress, #a78bfa); }

.hologram-label small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
}

/* 待机与实时状态圆点 */
.hologram-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--status-standby);
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.has-val .hologram-dot--heart {
  background: var(--gauge-heart, #f87171);
  box-shadow: 0 0 8px rgba(248, 113, 113, 0.6);
}

.has-val .hologram-dot--bp {
  background: var(--gauge-bp, #38bdf8);
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
}

.has-val .hologram-dot--oxygen {
  background: var(--gauge-oxygen, #34d399);
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.6);
}

.has-val .hologram-dot--temp {
  background: var(--gauge-temp, #fbbf24);
  box-shadow: 0 0 8px rgba(251, 191, 36, 0.6);
}

.has-val .hologram-dot--stress {
  background: var(--gauge-stress, #a78bfa);
  box-shadow: 0 0 8px rgba(167, 139, 250, 0.6);
}

/* 桌面端读数牌环绕人体空间定位 */
.hologram-label--heart { top: 22%; left: 8%; }
.hologram-label--bp { top: 52%; left: 8%; }
.hologram-label--oxygen { top: 18%; right: 8%; }
.hologram-label--temp { top: 42%; right: 8%; }
.hologram-label--stress { top: 66%; right: 8%; }

/* 底部状态条：嵌入式底栏，等宽时间，严谨指示 */
.hologram-footer {
  position: absolute;
  z-index: 3;
  left: 0;
  right: 0;
  bottom: 0;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-top: 1px solid var(--border-dim);
  background: rgba(7, 11, 18, 0.9);
  backdrop-filter: blur(8px);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.hologram-footer__info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hologram-footer__time {
  color: var(--text-secondary);
}

.hologram-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
}

.hologram-status::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.hologram-status.is-normal {
  color: var(--status-normal);
}

.hologram-status.is-warning {
  color: var(--status-warning);
}

.hologram-status.is-offline,
.hologram-status.is-unknown {
  color: var(--status-standby);
}

.hologram-footer__hint {
  color: var(--text-muted);
  font-size: 11px;
}

@media (max-width: 860px) {
  .hologram-header {
    top: 8px;
    left: 8px;
    right: 8px;
  }

  .hologram-eyebrow {
    display: none;
  }

  .hologram-label {
    padding: 3px 7px;
    min-width: unset;
    gap: 6px;
  }

  .hologram-label b {
    font-size: 10px;
  }

  .hologram-label b span {
    display: none;
  }

  .hologram-label strong {
    font-size: 14px;
  }

  .hologram-label small {
    font-size: 9px;
  }

  .hologram-label--heart { left: 6px; top: 16%; }
  .hologram-label--bp { left: 6px; top: 52%; }
  .hologram-label--oxygen { right: 6px; top: 16%; }
  .hologram-label--temp { right: 6px; top: 42%; }
  .hologram-label--stress { right: 6px; top: 68%; }

  .hologram-footer {
    padding: 0 8px;
    font-size: 10px;
    height: 30px;
  }

  .hologram-footer__hint {
    display: none;
  }
}
</style>
