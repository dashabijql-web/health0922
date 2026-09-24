import { nextTick } from 'vue'
import { fetchDashboardWarningTrend7dData } from './dashboard-chart-data'
import {
  buildDashboardLevelTotals,
  buildDashboardUnhandledStats,
  collectDashboardNewDangerEvents,
  fetchCommandCenterDashboardSummary,
  fetchDashboardKpiSnapshot,
  fetchDashboardWarningEventState,
  fetchUnifiedControlSnapshot,
  formatDashboardRefreshText
} from './dashboard-runtime-data'
import type { DashboardState } from './dashboard-state'
import type { useDashboardCharts } from './use-dashboard-charts'
import type { useDashboardComputed } from './use-dashboard-computed'

const DASHBOARD_SECTION_LABELS = {
  overview: '概览',
  bodyIndicators: '体征均值',
  deviceActivation: '设备状态',
  top5: '风险排名',
  personCounts: '覆盖人数',
  preShift: '入井健康准入',
  deptHealthCounts: '部门统计',
  deptPersonStats: '部门人员统计',
  dailyTrend: '趋势数据',
  warningDistribution: '预警分布',
  warningTypes: '预警类型',
  healthSnapshot: '实时快照',
  commandSummary: '处置摘要'
}

/**
 * 统一管控页的数据加载：整体快照、KPI、指挥摘要、预警事件流、刷新状态文案。
 * 请求序号 _dashboardRequestSeq 用来丢弃过期响应：切换周期后，旧周期的响应不能覆盖新周期。
 */
