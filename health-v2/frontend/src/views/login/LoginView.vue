<template>
  <!-- 复制自老项目 HealthShow/src/views/login 后独立修改：配色换成 tokens.css，去掉 Vuex/svg-icon/SCSS，
       去掉"系统在线"状态和随机数据流（看起来像真实状态，但不是后端数据） -->
  <div class="login-container">
    <div class="decor" aria-hidden="true">
      <div class="login-starfield">
        <span
          v-for="(s, i) in stars"
          :key="'s' + i"
          class="login-star"
          :style="{
            left: s.left + '%',
            top: s.top + '%',
            width: s.size + 'px',
            height: s.size + 'px',
            animationDuration: s.duration + 's',
            animationDelay: s.delay + 's'
          }"
        ></span>
      </div>

      <div class="login-bg-glow login-bg-glow--a"></div>
      <div class="login-bg-glow login-bg-glow--b"></div>

      <div class="login-beam login-beam--1"></div>
      <div class="login-beam login-beam--2"></div>

      <div class="login-hologram">
        <span class="login-holo-ring login-holo-ring--1"></span>
        <span class="login-holo-ring login-holo-ring--2"></span>
        <span class="login-holo-ring login-holo-ring--3"></span>
        <span class="login-holo-sweep"></span>
      </div>

      <span
        v-for="(p, i) in particles"
        :key="'p' + i"
        class="login-particle"
        :style="{
          left: p.left + '%',
          top: p.top + '%',
          width: p.size + 'px',
          height: p.size + 'px',
          opacity: p.opacity,
          animationDuration: p.duration + 's',
          animationDelay: p.delay + 's'
        }"
      ></span>

      <span class="login-hud-corner login-hud-corner--tl"></span>
      <span class="login-hud-corner login-hud-corner--tr"></span>
      <span class="login-hud-corner login-hud-corner--bl"></span>
      <span class="login-hud-corner login-hud-corner--br"></span>

      <!-- 底部心跳线：纯装饰，不代表任何人的数据 -->
      <div class="login-ecg">
        <div class="login-ecg-track">
          <svg v-for="n in 2" :key="n" viewBox="0 0 400 60" class="login-ecg-svg" preserveAspectRatio="none">
            <polyline points="0,30 55,30 65,6 75,54 85,20 95,30 195,30 205,6 215,54 225,20 235,30 335,30 345,6 355,54 365,20 375,30 400,30" />
          </svg>
        </div>
      </div>
    </div>

    <div
      ref="loginPanel"
      class="login-panel"
      :style="panelStyle"
      @mousemove="onPanelMouseMove"
      @mouseleave="onPanelMouseLeave"
    >
      <span class="login-panel-spotlight" :style="spotlightStyle"></span>
      <span class="login-panel-corner login-panel-corner--tl"></span>
      <span class="login-panel-corner login-panel-corner--tr"></span>
      <span class="login-panel-corner login-panel-corner--bl"></span>
      <span class="login-panel-corner login-panel-corner--br"></span>

      <div class="login-brand">
        <AppLogo class="login-brand-logo" />
      </div>
      <h1 class="login-brand-title">职工健康管理系统</h1>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <div class="login-field">
            <span class="login-field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4" /><path d="M4 21c1-4.5 4.5-6.5 8-6.5s7 2 8 6.5" /></svg>
            </span>
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              name="username"
              autocomplete="username"
              aria-label="用户名"
            />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="login-field">
            <span class="login-field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
            </span>
            <el-input
              ref="passwordInput"
              v-model="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              name="password"
              autocomplete="current-password"
              aria-label="密码"
              @keyup.enter="handleLogin"
            />
            <button
              type="button"
              class="login-field-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="togglePassword"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
                <circle cx="12" cy="12" r="3" />
                <path v-if="!showPassword" d="M4 4l16 16" />
              </svg>
            </button>
          </div>
        </el-form-item>

        <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>

        <el-button :loading="loading" class="login-submit" type="primary" native-type="submit">
          登 录
        </el-button>

        <p v-if="hint" class="login-tips" data-testid="login-hint">{{ hint }}</p>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import { fetchLoginHint } from '@/api/auth'
import { ApiError } from '@/api/request'
import { safeRedirect } from '@/router'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'LoginView' })

interface LoginForm {
  username: string
  password: string
}

function createStars(count: number) {
  return Array.from({ length: count }, () => ({
    left: Math.random() * 100,
    top: Math.random() * 100,
    size: Math.random() * 2 + 1,
    duration: Math.random() * 3 + 2,
    delay: Math.random() * 4
  }))
}

