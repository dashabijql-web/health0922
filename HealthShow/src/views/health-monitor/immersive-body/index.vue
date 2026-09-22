<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { generateEmployeeReport } from '@/api/ai'
import { renderMarkdown } from '@/utils/lazy-vendors'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import BodyHologram from './BodyHologram.vue'
import ImmersiveBodyCommandLayer from './components/ImmersiveBodyCommandLayer.vue'
import EmployeeHealthHistory from './components/EmployeeHealthHistory.vue'
import {
  createImmersiveWorker,
  loadImmersiveWorker,
  loadEmployeeWarningStats,
  searchImmersiveWorkers,
  type CommandEmployee,
  type ImmersiveWarningStats,
  type ImmersiveWorker
} from './immersive-body-runtime'
import {
  buildEmployeeProfilePrintHtml,
  fmtTime,
  loadEmployeeAiReport
} from './immersive-body-panels'

defineOptions({ name: 'Body360ImmersivePage' })

const route = useRoute()
const router = useRouter()
const hologram = ref<{ resetView: () => void } | null>(null)
const reducedMotion = ref(false)
const autoRotate = ref(true)
const isCockpit = ref(false)

function toggleCockpit() {
  isCockpit.value = !isCockpit.value
  nextTick(() => {
    window.dispatchEvent(new Event('resize'))
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isCockpit.value) {
    toggleCockpit()
  }
}

// 人员、设备和体征全部来自 health-old 后端，不使用演示名单或随机数据。
const emptyWorker = createImmersiveWorker({ empCode: '', empName: '请选择人员' })
const workersList = ref<ImmersiveWorker[]>([])
const selectedWorker = ref<ImmersiveWorker>(emptyWorker)
const selectedWorkerId = ref('')
const isSelectorOpen = ref(false)
const searchQuery = ref('')
const selectorRef = ref<HTMLElement | null>(null)
const workerMissing = ref(false)
const personCommandVisible = ref(false)
const loadingWorker = ref(false)
let workerRequestSequence = 0
let searchTimer: number | null = null

// ----------------------------------------------------
// 预警统计、规则提示、AI 诊断报告（并自职工健康画像）
// ----------------------------------------------------
const emptyWarningStats: ImmersiveWarningStats = { warnings: [], warningTotal: 0, pendingTotal: 0, warning7Total: 0 }
const warningStats = ref<ImmersiveWarningStats>({ ...emptyWarningStats })
const incidentDrawerVisible = ref(false)
const currentIncidentEvent = ref<Record<string, unknown> | null>(null)
const historyRefreshToken = ref(0)

const aiReportVisible = ref(false)
const aiReportLoading = ref(false)
const aiReportContent = ref('')
const aiReportHtml = ref('')
const printWindowRef = ref<Window | null>(null)

const recentWarnings = computed(() => warningStats.value.warnings.slice(0, 4))
const recentWarningFootnote = computed(() => {
  const total = warningStats.value.warningTotal
  if (!total) return '当前没有近30日预警记录。'
  if (total > recentWarnings.value.length) {
    return `已展示最近 ${recentWarnings.value.length} 条，更多记录可进入风险事件中心继续查看。`
  }
  return '当前按时间倒序展示最近预警。'
})
const recentWarningSummary = computed(() => [
  { key: 'pending', label: '未处理', value: warningStats.value.pendingTotal, tone: warningStats.value.pendingTotal > 0 ? 'danger' : 'safe' },
  { key: 'handled', label: '已处理', value: Math.max(0, warningStats.value.warningTotal - warningStats.value.pendingTotal), tone: 'safe' },
  { key: 'warn7', label: '近7日', value: warningStats.value.warning7Total, tone: warningStats.value.warning7Total > 0 ? 'warning' : 'muted' },
  { key: 'warn30', label: '近30日', value: warningStats.value.warningTotal, tone: warningStats.value.warningTotal > 0 ? 'accent' : 'muted' }
])

async function refreshWarningStats(empCode: string) {
  warningStats.value = await loadEmployeeWarningStats(empCode)
}

function openEmergency(event: Record<string, unknown>) {
  if (!event?.id || !event?.occurredAt) return
  currentIncidentEvent.value = event
  incidentDrawerVisible.value = true
}

async function handleIncidentUpdated() {
  await refreshWarningStats(currentWorker.value.id)
}

function goWarningCenter() {
  router.push({
    path: '/alert-management/records',
    query: { keyword: currentWorker.value.name || currentWorker.value.id || '' }
  })
}

const { start: startPrintTask } = useTimeoutTask(() => printWindowRef.value?.print(), 600)

async function openAiReport() {
  if (!currentWorker.value.id) return
  aiReportContent.value = ''
  aiReportHtml.value = ''
  aiReportLoading.value = true
  aiReportVisible.value = true
  try {
    const report = await loadEmployeeAiReport(currentWorker.value.id, generateEmployeeReport, renderMarkdown)
    aiReportContent.value = report.content
    aiReportHtml.value = report.html
  } catch {
    aiReportContent.value = ''
    aiReportHtml.value = ''
  } finally {
    aiReportLoading.value = false
  }
}

function printAiReport() {
  if (!aiReportContent.value) return
  const html = buildEmployeeProfilePrintHtml(currentWorker.value.name, aiReportHtml.value)
  const win = window.open('', '_blank')
  if (!win) return
  win.document.write(html)
  win.document.close()
  printWindowRef.value = win
  startPrintTask()
}

const requestedEmpCode = computed(() => {
  const raw = route.query.empCode ?? route.query.id
  return typeof raw === 'string' ? raw.trim() : ''
})
const requestedEmpName = computed(() => (
  typeof route.query.empName === 'string' ? route.query.empName.trim() : ''
))

const currentWorker = computed(() => selectedWorker.value)

function toWorker(person: CommandEmployee) {
  return createImmersiveWorker(person)
}

async function loadWorker(person: CommandEmployee) {
  const sequence = ++workerRequestSequence
  selectedWorkerId.value = person.empCode
  selectedWorker.value = toWorker(person)
  workerMissing.value = false
  loadingWorker.value = true
  warningStats.value = { ...emptyWarningStats }
  aiReportVisible.value = false
  aiReportContent.value = ''
  aiReportHtml.value = ''
  try {
    const [detail] = await Promise.all([
      loadImmersiveWorker(person),
      refreshWarningStats(person.empCode)
    ])
    if (sequence !== workerRequestSequence) return
    selectedWorker.value = detail
    const index = workersList.value.findIndex(item => item.id === detail.id)
    if (index >= 0) workersList.value.splice(index, 1, detail)
    historyRefreshToken.value += 1
  } catch {
    if (sequence === workerRequestSequence) workerMissing.value = true
  } finally {
    if (sequence === workerRequestSequence) loadingWorker.value = false
  }
}

async function refreshWorkerList(query = '') {
  try {
    const people = await searchImmersiveWorkers(query)
    workersList.value = people.map(toWorker)
    return people
  } catch {
    workersList.value = []
    return []
  }
}

async function applyQueryWorker() {
  const code = requestedEmpCode.value
  const people = await refreshWorkerList(code)
  const person = code ? people.find(item => item.empCode === code) : people[0]
  if (!person) {
    workerMissing.value = Boolean(code)
    selectedWorkerId.value = ''
    selectedWorker.value = createImmersiveWorker({
      empCode: code,
      empName: requestedEmpName.value || (code ? '未找到人员' : '暂无人员')
    })
    return
  }
  await loadWorker(person)
}

const isOfflineOrNoData = computed(() => {
  return currentWorker.value.freshnessStatus === 'no_data' || currentWorker.value.telemetry.netty !== 'online'
})

const filteredWorkers = computed(() => workersList.value)

function selectWorker(worker: ImmersiveWorker) {
  isSelectorOpen.value = false
  void loadWorker({
    empCode: worker.id,
    empName: worker.name,
    deptName: worker.team,
    jobTypeName: worker.role,
    imei: worker.imei,
    online: worker.telemetry.netty === 'online'
  })
}

function cycleWorker(direction: number) {
  if (workersList.value.length === 0) return
  const currentIndex = workersList.value.findIndex(w => w.id === selectedWorkerId.value)
  const base = currentIndex < 0 ? 0 : currentIndex
  const nextIndex = (base + direction + workersList.value.length) % workersList.value.length
  selectWorker(workersList.value[nextIndex])
}

function resetView() {
  hologram.value?.resetView()
}

function openMessageDialog() {
  if (isOfflineOrNoData.value) return
  personCommandVisible.value = true
}

function triggerVoicePrompt() {
  if (isOfflineOrNoData.value) return
  personCommandVisible.value = true
}

function triggerCall() {
  personCommandVisible.value = true
}

function handleGlobalClick(event: MouseEvent) {
  if (isSelectorOpen.value && selectorRef.value && !selectorRef.value.contains(event.target as Node)) {
    isSelectorOpen.value = false
  }
}

async function refreshCurrentWorker() {
  if (!currentWorker.value.id || loadingWorker.value) return
  await loadWorker({
    empCode: currentWorker.value.id,
    empName: currentWorker.value.name,
    deptName: currentWorker.value.team,
    jobTypeName: currentWorker.value.role,
    imei: currentWorker.value.imei,
    online: currentWorker.value.telemetry.netty === 'online'
  })
}

watch(searchQuery, (query) => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { void refreshWorkerList(query) }, 250)
})