export function useDashboardData(
  state: DashboardState,
  deps: {
    computed: Pick<ReturnType<typeof useDashboardComputed>, 'periodRange'>
    charts: ReturnType<typeof useDashboardCharts>
  }
) {
  const periodRangeRef = deps.computed.periodRange
  const { initHourDistChart, initWarnTypeChart, initUnifiedTrendChart, initWarningTrend7dChart } = deps.charts

  function triggerDangerNotification(event) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    const typeNames = { heartRate: '心率异常', bloodOxygen: '血氧偏低', temperature: '体温异常', pressure: '压力异常', SOS: 'SOS求助', fall: '跌倒' }
    const typeName = typeNames[event.type] || event.type || '健康预警'
    const n = new Notification(`高危预警：${event.userName || '未知人员'}`, {
      body: `${typeName}  ${event.value || ''}  —  请立即处理`,
      icon: '/favicon.ico',
      tag: `alert-${event.id}`
    })
    n.onclick = () => { window.focus(); n.close() }
  }

  async function fetchData(force = false) {
    // A period switch must be able to supersede an in-flight request. The
    // request sequence below prevents the older response from being applied
    // after the user has already selected another period.
    if (state.isRefreshing && !force) return
    const requestSeq = (state._dashboardRequestSeq || 0) + 1
    state._dashboardRequestSeq = requestSeq
    const activePeriod = state.activePeriod
    const periodRange = { ...periodRangeRef.value }
    const hadUsableData = state.dashboardDataState === 'ready'
      || state.dashboardDataState === 'partial'
      || state.dashboardDataState === 'stale'
    state.isRefreshing = true
    if (state.dashboardDataState === 'idle') state.dashboardDataState = 'loading'
    state.dashboardDataError = ''
    try {
      const snapshot = await fetchUnifiedControlSnapshot(periodRange, activePeriod)
      if (requestSeq !== state._dashboardRequestSeq || activePeriod !== state.activePeriod) return
      if (!snapshot) {
        state.dashboardDataState = hadUsableData ? 'stale' : 'error'
        state.dashboardDataError = hadUsableData
          ? '本次刷新失败，当前显示上次成功数据'
          : '统计数据加载失败，请点击刷新重试'
        if (!hadUsableData) state.lastRefreshText = '更新失败'
        return
      }

      applyUnifiedControlSnapshot(snapshot)
      const snapshotState = state.dashboardDataState
      // Fixed 7-day view, independent of activePeriod — fetch and render on
      // its own so it doesn't block the rest of the refresh cycle.
      fetchWarningTrend7d().then(() => nextTick(() => initWarningTrend7dChart()))
      await fetchWarningEvents(requestSeq)
      if (requestSeq !== state._dashboardRequestSeq || activePeriod !== state.activePeriod) return
      // Warning loading must not turn a partial aggregate snapshot into a
      // successful refresh. Keep the aggregate result's state and timestamp
      // semantics intact.
      if (snapshotState === 'ready') {
        state.lastRefreshTime = Date.now()
        updateRefreshText()
      } else if (snapshotState === 'partial') {
        state.dashboardDataState = 'partial'
        state.lastRefreshText = '部分更新'
      }
      nextTick(() => {
        initHourDistChart()
        initWarnTypeChart()
        initUnifiedTrendChart()
      })
    } catch {
      if (requestSeq !== state._dashboardRequestSeq || activePeriod !== state.activePeriod) return
      state.dashboardDataState = hadUsableData ? 'stale' : 'error'
      state.dashboardDataError = hadUsableData
        ? '本次刷新失败，当前显示上次成功数据'
        : '统计数据加载失败，请点击刷新重试'
      if (!hadUsableData) state.lastRefreshText = '更新失败'
    } finally {
      if (requestSeq === state._dashboardRequestSeq) state.isRefreshing = false
    }
  }

  async function fetchKpiData() {
    if (state.isRefreshing) return
    const requestSeq = state._dashboardRequestSeq
    const activePeriod = state.activePeriod
    const [snapshot] = await Promise.all([
      fetchDashboardKpiSnapshot(state.warningEvents, state.personCounts?.totalPersons || state.deviceStats.total),
      fetchCommandSummary(activePeriod, requestSeq)
    ])
    if (requestSeq !== state._dashboardRequestSeq || activePeriod !== state.activePeriod) return
    state.kpiRealtimeOnline = snapshot.kpiRealtimeOnline
    state.kpiRealtimeTotal = snapshot.kpiRealtimeTotal
    state.kpiTodayWarnings = state.commandSummary?.warning?.periodNew ?? snapshot.kpiTodayWarnings
    state.kpiYesterdayWarnings = snapshot.kpiYesterdayWarnings
    state.kpiUnhandledHigh = state.commandSummary?.warning?.criticalPending ?? snapshot.kpiUnhandledHigh
    state.kpiCriticalTotal = state.commandSummary?.warning?.criticalTotal ?? snapshot.kpiCriticalTotal
    state.kpiMidTotal = state.commandSummary?.warning?.midTotal ?? snapshot.kpiMidTotal
    state.kpiLowTotal = state.commandSummary?.warning?.lowTotal ?? snapshot.kpiLowTotal
  }

  async function fetchCommandSummary(period = state.activePeriod, requestSeq?: number) {
    const currentRequestSeq = requestSeq ?? state._dashboardRequestSeq
    const summary = await fetchCommandCenterDashboardSummary(period)
    if (!summary) return
    if (currentRequestSeq !== state._dashboardRequestSeq || period !== state.activePeriod) return
    applyCommandSummary(summary)
  }

  function applyCommandSummary(summary) {
    state.commandSummary = summary
    state.kpiTodayWarnings = summary.warning?.periodNew ?? state.kpiTodayWarnings
    state.kpiUnhandledHigh = summary.warning?.criticalPending ?? state.kpiUnhandledHigh
    state.kpiCriticalTotal = summary.warning?.criticalTotal ?? state.kpiCriticalTotal
    state.kpiMidTotal = summary.warning?.midTotal ?? state.kpiMidTotal
    state.kpiLowTotal = summary.warning?.lowTotal ?? state.kpiLowTotal
    state.preShiftData = {
      ...state.preShiftData,
      qualifiedCount: summary.admission?.passed ?? state.preShiftData.qualifiedCount,
      failedCount: summary.admission?.prohibited ?? state.preShiftData.failedCount
    }
    if (summary.device) {
      state.deviceStats = {
        ...state.deviceStats,
        total: summary.device.total ?? state.deviceStats.total,
        boundDevices: summary.device.total ?? state.deviceStats.boundDevices,
        activeRate: summary.device.onlineRate ?? state.deviceStats.activeRate
      }
    }
  }

  function applyUnifiedControlSnapshot(snapshot) {
    const missingSections = Array.isArray(snapshot._missingSections) ? snapshot._missingSections : []
    const isMissing = (section) => missingSections.includes(section)
    state.dashboardMissingSections = missingSections
    if (missingSections.length) {
      state.dashboardDataState = 'partial'
      const labels = missingSections.map((section) => DASHBOARD_SECTION_LABELS[section] || section)
      state.dashboardDataError = `部分统计加载失败，缺失项显示为 --（${labels.join('、')}）`
    } else {
      state.dashboardDataState = 'ready'
      state.dashboardDataError = ''
    }
    if (isMissing('overview')) state.checkData = {}
    else if (snapshot.overview) state.checkData = snapshot.overview
    if (isMissing('bodyIndicators')) state.bodyIndicators = {}
    else if (snapshot.bodyIndicators) state.bodyIndicators = snapshot.bodyIndicators
    if (isMissing('deviceActivation')) {
      state.deviceStats = { total: 0, activeRate: 0, usageRate: 0, warningRate: 0 }
      state.warningRates = []
    } else if (snapshot.deviceActivation) {
      state.deviceStats = snapshot.deviceActivation.stats || state.deviceStats
      state.warningRates = snapshot.deviceActivation.warningRates || []
    }
    if (isMissing('top5')) state.top5Data = []
    else if (snapshot.top5) state.top5Data = snapshot.top5
    if (isMissing('personCounts')) state.personCounts = {}
    else if (snapshot.personCounts) state.personCounts = snapshot.personCounts
    if (isMissing('preShift')) {
      state.preShiftData = { totalToday: null, qualifiedCount: null, failedCount: null, preShiftRate: null }
    } else if (snapshot.preShift) state.preShiftData = snapshot.preShift
    if (isMissing('deptHealthCounts')) state.deptDataList = []
    else if (snapshot.deptHealthCounts) state.deptDataList = snapshot.deptHealthCounts
    if (isMissing('deptPersonStats')) state.deptPersonStatsList = []
    else if (snapshot.deptPersonStats) state.deptPersonStatsList = snapshot.deptPersonStats
    if (isMissing('dailyTrend')) state.trendDailyData = []
    else if (snapshot.dailyTrend) state.trendDailyData = snapshot.dailyTrend
    if (isMissing('warningDistribution')) state.warningDistData = { labels: [], counts: [] }
    else if (snapshot.warningDistribution) state.warningDistData = snapshot.warningDistribution
    if (isMissing('warningTypes')) state.warningTypesData = []
    else if (snapshot.warningTypes) state.warningTypesData = snapshot.warningTypes
    if (isMissing('healthSnapshot')) {
      state.healthSnapshot = null
      state.kpiRealtimeOnline = 0
    }
    else if (snapshot.healthSnapshot) {
      state.healthSnapshot = snapshot.healthSnapshot
      state.kpiRealtimeOnline = snapshot.healthSnapshot.onlineUsers ?? state.kpiRealtimeOnline
    }
    state.kpiRealtimeTotal = isMissing('personCounts') && isMissing('deviceActivation')
      ? 0
      : (snapshot.personCounts?.totalPersons ?? snapshot.deviceActivation?.stats?.total ?? state.kpiRealtimeTotal)
    if (isMissing('commandSummary')) {
      state.commandSummary = null
      state.kpiTodayWarnings = 0
      state.kpiYesterdayWarnings = 0
      state.kpiUnhandledHigh = 0
      state.kpiCriticalTotal = 0
      state.kpiMidTotal = 0
      state.kpiLowTotal = 0
    }
    else if (snapshot.commandSummary) applyCommandSummary(snapshot.commandSummary)
  }

  function updateRefreshText() {
    if (state.dashboardDataState === 'partial') {
      state.lastRefreshText = '部分更新'
      return
    }
    if (state.dashboardDataState === 'error' && !state.lastRefreshTime) {
      state.lastRefreshText = '更新失败'
      return
    }
    if (state.dashboardDataState === 'stale' && !state.lastRefreshTime) {
      state.lastRefreshText = '更新失败'
      return
    }
    state.lastRefreshText = formatDashboardRefreshText(state.lastRefreshTime)
  }

  async function fetchWarningTrend7d() {
    state.warningTrend7dData = await fetchDashboardWarningTrend7dData()
  }

  async function fetchWarningEvents(requestSeq?: number) {
    const currentRequestSeq = requestSeq ?? state._dashboardRequestSeq
    const { events: warningEvents, totals } = await fetchDashboardWarningEventState()
    if (currentRequestSeq !== state._dashboardRequestSeq) return
    state.warningEvents = warningEvents
    state.warningStreamTotals = totals
    const unhandled = buildDashboardUnhandledStats(state.warningEvents)
    const levelTotals = buildDashboardLevelTotals(state.warningEvents)
    if (!state.commandSummary) {
      state.kpiUnhandledHigh = unhandled.kpiUnhandledHigh
      state.kpiCriticalTotal = levelTotals.kpiCriticalTotal
      state.kpiMidTotal = levelTotals.kpiMidTotal
      state.kpiLowTotal = levelTotals.kpiLowTotal
    }
    collectDashboardNewDangerEvents(state.warningEvents, state.seenAlertIds).forEach((event) => {
      triggerDangerNotification(event)
    })
  }

  return {
    triggerDangerNotification,
    fetchData,
    fetchKpiData,
    updateRefreshText,
    fetchWarningTrend7d,
    fetchWarningEvents
  }
}