function createParticles(count: number) {
  return Array.from({ length: count }, () => ({
    left: Math.random() * 100,
    top: Math.random() * 100,
    size: Math.random() * 2.4 + 1.2,
    opacity: Math.random() * 0.5 + 0.35,
    duration: Math.random() * 6 + 5,
    delay: Math.random() * 6
  }))
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loginPanel = ref<HTMLElement | null>(null)
const loginFormRef = ref<FormInstance | null>(null)
const passwordInput = ref<InputInstance | null>(null)
const loginForm = reactive<LoginForm>({ username: '', password: '' })
const loginRules: FormRules<LoginForm> = {
  username: [{ required: true, trigger: 'blur', message: '请输入用户名' }],
  password: [{ required: true, trigger: 'blur', message: '请输入密码' }]
}
const loading = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')
/** 默认账号提示，由后端 GET /api/auth/login-hint 决定是否显示（docs/05 第一节） */
const hint = ref<string | null>(null)

const stars = createStars(120)
const particles = createParticles(30)
const tiltX = ref(0)
const tiltY = ref(0)
const spotlightX = ref(50)
const spotlightY = ref(50)
const spotlightOpacity = ref(0)

const panelStyle = computed(() => ({
  transform: `rotateX(${tiltX.value}deg) rotateY(${tiltY.value}deg)`
}))
const spotlightStyle = computed(() => ({
  background: `radial-gradient(circle at ${spotlightX.value}% ${spotlightY.value}%, var(--accent-bg), transparent 55%)`,
  opacity: spotlightOpacity.value
}))

function onPanelMouseMove(event: MouseEvent) {
  const rect = loginPanel.value?.getBoundingClientRect()
  if (!rect) return
  const px = (event.clientX - rect.left) / rect.width
  const py = (event.clientY - rect.top) / rect.height
  tiltY.value = (px - 0.5) * 6
  tiltX.value = (0.5 - py) * 6
  spotlightX.value = px * 100
  spotlightY.value = py * 100
  spotlightOpacity.value = 1
}

function onPanelMouseLeave() {
  tiltX.value = 0
  tiltY.value = 0
  spotlightOpacity.value = 0
}

function togglePassword() {
  showPassword.value = !showPassword.value
  void nextTick(() => passwordInput.value?.focus())
}

async function handleLogin() {
  if (loading.value) return
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.login(loginForm.username, loginForm.password)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试'
    loginForm.password = ''
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    hint.value = (await fetchLoginHint())?.text ?? null
  } catch {
    hint.value = null
  }
})
</script>

<!-- 覆盖 Element Plus 输入框内部结构，只作用于登录页 -->
<style>
.login-container .login-field .el-input__wrapper {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  padding: 0;
  height: 46px;
  flex: 1;
}

.login-container .login-field .el-input__inner {
  color: var(--text-primary);
  height: 46px;
  padding: 0 6px;
  caret-color: var(--accent);
  font-size: 15px;
}

.login-container .login-field .el-input__inner:-webkit-autofill {
  box-shadow: 0 0 0 1000px var(--bg-panel-strong) inset;
  -webkit-text-fill-color: var(--text-primary);
}

.login-container .el-form-item {
  margin-bottom: 20px;
}

.login-container .el-form-item__error {
  color: var(--danger);
  padding-top: 4px;
}

.login-container .login-submit.el-button--primary {
  background: linear-gradient(135deg, var(--accent-soft) 0%, var(--accent) 100%);
  border: none;
  color: var(--bg-page);
  box-shadow: 0 12px 24px -8px var(--accent-glow);
}

.login-container .login-submit.el-button--primary:hover {
  filter: brightness(1.08);
}
</style>

<style scoped>
@keyframes starTwinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

@keyframes beamPass {
  0% { left: -10%; opacity: 0; }
  12% { opacity: 0.6; }
  50% { opacity: 0.3; }
  88% { opacity: 0.6; }
  100% { left: 110%; opacity: 0; }
}

@keyframes holoSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes particleFloat {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-16px) scale(1.15); }
}

@keyframes glowDrift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}

@keyframes ecgScroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@keyframes cornerPulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

@keyframes btnShine {
  0% { left: -60%; }
  55% { left: 120%; }
  100% { left: 120%; }
}

.login-container {
  position: fixed;
  inset: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 12% 18%, var(--accent-bg), transparent 40%),
    radial-gradient(circle at 88% 78%, var(--border-faint), transparent 42%),
    var(--bg-page);
  perspective: 1200px;
}

