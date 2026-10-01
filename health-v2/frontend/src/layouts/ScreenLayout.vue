<template>
  <ScaleScreen>
    <div class="screen">
      <div class="screen__bg" aria-hidden="true"></div>
      <ScreenFrame />

      <!-- 页头（docs/09 第二节"页头"），位置按 1920×1080 换算 -->
      <header class="header">
        <span class="header__bar" aria-hidden="true"></span>
        <div class="header__logo"><AppLogo /></div>
        <h1 class="header__title">职工健康管理系统</h1>

        <!-- 导航：入口页没有（docs/09 第五节） -->
        <nav v-if="showNav" class="header__nav" aria-label="页面导航">
          <RouterLink to="/dashboard" :class="['nav-item', { 'nav-item--active': route.name === 'dashboard' }]">
            动态数据
          </RouterLink>
          <button type="button" :class="['nav-item', { 'nav-item--active': route.path.startsWith('/archive') }]"
                  @click="openArchive">
            健康档案
          </button>
        </nav>

        <el-dropdown class="header__account" trigger="click" @command="onCommand">
          <button type="button" class="account" aria-label="账号菜单">
            <span class="account__avatar" aria-hidden="true">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="9" r="4" /><path d="M4 21c1-4.5 4.5-6.5 8-6.5s7 2 8 6.5" /></svg>
            </span>
            <span class="account__name">{{ accountName }}</span>
            <span class="account__caret" aria-hidden="true">▾</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="admin">数据管理</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="header__line" aria-hidden="true"></span>
      </header>

      <main class="screen__main">
        <RouterView />
      </main>
    </div>
  </ScaleScreen>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLogo from '@/components/AppLogo.vue'
import ScaleScreen from '@/components/ScaleScreen.vue'
import ScreenFrame from '@/components/ScreenFrame.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const showNav = computed(() => route.meta.nav === true)

function openArchive() {
  // 健康档案在阶段 5 做（docs/08）
  ElMessage.info('建设中')
}

const accountName = computed(() => {
  const name = auth.displayName
  return name ? name.charAt(0).toUpperCase() + name.slice(1) : ''
})

async function onCommand(command: string) {
  if (command === 'admin') {
    // 后台管理放最后做（docs/05 第一节）
    ElMessage.info('建设中')
    return
  }
  if (command === 'logout') {
    await auth.logout()
    await router.replace({ name: 'login' })
  }
}
</script>

<style scoped>
.screen {
  position: relative;
  width: 100%;
  height: 100%;
}

.screen__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 40%, var(--accent-bg), transparent 60%),
    radial-gradient(circle at 15% 85%, var(--border-faint), transparent 40%),
    var(--bg-page);
}

.header {
  position: absolute;
  inset: 0 0 auto 0;
  height: 140px;
}

.header__bar {
  position: absolute;
  left: 65px;
  top: 36px;
  width: 8px;
  height: 98px;
  background: var(--accent);
  box-shadow: 0 0 10px var(--accent-glow);
}

.header__logo {
  position: absolute;
  left: 91px;
  top: 50px;
  width: 207px;
  height: 62px;
}

.header__title {
  position: absolute;
  left: 317px;
  top: 52px;
  margin: 0;
  font-size: 54px;
  line-height: 64px;
  font-weight: 700;
  letter-spacing: 4px;
  white-space: nowrap;
  transform: skewX(-12deg);
  transform-origin: left bottom;
  background: var(--title-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-stroke: 1px var(--accent-glow);
  filter: drop-shadow(0 0 8px var(--accent-glow));
}

.header__account {
  position: absolute;
  left: 1747px;
  top: 78px;
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-primary);
  font-size: 18px;
  cursor: pointer;
}

.account__avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--border-glow);
  background: var(--accent-bg);
}

.account__avatar svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 2;
}

.account__caret {
  font-size: 12px;
  color: var(--text-secondary);
}

.header__line {
  position: absolute;
  left: 86px;
  right: 77px;
  top: 137px;
  height: 1px;
  background: var(--border-glow);
}

.header__nav {
  position: absolute;
  left: 1392px;
  top: 74px;
  display: flex;
  gap: 11px;
}

/* 平行四边形的深绿渐变底；当前页文字更亮、底更亮（docs/09 第二节"页头"） */
.nav-item {
  position: relative;
  display: grid;
  place-items: center;
  width: 154px;
  height: 40px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-secondary);
  font-size: 22px;
  letter-spacing: 2px;
  text-decoration: none;
  cursor: pointer;
}

.nav-item::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  transform: skewX(-20deg);
  background: linear-gradient(180deg, var(--nav-bg-top), var(--nav-bg-bottom));
  border-bottom: 2px solid var(--accent-soft);
  opacity: 0.6;
}

.nav-item--active {
  color: var(--text-bright);
  text-shadow: 0 0 8px var(--accent-glow);
}

.nav-item--active::before {
  opacity: 1;
  box-shadow: 0 0 12px var(--accent-glow);
}

.nav-item:hover {
  color: var(--text-primary);
}

/* 页面按 1920×1080 画布的绝对坐标布局，主区域铺满画布（页头在它上面） */
.screen__main {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.screen__main > :deep(*) {
  pointer-events: auto;
}

.header {
  z-index: 10;
}
</style>