watch(
  () => [route.query.empCode, route.query.id, route.query.empName] as const,
  () => { void applyQueryWorker() }
)

let refreshTimer: number | null = null

onMounted(() => {
  window.addEventListener('click', handleGlobalClick)
  window.addEventListener('keydown', handleKeydown)
  void applyQueryWorker()
  refreshTimer = window.setInterval(() => { void refreshCurrentWorker() }, 30000)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', handleGlobalClick)
  window.removeEventListener('keydown', handleKeydown)
  if (searchTimer) window.clearTimeout(searchTimer)
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div class="immersive-page" :class="{ 'is-cockpit': isCockpit }">
   <div class="immersive-stage">
    <!-- 底层：全屏满铺 3D 线框人体背景（接收全屏鼠标拖拽旋转） -->
    <div class="immersive-bg">
      <BodyHologram
        v-if="!workerMissing"
        ref="hologram"
        variant="immersive"
        :person-name="currentWorker.name"
        :vitals="currentWorker.vitals"
        :freshness-status="currentWorker.freshnessStatus"
        :last-collected="currentWorker.lastCollected"
        :reduced-motion="reducedMotion"
        :auto-rotate="autoRotate"
      />
      <div v-else class="missing-stage" role="status">
        未加载三维人体。名单中没有该人员，不会改用其他人的数据。
      </div>
    </div>

    <!-- 顶层悬浮 HUD 玻璃层容器（容器本身 pointer-events: none，内部玻璃条 pointer-events: auto） -->
    <div class="immersive-overlay">
      <!-- 1. 顶部半透明磨砂矮栏 (约 36px 高) -->
      <header class="glass-bar glass-bar--top">
        <div class="top-left">
          <div class="top-title-group">
            <span class="kicker-dot"></span>
            <span class="top-kicker">HUMAN BIOMETRICS 360°</span>
            <h1 class="top-title">3D沉浸人体</h1>
            <span class="badge-pill">真实数据</span>
          </div>
        </div>

        <div class="top-right">
          <!-- 采集时间与新鲜度 -->
          <div class="freshness-tag" :class="`is-${currentWorker.freshnessStatus}`">
            <span class="freshness-dot"></span>
            <span class="freshness-text">
              {{ currentWorker.freshnessStatus === 'fresh' ? '在线' : (currentWorker.freshnessStatus === 'stale' ? '陈旧' : '离线') }}
            </span>
            <span class="freshness-time">· {{ currentWorker.lastCollected || '暂无记录' }}</span>
          </div>

          <!-- 克制人员切换器 -->
          <div ref="selectorRef" class="worker-switcher">
            <div class="switcher-controls">
              <button
                type="button"
                class="switcher-nav-btn"
                title="上一位人员"
                @click.stop="cycleWorker(-1)"
              >
                ‹
              </button>
              <button
                type="button"
                class="switcher-trigger"
                :class="{ 'is-active': isSelectorOpen }"
                @click.stop="isSelectorOpen = !isSelectorOpen"
              >
                <span class="switcher-avatar-dot" :class="`is-${currentWorker.freshnessStatus}`"></span>
                <span class="switcher-name">{{ currentWorker.name }}</span>
                <span class="switcher-id">#{{ currentWorker.id }}</span>
                <span class="switcher-caret">▾</span>
              </button>
              <button
                type="button"
                class="switcher-nav-btn"
                title="下一位人员"
                @click.stop="cycleWorker(1)"
              >
                ›
              </button>
            </div>

            <!-- 人员下拉浮层 -->
            <transition name="dropdown-fade">
              <div v-if="isSelectorOpen" class="switcher-dropdown">
                <div class="dropdown-header">
                  <input
                    v-model="searchQuery"
                    type="text"
                    placeholder="搜索姓名 / 工号 / 班组..."
                    class="dropdown-search"
                    autofocus
                    @click.stop
                  />
                </div>
                <div class="dropdown-list">
                  <div
                    v-for="worker in filteredWorkers"
                    :key="worker.id"
                    class="dropdown-item"
                    :class="{ 'is-selected': worker.id === currentWorker.id }"
                    @click.stop="selectWorker(worker)"
                  >
                    <div class="item-left">
                      <span class="item-dot" :class="`is-${worker.freshnessStatus}`"></span>
                      <div>
                        <div class="item-name-row">
                          <strong>{{ worker.name }}</strong>
                          <span class="item-id">{{ worker.id }}</span>
                        </div>
                        <span class="item-team">{{ worker.team }} · {{ worker.role }}</span>
                      </div>
                    </div>
                    <span class="item-status-tag" :class="`is-${worker.freshnessStatus}`">
                      {{ worker.freshnessStatus === 'fresh' ? '在线' : (worker.freshnessStatus === 'stale' ? '陈旧' : '无数据') }}
                    </span>
                  </div>
                  <div v-if="filteredWorkers.length === 0" class="dropdown-empty">
                    无匹配人员
                  </div>
                </div>
              </div>
            </transition>
          </div>

          <!-- AI 诊断报告入口 -->
          <button
            type="button"
            class="cockpit-btn"
            :disabled="!currentWorker.id || aiReportLoading"
            @click="openAiReport"
            title="生成该人员的 AI 健康诊断报告"
          >
            <svg class="cockpit-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M4 2h6l2 2v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z" stroke-linejoin="round"/>
              <path d="M5.5 7h5M5.5 9.5h5M5.5 12h3" stroke-linecap="round"/>
            </svg>
            <span>{{ aiReportLoading ? 'AI 生成中...' : 'AI 诊断报告' }}</span>
          </button>

          <!-- 全屏舱切换开关 (藏掉应用侧栏与顶栏) -->
          <button
            type="button"
            class="cockpit-btn"
            :class="{ 'is-active': isCockpit }"
            @click="toggleCockpit"
            :title="isCockpit ? '退出全屏舱 (ESC)' : '进入全屏舱：隐藏应用侧栏与顶栏'"
          >
            <svg class="cockpit-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
              <path v-if="!isCockpit" d="M2 5V2h3M11 2h3v3M14 11v3h-3M5 14H2v-3" stroke-linecap="round" stroke-linejoin="round"/>
              <path v-else d="M5 2v3H2M11 2v3h3M5 14v-3H2M11 14v-3h3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>{{ isCockpit ? '退出全屏舱' : '全屏舱' }}</span>
          </button>
        </div>
      </header>

      <div v-if="workerMissing" class="missing-banner" role="alert">
        未找到工号 {{ requestedEmpCode }}{{ requestedEmpName ? `（${requestedEmpName}）` : '' }} 的沉浸人体数据。当前没有加载任何人，请从名单选择或返回。
      </div>

      <!-- 2a. 左上浮岛：按内容撑开的矮卡 (身份 + 两行遥测缩写)，高约 160~180px，不拉长 -->
      <aside v-if="!workerMissing" class="glass-card glass-island--left-top">
        <div class="glass-card-header">
          <span class="glass-card-eyebrow">职工标卡</span>
          <span class="glass-card-tag">在册</span>
        </div>

        <!-- 身份一行 -->
        <div class="worker-brief">
          <div class="brief-row">
            <span class="brief-dot" :class="`is-${currentWorker.freshnessStatus}`"></span>
            <strong class="brief-name">{{ currentWorker.name }}</strong>
            <span class="brief-id">#{{ currentWorker.id }}</span>
            <span class="brief-status" :class="`is-${currentWorker.freshnessStatus}`">
              {{ currentWorker.freshnessStatus === 'fresh' ? '在线' : (currentWorker.freshnessStatus === 'stale' ? '陈旧' : '离线') }}
            </span>
          </div>
          <div class="brief-team">
            {{ currentWorker.team }} · {{ currentWorker.role }}
            <template v-if="currentWorker.gender">· {{ currentWorker.gender === 2 ? '女' : '男' }}</template>
          </div>
        </div>

        <!-- 遥测两行缩写 -->
        <div class="telemetry-compact">
          <div class="telemetry-line">
            <span class="t-label">电量</span>
            <strong
              class="t-val"
              :class="currentWorker.telemetry.battery ? (currentWorker.telemetry.battery > 20 ? 'is-good' : 'is-low') : 'is-none'"
            >
              {{ currentWorker.telemetry.battery !== null ? currentWorker.telemetry.battery + '%' : '--' }}
            </strong>
            <span class="t-sep">·</span>
            <span class="t-label">Netty</span>
            <strong
              class="t-val"
              :class="currentWorker.telemetry.netty === 'online' ? 'is-good' : (currentWorker.telemetry.netty === 'reconnecting' ? 'is-warn' : 'is-none')"
            >
              {{ currentWorker.telemetry.netty === 'online' ? '在线' : (currentWorker.telemetry.netty === 'reconnecting' ? '重连' : '断开') }}
            </strong>
          </div>

          <div class="telemetry-line">
            <span class="t-label">信号</span>
            <strong
              class="t-val"
              :class="currentWorker.telemetry.signal === '5G' || currentWorker.telemetry.signal === '4G' ? 'is-good' : 'is-none'"
            >
              {{ currentWorker.telemetry.signal === '5G' ? '5G' : (currentWorker.telemetry.signal === '4G' ? '4G' : (currentWorker.telemetry.signal === 'weak' ? '弱' : '无')) }}
            </strong>
            <span class="t-sep">·</span>
            <span class="t-label">佩戴</span>
            <strong
              class="t-val"
              :class="currentWorker.telemetry.wearing === 'confirmed' ? 'is-good' : 'is-none'"
            >
              {{ currentWorker.telemetry.wearing === 'confirmed' ? '已佩戴' : '未检测' }}
            </strong>
          </div>
        </div>
      </aside>

      <!-- 2b. 左下浮岛：独立矮联系条，贴走势条上方，与身份卡完全断开，中间彻底露出 3D 人体与粒子 -->
      <div class="glass-bar glass-island--left-bottom">
        <div class="contact-actions-compact">
          <button
            type="button"
            class="c-btn"
            :disabled="isOfflineOrNoData"
            title="向此人终端发送调度消息"
            @click="openMessageDialog"
          >
            <svg class="c-icon" viewBox="0 0 16 16" fill="currentColor">
              <path d="M2 3a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5.414L2.707 14.707A1 1 0 0 1 1 14V3a2 2 0 0 1 1-1z"/>
            </svg>
            <span>发消息</span>
          </button>

          <button
            type="button"
            class="c-btn"
            :disabled="isOfflineOrNoData"
            title="向手表发送蜂鸣提醒"
            @click="triggerVoicePrompt"
          >
            <svg class="c-icon" viewBox="0 0 16 16" fill="currentColor">
              <path d="M11.536 14.01A8.473 8.473 0 0 0 14.026 8a8.473 8.473 0 0 0-2.49-6.01l-.708.707A7.476 7.476 0 0 1 13.025 8c0 2.071-.84 3.946-2.197 5.303l.708.707z"/>
              <path d="M8.707 11.182A4.486 4.486 0 0 0 10.025 8a4.486 4.486 0 0 0-1.318-3.182L8 5.525A3.489 3.489 0 0 1 9.025 8 3.49 3.49 0 0 1 8 10.475l.707.707zM6.717 3.55A.5.5 0 0 1 7 4v8a.5.5 0 0 1-.812.39L3.825 10.5H1.5A.5.5 0 0 1 1 10V6a.5.5 0 0 1 .5-.5h2.325l2.363-1.89a.5.5 0 0 1 .529-.06z"/>
            </svg>
            <span>语音</span>
          </button>

          <button
            type="button"
            class="c-btn c-btn--call"
            title="语音通话未配置通道"
            @click="triggerCall"
          >
            <svg class="c-icon" viewBox="0 0 16 16" fill="currentColor">
              <path d="M3.654 1.328a.678.678 0 0 0-1.015-.063L1.605 2.3c-.483.484-.661 1.169-.45 1.77a17.568 17.568 0 0 0 4.168 6.608 17.569 17.569 0 0 0 6.608 4.168c.601.211 1.286.033 1.77-.45l1.034-1.034a.678.678 0 0 0-.063-1.015l-2.307-1.794a.678.678 0 0 0-.58-.122l-2.19.547a1.745 1.745 0 0 1-1.657-.459L5.482 8.062a1.745 1.745 0 0 1-.46-1.657l.548-2.19a.678.678 0 0 0-.122-.58L3.654 1.328z"/>
            </svg>
            <span>电话</span>
            <span class="c-badge">未配</span>
          </button>
        </div>
      </div>

      <!-- 3. 浮动 3D 视角控制条 (悬浮于走势条上方，不遮挡曲线) -->
      <div class="glass-bar glass-controls">
        <label class="glass-checkbox">
          <input v-model="autoRotate" type="checkbox" />
          <span>旋转</span>
        </label>
        <label class="glass-checkbox">
          <input v-model="reducedMotion" type="checkbox" />
          <span>静止</span>
        </label>
        <button type="button" class="glass-reset-btn" @click="resetView">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" class="btn-icon">
            <path d="M2.5 8a5.5 5.5 0 1 0 1.5-3.8L2 6M2 2v4h4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          重置
        </button>
      </div>

      <!-- 4. 左侧悬浮玻璃面板：人体已向右平移让出空白，近30日预警记录浮在此处，无需下滑查看 -->
      <div class="ib-warning-panel">
        <div class="ib-panel-head">
          <span class="ib-panel-bar"></span>近30日预警记录
          <span class="ib-panel-note">{{ recentWarningFootnote }}</span>
        </div>

        <div class="ib-warning-stats">
          <div v-for="item in recentWarningSummary" :key="item.key" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
          </div>
        </div>

        <div v-if="recentWarnings.length === 0" class="ib-empty-warn">该员工近30日暂无预警记录</div>
        <div v-else class="ib-warning-list">
          <article v-for="(warning, index) in recentWarnings" :key="(warning.id || warning.createTime) + '_' + index" class="ib-warning-item">
            <div>
              <strong>{{ warning.warningType || warning.type || '--' }}</strong>
              <span>{{ fmtTime(warning.occurredAt || warning.createTime || warning.time) }}</span>
            </div>
            <em :class="warning.handled ? 'done' : 'pend'">{{ warning.handled ? '已处理' : '待处理' }}</em>
            <el-button
              v-if="!warning.handled && warning.id && (warning.occurredAt || warning.createTime || warning.time)"
              type="danger"
              link
              @click="openEmergency({ ...warning, occurredAt: warning.occurredAt || warning.createTime || warning.time })"
            >处置</el-button>
          </article>
        </div>

        <div class="ib-warning-footer">
          <el-button type="primary" link @click="goWarningCenter">查看全部预警</el-button>
        </div>
      </div>
    </div>
   </div>

   <!-- 3D 舞台下方：历史健康数据（全屏舱模式下隐藏，保持沉浸纯净） -->
   <section v-show="!isCockpit" class="immersive-below">
     <EmployeeHealthHistory
       :employee-code="currentWorker.id"
       :employee-name="currentWorker.name"
       :refresh-token="historyRefreshToken"
     />
   </section>

    <el-dialog
      v-model="aiReportVisible"
      :title="'AI 健康诊断报告 - ' + currentWorker.name"
      width="820px"
      class="ib-ai-report-dialog"
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-if="aiReportLoading" class="ib-report-loading">
        <div class="ib-report-dots"><span></span><span></span><span></span></div>
        <p>正在生成健康诊断报告，请稍候...</p>
      </div>
      <div v-else-if="aiReportContent" v-html="aiReportHtml" class="ib-report-content"></div>
      <div v-else class="ib-report-empty">生成失败，请重试</div>
      <template #footer>
        <el-button @click="aiReportVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!aiReportContent || aiReportLoading" @click="printAiReport">打印 / 导出 PDF</el-button>
      </template>
    </el-dialog>

    <ImmersiveBodyCommandLayer
      v-model:person-visible="personCommandVisible"
      v-model:incident-visible="incidentDrawerVisible"
      :person="{ empCode: currentWorker.id, empName: currentWorker.name, deptName: currentWorker.team, imei: currentWorker.imei }"
      :online="currentWorker.telemetry.netty === 'online'"
      :incident="currentIncidentEvent"
      @emergency="openEmergency"
      @updated="handleIncidentUpdated"
    />
  </div>
</template>

<style scoped>
/* 全屏容器：吃满 Layout 内容区，3D 舞台之外自然文档流，可向下滚动到画像内容 */
.immersive-page {
  position: relative;
  width: 100%;
  transition: all 0.2s ease;
}

/* 3D 舞台：固定视口高度，纯粹通透无暗角遮挡（去掉底部走势条后拉高，人体展示区域更大） */
.immersive-stage {
  position: relative;
  width: 100%;
  height: calc(100vh - 24px);
  min-height: 680px;
  overflow: hidden;
  background: #05080e;
  border-radius: var(--radius-md);
}

/* 全屏舱模式：逃逸外层 Layout，满屏覆盖，隐藏侧栏与顶栏；下方画像内容一并隐藏保持沉浸纯净 */
.immersive-page.is-cockpit {
  position: fixed !important;
  inset: 0 !important;
  z-index: 9999 !important;
  width: 100vw !important;
  height: 100vh !important;
  margin: 0 !important;
  overflow: hidden;
}

.immersive-page.is-cockpit .immersive-stage {
  height: 100%;
  border-radius: 0;
}

/* 3D 背景层：铺满整屏，接收鼠标拖拽事件，严格铺在最底层 z-index: 0 */
.immersive-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: auto;
}

.missing-stage {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 24px;
  color: #8fa7c3;
  font-size: 14px;
  text-align: center;
  background: #06111f;
}

.missing-banner {
  pointer-events: auto;
  margin: 8px 16px 0;
  padding: 10px 14px;
  border: 1px solid rgba(245, 158, 11, 0.4);
  border-radius: 6px;
  background: rgba(12, 16, 22, 0.92);
  color: #fcd34d;
  font-size: 13px;
  line-height: 1.5;
  max-width: 720px;
}

/* 浮动玻璃 HUD 层：容器贯穿无阻，内层卡片独立交互，悬浮于画布上方 z-index: 1 */
.immersive-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

/* 3D 舞台下方：历史健康数据，正常文档流可滚动（近30日预警记录已移入舞台左侧悬浮层） */
.immersive-below {
  position: relative;
  z-index: 1;
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 悬浮在舞台左侧空白区的预警面板（人体已右移让出此区域），随内容纵向排布、超高时内部滚动 */
.ib-warning-panel {
  position: absolute;
  left: 4%;
  top: 232px;
  bottom: 164px;
  width: 33%;
  min-width: 300px;
  max-width: 440px;
  z-index: 2;
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(8, 12, 18, 0.28);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35), inset 0 0 1px rgba(255, 255, 255, 0.12);
  padding: 16px;
}

.ib-panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.ib-panel-bar {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--el-color-primary, #409eff);
}

.ib-panel-note {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
}

.ib-warning-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.ib-warning-stats > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.ib-warning-stats span {
  font-size: 12px;
  color: var(--text-secondary);
}

.ib-warning-stats strong {
  font-size: 18px;
  color: var(--text-primary);
}

.ib-warning-stats .tone-danger strong { color: var(--status-warning, #f59e0b); }
.ib-warning-stats .tone-warning strong { color: var(--status-warning, #f59e0b); }
.ib-warning-stats .tone-accent strong { color: var(--el-color-primary, #409eff); }

.ib-empty-warn {
  color: var(--text-secondary);
  text-align: center;
  padding: 24px 0;
}

.ib-warning-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
}

.ib-warning-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.ib-warning-item strong {
  color: var(--text-primary);
}

.ib-warning-item span {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.ib-warning-item em {
  font-style: normal;
  font-size: 12px;
}

.ib-warning-item em.pend { color: var(--status-warning, #f59e0b); }
.ib-warning-item em.done { color: var(--status-normal, #22c55e); }

.ib-warning-footer {
  margin-top: 12px;
  text-align: right;
  flex-shrink: 0;
}

.ib-report-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--text-secondary);
}

.ib-report-dots {
  display: flex;
  gap: 6px;
}

.ib-report-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
  animation: ib-report-dot-bounce 1.2s infinite ease-in-out;
}

.ib-report-dots span:nth-child(2) { animation-delay: 0.15s; }
.ib-report-dots span:nth-child(3) { animation-delay: 0.3s; }

@keyframes ib-report-dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.ib-report-content {
  max-height: 60vh;
  overflow-y: auto;
  line-height: 1.7;
}

.ib-report-empty {
  text-align: center;
  color: var(--text-secondary);
  padding: 24px 0;
}

/* 玻璃通用质感：严格限制背景透明度 <= 0.24，采用 5px 微磨砂保持背景人体与粒子清晰透出 */
.glass-bar,
.glass-card {
  pointer-events: auto;
  background: rgba(8, 12, 18, 0.22);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35), inset 0 0 1px rgba(255, 255, 255, 0.12);
}

/* ----------------------------------------------------
   1. 顶部玻璃矮栏 (约 36px 高)
   ---------------------------------------------------- */
.glass-bar--top {
  position: absolute;
  top: 8px;
  left: 10px;
  right: 10px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border-radius: 6px;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kicker-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--signal-cyan, #38bdf8);
  box-shadow: 0 0 8px var(--signal-cyan, #38bdf8);
}

.top-kicker {
  color: var(--signal-cyan, #38bdf8);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.08em;
  font-weight: 600;
}

.top-title {
  margin: 0;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.85);
}

.badge-pill {
  padding: 1px 6px;
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 3px;
  background: rgba(56, 189, 248, 0.08);
  color: var(--signal-cyan, #38bdf8);
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 600;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.freshness-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 7px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  font-size: 10.5px;
  font-family: var(--font-mono);
}

.freshness-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--status-standby);
}

.freshness-tag.is-fresh .freshness-dot {
  background: var(--status-normal, #22c55e);
  box-shadow: 0 0 6px var(--status-normal, #22c55e);
}
.freshness-tag.is-fresh .freshness-text { color: var(--status-normal, #22c55e); font-weight: 600; }

.freshness-tag.is-stale .freshness-dot { background: var(--status-warning, #f59e0b); }
.freshness-tag.is-stale .freshness-text { color: var(--status-warning, #f59e0b); font-weight: 600; }

.freshness-tag.is-no_data .freshness-dot { background: var(--status-standby); }
.freshness-tag.is-no_data .freshness-text { color: var(--status-standby); }

.freshness-time {
  color: var(--text-muted);
}

/* 克制切换器 */
.worker-switcher {
  position: relative;
  flex-shrink: 0;
}

.switcher-controls {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  overflow: hidden;
}

.switcher-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.switcher-nav-btn:hover {
  background: rgba(56, 189, 248, 0.15);
  color: #fff;
}

.switcher-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 8px;
  border: none;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  color: var(--text-strong);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.switcher-trigger:hover,
.switcher-trigger.is-active {
  background: rgba(56, 189, 248, 0.12);
}

.switcher-avatar-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--status-standby);
}

.switcher-avatar-dot.is-fresh {
  background: var(--status-normal, #22c55e);
  box-shadow: 0 0 6px var(--status-normal, #22c55e);
}

.switcher-avatar-dot.is-stale {
  background: var(--status-warning, #f59e0b);
}

.switcher-name {
  font-weight: 600;
}

.switcher-id {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.switcher-caret {
  font-size: 8px;
  color: var(--text-muted);
}

/* 下拉浮层 */
.switcher-dropdown {
  position: absolute;
  top: calc(100% + 5px);
  right: 0;
  z-index: 50;
  width: 250px;
  padding: 6px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  background: #0d121c;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.75);
}

.dropdown-header {
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-dim);
}

.dropdown-search {
  width: 100%;
  box-sizing: border-box;
  padding: 4px 7px;
  border: 1px solid var(--border-dim);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-strong);
  font-size: 11.5px;
  outline: none;
}

.dropdown-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 7px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.dropdown-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.dropdown-item.is-selected {
  background: rgba(56, 189, 248, 0.14);
}

.item-left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.item-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--status-standby);
  flex-shrink: 0;
}

.item-dot.is-fresh { background: var(--status-normal, #22c55e); box-shadow: 0 0 6px var(--status-normal, #22c55e); }
.item-dot.is-stale { background: var(--status-warning, #f59e0b); }
.item-dot.is-no_data { background: var(--status-standby); }

.item-name-row {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.item-name-row strong {
  color: var(--text-strong);
  font-size: 12px;
  font-weight: 600;
}

.item-id {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.item-team {
  color: var(--text-secondary);
  font-size: 10.5px;
}

.item-status-tag {
  padding: 1px 4px;
  border-radius: 2px;
  font-size: 9.5px;
  font-family: var(--font-mono);
}

.item-status-tag.is-fresh {
  color: var(--status-normal, #22c55e);
  background: rgba(34, 197, 94, 0.15);
}

.item-status-tag.is-stale {
  color: var(--status-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.15);
}

.item-status-tag.is-no_data {
  color: var(--status-standby);
  background: rgba(100, 116, 139, 0.15);
}

.dropdown-empty {
  padding: 10px;
  color: var(--text-muted);
  text-align: center;
  font-size: 11px;
}

/* 全屏舱按钮 */
.cockpit-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 25px;
  padding: 0 9px;
  border: 1px solid rgba(56, 189, 248, 0.35);
  border-radius: 4px;
  background: rgba(56, 189, 248, 0.08);
  color: var(--signal-cyan, #38bdf8);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.cockpit-btn:hover {
  background: rgba(56, 189, 248, 0.22);
  border-color: var(--signal-cyan, #38bdf8);
  color: #fff;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.35);
}

.cockpit-btn.is-active {
  background: rgba(56, 189, 248, 0.3);
  border-color: #38bdf8;
  color: #fff;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
}

.cockpit-icon {
  width: 12px;
  height: 12px;
}

/* ----------------------------------------------------
   2a. 左上浮岛：按内容撑开的矮卡 (约 200px 宽，高约 160~180px，不拉长)
   ---------------------------------------------------- */
.glass-island--left-top {
  position: absolute;
  top: 48px;
  left: 10px;
  width: 200px;
  height: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: 6px;
  background: rgba(8, 12, 18, 0.22);
}

/* ----------------------------------------------------
   2b. 左下浮岛：独立矮联系条 (贴在走势条上方，与身份卡完全断开)
   中间空出大面积完全透光的 3D 人体活动区，绝无贯通玻璃柱
   ---------------------------------------------------- */
.glass-island--left-bottom {
  position: absolute;
  bottom: 106px;
  left: 10px;
  width: 200px;
  height: auto;
  padding: 4px;
  border-radius: 6px;
  background: rgba(8, 12, 18, 0.22);
}

.glass-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 5px;
}

.glass-card-eyebrow {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.glass-card-tag {
  padding: 1px 5px;
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 2px;
  background: rgba(56, 189, 248, 0.08);
  color: var(--signal-cyan, #38bdf8);
  font-size: 9.5px;
  font-weight: 600;
}

/* 身份一行 */
.worker-brief {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brief-row {
  display: flex;
  align-items: center;
  gap: 5px;
}

.brief-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--status-standby);
  flex-shrink: 0;
}

.brief-dot.is-fresh {
  background: var(--status-normal, #22c55e);
  box-shadow: 0 0 6px var(--status-normal, #22c55e);
}

.brief-dot.is-stale {
  background: var(--status-warning, #f59e0b);
}

.brief-name {
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.brief-id {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.brief-status {
  margin-left: auto;
  font-size: 9.5px;
  font-family: var(--font-mono);
  padding: 1px 4px;
  border-radius: 2px;
}

.brief-status.is-fresh {
  color: var(--status-normal, #22c55e);
  background: rgba(34, 197, 94, 0.15);
}

.brief-status.is-stale {
  color: var(--status-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.15);
}

.brief-status.is-no_data {
  color: var(--status-standby);
  background: rgba(100, 116, 139, 0.15);
}

.brief-team {
  color: var(--text-secondary);
  font-size: 10.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 遥测两行缩写 */
.telemetry-compact {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.02);
}

.telemetry-line {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
}

.t-label {
  color: var(--text-muted);
  font-size: 9.5px;
}

.t-val {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
}

.t-val.is-good { color: var(--status-normal, #22c55e); }
.t-val.is-warn { color: var(--status-warning, #f59e0b); }
.t-val.is-low { color: var(--status-danger, #ef4444); }
.t-val.is-none { color: var(--status-standby, #64748b); }

.t-sep {
  color: rgba(255, 255, 255, 0.2);
  font-size: 9px;
  margin: 0 1px;
}

.contact-actions-compact {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 4px;
}

.c-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 38px;
  padding: 2px 2px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-strong);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
}

.c-btn:hover:not(:disabled) {
  border-color: var(--signal-cyan, #38bdf8);
  background: rgba(56, 189, 248, 0.18);
  color: #fff;
}

.c-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  border-color: rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.01);
}

.c-icon {
  width: 12px;
  height: 12px;
  opacity: 0.85;
}

.c-btn--call {
  color: var(--text-muted);
}

.c-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  font-size: 7.5px;
  padding: 0 3px;
  border-radius: 2px;
  background: rgba(245, 158, 11, 0.25);
  color: var(--status-warning, #f59e0b);
  font-family: var(--font-mono);
}

/* ----------------------------------------------------
   3. 浮动 3D 视角控制条
   ---------------------------------------------------- */
.glass-controls {
  position: absolute;
  right: 10px;
  bottom: 106px;
  height: 26px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

.glass-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}

.glass-checkbox input[type='checkbox'] {
  accent-color: var(--signal-cyan, #38bdf8);
  cursor: pointer;
}

.glass-reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-strong);
  font-size: 10.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.glass-reset-btn:hover {
  background: rgba(56, 189, 248, 0.18);
  border-color: var(--signal-cyan, #38bdf8);
  color: #fff;
}

.btn-icon {
  width: 10px;
  height: 10px;
}

/* 弹窗深色磨砂工业风 */
:deep(.custom-msg-dialog) {
  background: rgba(13, 18, 28, 0.94) !important;
  backdrop-filter: blur(14px) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  border-radius: 8px !important;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.8) !important;
}

:deep(.custom-msg-dialog .el-dialog__header) {
  padding: 14px 18px 10px;
  margin-right: 0;
  border-bottom: 1px solid var(--border-dim);
}

:deep(.custom-msg-dialog .el-dialog__title) {
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

:deep(.custom-msg-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: var(--text-muted);
}

:deep(.custom-msg-dialog .el-dialog__body) {
  padding: 16px 18px 8px;
}

:deep(.custom-msg-dialog .el-dialog__footer) {
  padding: 10px 18px 16px;
  border-top: 1px solid var(--border-dim);
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recipient-bar {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  font-size: 12px;
}

.r-label { color: var(--text-muted); }
.r-name { color: var(--text-strong); font-size: 14px; }
.r-desc { color: var(--text-secondary); font-size: 11px; }

.preset-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preset-title { color: var(--text-muted); font-size: 11px; }

.preset-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preset-btn {
  padding: 3px 8px;
  border: 1px solid var(--border-dim);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.preset-btn:hover {
  border-color: var(--signal-cyan);
  color: #fff;
  background: rgba(56, 189, 248, 0.15);
}

.textarea-box {
  position: relative;
}

.dialog-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.4);
  color: var(--text-strong);
  font-family: var(--font);
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.15s ease;
}

.dialog-textarea:focus {
  border-color: var(--signal-cyan);
}

.char-count {
  position: absolute;
  right: 8px;
  bottom: 8px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.char-count.is-limit { color: var(--status-warning); }

.dialog-disclaimer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 4px;
  background: rgba(245, 158, 11, 0.06);
  color: var(--text-muted);
  font-size: 11px;
}

.disclaimer-tag {
  color: var(--status-warning);
  font-weight: 600;
  flex-shrink: 0;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-cancel {
  padding: 5px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.btn-cancel:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.btn-send {
  padding: 5px 16px;
  border: 1px solid var(--signal-cyan);
  border-radius: 4px;
  background: rgba(56, 189, 248, 0.22);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-send:hover:not(:disabled) {
  background: rgba(56, 189, 248, 0.35);
}

.btn-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.dropdown-fade-enter-from,
.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ----------------------------------------------------
   5. 窄屏移动端自适应 (<= 860px)
   ---------------------------------------------------- */
@media (max-width: 860px) {
  .immersive-page {
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    height: auto;
    min-height: 100vh;
  }

  .immersive-stage {
    height: auto;
    min-height: 100vh;
    overflow: visible;
    border-radius: 0;
  }

  .immersive-below {
    position: relative;
    z-index: 2;
    margin-top: 0;
    padding: 0 10px 16px;
  }

  .ib-warning-panel {
    position: static;
    width: 100%;
    max-width: none;
    min-width: 0;
    top: auto;
    bottom: auto;
  }

  .ib-warning-stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .immersive-bg {
    position: fixed;
    inset: 0;
    z-index: 1;
  }

  .immersive-overlay {
    position: relative;
    inset: auto;
    z-index: 2;
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 10px;
  }

  .glass-bar--top {
    position: static;
    height: auto;
    padding: 8px 10px;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .top-title-group {
    flex-wrap: wrap;
  }

  .top-right {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }

  .freshness-tag {
    justify-content: space-between;
  }

  .glass-island--left-top,
  .glass-island--left-bottom {
    position: static;
    width: auto;
    bottom: auto;
    padding: 8px;
    gap: 8px;
  }

  .telemetry-compact {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: space-between;
  }

  .contact-actions-compact {
    grid-template-columns: 1fr 1fr 1fr;
    gap: 4px;
  }

  .c-btn {
    font-size: 9.5px;
    padding: 2px 2px;
  }

  .glass-controls {
    position: static;
    height: auto;
    padding: 8px 10px;
    justify-content: space-between;
    flex-wrap: wrap;
  }

}
</style>