.login-starfield {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-star {
  position: absolute;
  border-radius: 50%;
  background: var(--text-primary);
  animation: starTwinkle ease-in-out infinite;
}

.login-bg-glow {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
  opacity: 0.5;
  background: var(--accent-glow);
  animation: glowDrift 10s ease-in-out infinite;
}

.login-bg-glow--a { top: -200px; left: -160px; }
.login-bg-glow--b { bottom: -220px; right: -180px; animation-delay: 2s; }

.login-beam {
  position: absolute;
  top: -30%;
  width: 2px;
  height: 160%;
  background: linear-gradient(180deg, transparent, var(--accent-glow), transparent);
  filter: blur(1.5px);
  transform: rotate(14deg);
  pointer-events: none;
  animation: beamPass linear infinite;
}

.login-beam--1 { animation-duration: 9s; }
.login-beam--2 { animation-duration: 13s; animation-delay: 3s; transform: rotate(-10deg); }

.login-hologram {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  pointer-events: none;
}

.login-holo-ring,
.login-holo-sweep {
  position: absolute;
  border-radius: 50%;
}

.login-holo-ring {
  border: 1px dashed var(--border-glow);
  animation: holoSpin 40s linear infinite;
}

.login-holo-ring--1 { width: 560px; height: 560px; margin: -280px 0 0 -280px; }
.login-holo-ring--2 { width: 760px; height: 760px; margin: -380px 0 0 -380px; border-style: solid; border-color: var(--border-faint); animation-direction: reverse; animation-duration: 60s; }
.login-holo-ring--3 { width: 940px; height: 940px; margin: -470px 0 0 -470px; border-color: var(--border-faint); animation-duration: 80s; }

.login-holo-sweep {
  width: 560px;
  height: 560px;
  margin: -280px 0 0 -280px;
  background: conic-gradient(from 0deg, var(--accent-glow), transparent 26%, transparent 100%);
  opacity: 0.35;
  animation: holoSpin 6s linear infinite;
  mask-image: radial-gradient(circle, transparent 0%, #000 8%, #000 92%, transparent 100%);
}

.login-particle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  background: var(--accent);
  box-shadow: 0 0 6px var(--accent-glow);
  animation: particleFloat ease-in-out infinite;
}

.login-hud-corner {
  position: absolute;
  width: 34px;
  height: 34px;
  border: 2px solid var(--border-glow);
  pointer-events: none;
  animation: cornerPulse 3s ease-in-out infinite;
}

.login-hud-corner--tl { top: 22px; left: 22px; border-right: 0; border-bottom: 0; }
.login-hud-corner--tr { top: 22px; right: 22px; border-left: 0; border-bottom: 0; }
.login-hud-corner--bl { bottom: 22px; left: 22px; border-right: 0; border-top: 0; }
.login-hud-corner--br { bottom: 22px; right: 22px; border-left: 0; border-top: 0; }

.login-ecg {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 32px;
  height: 60px;
  overflow: hidden;
  opacity: 0.35;
  mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
  pointer-events: none;
}

.login-ecg-track {
  display: flex;
  width: 200%;
  height: 100%;
  animation: ecgScroll 6s linear infinite;
}

.login-ecg-svg {
  width: 50%;
  height: 100%;
  flex-shrink: 0;
}

.login-ecg-svg polyline {
  fill: none;
  stroke: var(--accent);
  stroke-width: 2;
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 480px;
  max-width: calc(100vw - 40px);
  padding: 44px 48px 36px;
  border-radius: 12px;
  border: 1px solid var(--border-glow);
  background: var(--bg-panel-strong);
  box-shadow: 0 0 46px var(--accent-bg);
  backdrop-filter: blur(18px);
  transform-style: preserve-3d;
  transition: transform 0.15s ease-out;
}

.login-panel-spotlight {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  transition: opacity 0.25s ease-out;
}

.login-panel-corner {
  position: absolute;
  width: 22px;
  height: 22px;
  border: 2px solid var(--accent);
  opacity: 0.8;
  pointer-events: none;
}

.login-panel-corner--tl { top: -1px; left: -1px; border-right: 0; border-bottom: 0; border-radius: 10px 0 0 0; }
.login-panel-corner--tr { top: -1px; right: -1px; border-left: 0; border-bottom: 0; border-radius: 0 10px 0 0; }
.login-panel-corner--bl { bottom: -1px; left: -1px; border-right: 0; border-top: 0; border-radius: 0 0 0 10px; }
.login-panel-corner--br { bottom: -1px; right: -1px; border-left: 0; border-top: 0; border-radius: 0 0 10px 0; }

.login-brand {
  display: flex;
  justify-content: center;
  height: 60px;
}

.login-brand-title {
  margin: 18px 0 28px;
  text-align: center;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 3px;
  font-style: italic;
  background: var(--title-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.login-field {
  display: flex;
  align-items: center;
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1px solid var(--border-glow);
  border-radius: 8px;
  background: var(--bg-panel);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.login-field:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.login-field-icon,
.login-field-toggle {
  display: flex;
  align-items: center;
  color: var(--text-secondary);
}

.login-field-icon svg,
.login-field-toggle svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
}

.login-field-toggle {
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
}

.login-field-toggle:hover {
  color: var(--accent);
}

.login-error {
  margin: -6px 0 12px;
  color: var(--danger);
  font-size: 14px;
}

.login-submit {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: 46px;
  margin: 4px 0 16px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 4px;
  border-radius: 8px;
}

.login-submit::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 40%;
  height: 100%;
  background: linear-gradient(120deg, transparent, var(--shine), transparent);
  transform: skewX(-20deg);
  animation: btnShine 3s ease-in-out infinite;
}

.login-tips {
  margin: 0;
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

@media (prefers-reduced-motion: reduce) {
  .login-submit::after { animation: none; }
  .login-panel { transition: none; }
}
</style>
