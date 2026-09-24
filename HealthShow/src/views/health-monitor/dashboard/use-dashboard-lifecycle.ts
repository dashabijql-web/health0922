import { nextTick, onActivated, onBeforeUnmount, onMounted, type Ref } from 'vue'
import dayjs from 'dayjs'
import { createEventBinding, createIntervalTask, createTimeoutTask } from '@/utils/task-timer'
import type { DashboardState } from './dashboard-state'
import type { useDashboardCharts } from './use-dashboard-charts'
import type { useDashboardData } from './use-dashboard-data'
import type { useMineAi } from './use-mine-ai'

const CLOCK_MS = 1000
const REFRESH_MS = 120_000
const KPI_REFRESH_MS = 30_000
const REFRESH_TEXT_MS = 5000
const RESIZE_DEBOUNCE_MS = 200

/**
 * 统一管控页的生命周期：时钟与定时刷新、窗口缩放/标签页可见性/全屏监听、卸载时释放图表。
 * 组合函数内部自己注册 onMounted / onActivated / onBeforeUnmount。
 */
export function useDashboardLifecycle(
  state: DashboardState,
  deps: {
    refs: { dmScale: Ref<HTMLElement | null>; dmBody: Ref<HTMLElement | null> }
    data: Pick<ReturnType<typeof useDashboardData>, 'fetchData' | 'fetchKpiData' | 'updateRefreshText'>
    charts: ReturnType<typeof useDashboardCharts>
    mineAi: Pick<ReturnType<typeof useMineAi>, 'loadMineAiCache'>
  }
) {
  const { dmScale, dmBody } = deps.refs
  const { fetchData, fetchKpiData, updateRefreshText } = deps.data
  const { initHourDistChart, initUnifiedTrendChart, initWarnTypeChart, initWarningTrend7dChart } = deps.charts

  const updateTime = () => { state.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss') }

  const timeTask = createIntervalTask(updateTime, CLOCK_MS)
  const refreshTask = createIntervalTask(() => fetchData(), REFRESH_MS)
  // 注意：KPI 定时刷新和“更新于…”文案定时刷新只在标签页从隐藏切回可见时才会启动，挂载时不启动（沿用重构前行为）
  const kpiRefreshTask = createIntervalTask(() => fetchKpiData(), KPI_REFRESH_MS)
  const refreshTextTask = createIntervalTask(() => updateRefreshText(), REFRESH_TEXT_MS)
  const resizeTask = createTimeoutTask(resizeCharts, RESIZE_DEBOUNCE_MS)

  function resizeCharts() {
    void nextTick(() => {
      Object.values(state.charts as Record<string, { resize?: () => void } | null>).forEach((chart) => chart?.resize?.())
    })
  }

  function onVisibilityChange() {
    if (document.hidden) {
      timeTask.stop()
      refreshTask.stop()
      kpiRefreshTask.stop()
      refreshTextTask.stop()
    } else {
      void fetchData(true)
      timeTask.start()
      refreshTask.start()
      kpiRefreshTask.start()
      refreshTextTask.start()
    }
  }

  const resizeBinding = createEventBinding(() => window, 'resize', () => resizeTask.start())
  const visibilityBinding = createEventBinding(() => document, 'visibilitychange', onVisibilityChange)
  const fullscreenBinding = createEventBinding(() => document, 'fullscreenchange', () => {
    state.isFullscreen = !!document.fullscreenElement
  })

  function toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
    } else {
      document.exitFullscreen().catch(() => {})
    }
  }

  function resetScroll() {
    void nextTick(() => {
      window.scrollTo?.(0, 0)
      document.documentElement.scrollTop = 0
      document.body.scrollTop = 0
      const body = dmBody.value
      body?.scrollTo?.({ top: 0, left: 0, behavior: 'auto' })
      if (body) body.scrollTop = 0
      if (dmScale.value) dmScale.value.scrollTop = 0
    })
  }

  function resetScale() {
    const el = dmScale.value
    if (!el) return
    el.style.transform = ''
    el.style.width = ''
    el.style.height = ''
    el.style.transformOrigin = ''
  }

  const disposeChart = (chart: { dispose: () => void } | null | undefined) => { if (chart) chart.dispose() }

  onMounted(() => {
    resetScroll()
    updateTime()
    timeTask.start()
    void nextTick(() => {
      initHourDistChart()
      initUnifiedTrendChart()
      initWarnTypeChart()
      initWarningTrend7dChart()
    })
    void fetchData()
    void deps.mineAi.loadMineAiCache()
    refreshTask.start()
    resizeBinding.start()
    visibilityBinding.start()
    fullscreenBinding.start()
    if ('Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission()
    }
  })

  onActivated(() => {
    resetScroll()
    void fetchData()
    resizeCharts()
  })

  onBeforeUnmount(() => {
    state._chartRetryToken += 1
    timeTask.stop()
    refreshTask.stop()
    kpiRefreshTask.stop()
    refreshTextTask.stop()
    resizeTask.stop()
    resizeBinding.stop()
    visibilityBinding.stop()
    fullscreenBinding.stop()

    Object.values(state.charts || {}).forEach(disposeChart)
    disposeChart(state.empDrawer.trendChart)
    disposeChart(state.empDrawer.radarChart)
    disposeChart(state.warnCurveModal?.chart)
    state.empDrawer.trendChart = null
    state.empDrawer.radarChart = null
    Object.keys(state.charts).forEach((key) => { state.charts[key] = null })
    resetScale()
  })

  return { toggleFullscreen }
}
