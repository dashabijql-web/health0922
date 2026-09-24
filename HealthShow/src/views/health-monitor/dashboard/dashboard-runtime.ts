import dayjs from 'dayjs'
import { getMineAiReport, generateMineAiReport } from '@/api/ai'
import {
  buildDashboardLevelTotals,
  buildDashboardUnhandledStats,
  collectDashboardNewDangerEvents,
  fetchDashboardBodyIndicatorData,
  fetchDashboardHealthSnapshot,
  fetchCommandCenterDashboardSummary,
  fetchDashboardDeptState,
  fetchDashboardDeviceState,
  fetchDashboardKpiSnapshot,
  fetchDashboardOverviewData,
  fetchDashboardPersonCountData,
  fetchDashboardPreShiftData,
  fetchDashboardTop5Data,
  fetchDashboardWarningEventState,
  fetchUnifiedControlSnapshot,
  formatDashboardRefreshText
} from './dashboard-runtime-data'

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

export const dashboardRuntimeMethods: LegacyVueOptions = {
  async handleMineAi(force = false) {
    if (this.mineAiLoading) return
    this.mineAiLoading = true
    try {
      const res = await generateMineAiReport(force)
      if (res.code === 200 && res.data) {
        this.mineAiReport = res.data.reportContent
        this.mineAiTime = res.data.generateTime
        this.mineAiDialogVisible = true
        this.$message.success('全矿 AI 分析完成')
      } else {
        this.$message.error(res.message || '生成失败')
      }
    } catch {
      this.$message.error('AI 服务暂时不可用，请稍后重试')
    } finally {
      this.mineAiLoading = false
    }
  },

  async loadMineAiCache() {
    try {
      const res = await getMineAiReport()
      if (res.code === 200 && res.data) {
        this.mineAiReport = res.data.reportContent
        this.mineAiTime = res.data.generateTime
      }
    } catch {}
  },

  toggleMineAiPanel() {
    if (this.mineAiReport) {
      this.mineAiDialogVisible = true
      return
    }
    return this.handleMineAi(false)
  },

  initTime() {
    this.updateTime()
    this._timeTask?.start()
  },

  updateTime() {
    this.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  },

  toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
    } else {
      document.exitFullscreen().catch(() => {})
    }
  },

  triggerDangerNotification(event) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    const typeNames = { heartRate: '心率异常', bloodOxygen: '血氧偏低', temperature: '体温异常', pressure: '压力异常', SOS: 'SOS求助', fall: '跌倒' }
    const typeName = typeNames[event.type] || event.type || '健康预警'
    const n = new Notification(`高危预警：${event.userName || '未知人员'}`, {
      body: `${typeName}  ${event.value || ''}  —  请立即处理`,
      icon: '/favicon.ico',
      tag: `alert-${event.id}`
    })
    n.onclick = () => { window.focus(); n.close() }
  },

  async fetchData(force = false) {
    // A period switch must be able to supersede an in-flight request. The
    // request sequence below prevents the older response from being applied
    // after the user has already selected another period.
    if (this.isRefreshing && !force) return
    const requestSeq = (this._dashboardRequestSeq || 0) + 1
    this._dashboardRequestSeq = requestSeq
    const activePeriod = this.activePeriod
    const periodRange = { ...this.periodRange }
    const hadUsableData = this.dashboardDataState === 'ready'
      || this.dashboardDataState === 'partial'
      || this.dashboardDataState === 'stale'
    this.isRefreshing = true
    if (this.dashboardDataState === 'idle') this.dashboardDataState = 'loading'
    this.dashboardDataError = ''
    try {
      const snapshot = await fetchUnifiedControlSnapshot(periodRange, activePeriod)
      if (requestSeq !== this._dashboardRequestSeq || activePeriod !== this.activePeriod) return
      if (!snapshot) {
        this.dashboardDataState = hadUsableData ? 'stale' : 'error'
        this.dashboardDataError = hadUsableData
          ? '本次刷新失败，当前显示上次成功数据'
          : '统计数据加载失败，请点击刷新重试'
        if (!hadUsableData) this.lastRefreshText = '更新失败'
        return
      }

      this.applyUnifiedControlSnapshot(snapshot)
      const snapshotState = this.dashboardDataState
      // Fixed 7-day view, independent of activePeriod — fetch and render on
      // its own so it doesn't block the rest of the refresh cycle.
      this.fetchWarningTrend7d().then(() => this.$nextTick(() => this.initWarningTrend7dChart()))
      await this.fetchWarningEvents(requestSeq)
      if (requestSeq !== this._dashboardRequestSeq || activePeriod !== this.activePeriod) return
      // Warning loading must not turn a partial aggregate snapshot into a
      // successful refresh. Keep the aggregate result's state and timestamp
      // semantics intact.
      if (snapshotState === 'ready') {
        this.lastRefreshTime = Date.now()
        this.updateRefreshText()
      } else if (snapshotState === 'partial') {
        this.dashboardDataState = 'partial'
        this.lastRefreshText = '部分更新'
      }
      this.$nextTick(() => {
        this.initHourDistChart()
        this.initWarnTypeChart()
        this.initUnifiedTrendChart()
        this.initDeptChart()
        this.initDeviceCharts()
      })
    } catch {
      if (requestSeq !== this._dashboardRequestSeq || activePeriod !== this.activePeriod) return
      this.dashboardDataState = hadUsableData ? 'stale' : 'error'
      this.dashboardDataError = hadUsableData
        ? '本次刷新失败，当前显示上次成功数据'
        : '统计数据加载失败，请点击刷新重试'
      if (!hadUsableData) this.lastRefreshText = '更新失败'
    } finally {
      if (requestSeq === this._dashboardRequestSeq) this.isRefreshing = false
    }
  },

  async fetchKpiData() {
    if (this.isRefreshing) return
    const requestSeq = this._dashboardRequestSeq
    const activePeriod = this.activePeriod
    const [snapshot] = await Promise.all([
      fetchDashboardKpiSnapshot(this.warningEvents, this.personCounts?.totalPersons || this.deviceStats.total),
      this.fetchCommandSummary(activePeriod, requestSeq)
    ])
    if (requestSeq !== this._dashboardRequestSeq || activePeriod !== this.activePeriod) return
    this.kpiRealtimeOnline = snapshot.kpiRealtimeOnline
    this.kpiRealtimeTotal = snapshot.kpiRealtimeTotal
    this.kpiTodayWarnings = this.commandSummary?.warning?.periodNew ?? snapshot.kpiTodayWarnings
    this.kpiYesterdayWarnings = snapshot.kpiYesterdayWarnings
    this.kpiUnhandledHigh = this.commandSummary?.warning?.criticalPending ?? snapshot.kpiUnhandledHigh
    this.kpiCriticalTotal = this.commandSummary?.warning?.criticalTotal ?? snapshot.kpiCriticalTotal
    this.kpiMidTotal = this.commandSummary?.warning?.midTotal ?? snapshot.kpiMidTotal
    this.kpiLowTotal = this.commandSummary?.warning?.lowTotal ?? snapshot.kpiLowTotal
  },

  async fetchCommandSummary(this: Record<string, any>, period = this.activePeriod, requestSeq?: number) {
    const currentRequestSeq = requestSeq ?? this._dashboardRequestSeq
    const summary = await fetchCommandCenterDashboardSummary(period)
    if (!summary) return
    if (currentRequestSeq !== this._dashboardRequestSeq || period !== this.activePeriod) return
    this.applyCommandSummary(summary)
  },

  applyCommandSummary(summary) {
    this.commandSummary = summary
    this.kpiTodayWarnings = summary.warning?.periodNew ?? this.kpiTodayWarnings
    this.kpiUnhandledHigh = summary.warning?.criticalPending ?? this.kpiUnhandledHigh
    this.kpiCriticalTotal = summary.warning?.criticalTotal ?? this.kpiCriticalTotal
    this.kpiMidTotal = summary.warning?.midTotal ?? this.kpiMidTotal
    this.kpiLowTotal = summary.warning?.lowTotal ?? this.kpiLowTotal
    this.preShiftData = {
      ...this.preShiftData,
      qualifiedCount: summary.admission?.passed ?? this.preShiftData.qualifiedCount,
      failedCount: summary.admission?.prohibited ?? this.preShiftData.failedCount
    }
    if (summary.device) {
      this.deviceStats = {
        ...this.deviceStats,
        total: summary.device.total ?? this.deviceStats.total,
        boundDevices: summary.device.total ?? this.deviceStats.boundDevices,
        activeRate: summary.device.onlineRate ?? this.deviceStats.activeRate
      }
    }
  },

  applyUnifiedControlSnapshot(snapshot) {
    const missingSections = Array.isArray(snapshot._missingSections) ? snapshot._missingSections : []
    const isMissing = (section) => missingSections.includes(section)
    this.dashboardMissingSections = missingSections
    if (missingSections.length) {
      this.dashboardDataState = 'partial'
      const labels = missingSections.map((section) => DASHBOARD_SECTION_LABELS[section] || section)
      this.dashboardDataError = `部分统计加载失败，缺失项显示为 --（${labels.join('、')}）`
    } else {
      this.dashboardDataState = 'ready'
      this.dashboardDataError = ''
    }
    if (isMissing('overview')) this.checkData = {}
    else if (snapshot.overview) this.checkData = snapshot.overview
    if (isMissing('bodyIndicators')) this.bodyIndicators = {}
    else if (snapshot.bodyIndicators) this.bodyIndicators = snapshot.bodyIndicators
    if (isMissing('deviceActivation')) {
      this.deviceStats = { total: 0, activeRate: 0, usageRate: 0, warningRate: 0 }
      this.warningRates = []
    } else if (snapshot.deviceActivation) {
      this.deviceStats = snapshot.deviceActivation.stats || this.deviceStats
      this.warningRates = snapshot.deviceActivation.warningRates || []
    }
    if (isMissing('top5')) this.top5Data = []
    else if (snapshot.top5) this.top5Data = snapshot.top5
    if (isMissing('personCounts')) this.personCounts = {}
    else if (snapshot.personCounts) this.personCounts = snapshot.personCounts
    if (isMissing('preShift')) {
      this.preShiftData = { totalToday: null, qualifiedCount: null, failedCount: null, preShiftRate: null }
    } else if (snapshot.preShift) this.preShiftData = snapshot.preShift
    if (isMissing('deptHealthCounts')) this.deptDataList = []
    else if (snapshot.deptHealthCounts) this.deptDataList = snapshot.deptHealthCounts
    if (isMissing('deptPersonStats')) this.deptPersonStatsList = []
    else if (snapshot.deptPersonStats) this.deptPersonStatsList = snapshot.deptPersonStats
    if (isMissing('dailyTrend')) this.trendDailyData = []
    else if (snapshot.dailyTrend) this.trendDailyData = snapshot.dailyTrend
    if (isMissing('warningDistribution')) this.warningDistData = { labels: [], counts: [] }
    else if (snapshot.warningDistribution) this.warningDistData = snapshot.warningDistribution
    if (isMissing('warningTypes')) this.warningTypesData = []
    else if (snapshot.warningTypes) this.warningTypesData = snapshot.warningTypes
    if (isMissing('healthSnapshot')) {
      this.healthSnapshot = null
      this.kpiRealtimeOnline = 0
    }
    else if (snapshot.healthSnapshot) {
      this.healthSnapshot = snapshot.healthSnapshot
      this.kpiRealtimeOnline = snapshot.healthSnapshot.onlineUsers ?? this.kpiRealtimeOnline
    }
    this.kpiRealtimeTotal = isMissing('personCounts') && isMissing('deviceActivation')
      ? 0
      : (snapshot.personCounts?.totalPersons ?? snapshot.deviceActivation?.stats?.total ?? this.kpiRealtimeTotal)
    if (isMissing('commandSummary')) {
      this.commandSummary = null
      this.kpiTodayWarnings = 0
      this.kpiYesterdayWarnings = 0
      this.kpiUnhandledHigh = 0
      this.kpiCriticalTotal = 0
      this.kpiMidTotal = 0
      this.kpiLowTotal = 0
    }
    else if (snapshot.commandSummary) this.applyCommandSummary(snapshot.commandSummary)
  },

  updateRefreshText() {
    if (this.dashboardDataState === 'partial') {
      this.lastRefreshText = '部分更新'
      return
    }
    if (this.dashboardDataState === 'error' && !this.lastRefreshTime) {
      this.lastRefreshText = '更新失败'
      return
    }
    if (this.dashboardDataState === 'stale' && !this.lastRefreshTime) {
      this.lastRefreshText = '更新失败'
      return
    }
    this.lastRefreshText = formatDashboardRefreshText(this.lastRefreshTime)
  },

  async fetchTop5Data() {
    this.top5Data = await fetchDashboardTop5Data(this.periodRange)
  },

  async fetchDashboardData() {
    this.checkData = await fetchDashboardOverviewData(this.periodRange)
  },

  async fetchPreShiftRate(force = false) {
    const nextPreShiftData = await fetchDashboardPreShiftData(force)
    if (nextPreShiftData) this.preShiftData = nextPreShiftData
  },

  async fetchPersonCounts() {
    this.personCounts = await fetchDashboardPersonCountData(this.periodRange)
  },

  async fetchBodyIndicators() {
    this.bodyIndicators = await fetchDashboardBodyIndicatorData(this.periodRange)
  },

  async fetchHealthSnapshot() {
    const snapshot = await fetchDashboardHealthSnapshot()
    if (snapshot) this.healthSnapshot = snapshot
  },

  async fetchDeviceData() {
    const nextState = await fetchDashboardDeviceState(this.periodRange)
    this.deviceStats = nextState.deviceStats
    this.warningRates = nextState.warningRates
  },

  async fetchWarningEvents(requestSeq?: number) {
    const currentRequestSeq = requestSeq ?? this._dashboardRequestSeq
    const { events: warningEvents, totals } = await fetchDashboardWarningEventState()
    if (currentRequestSeq !== this._dashboardRequestSeq) return
    this.warningEvents = warningEvents
    this.warningStreamTotals = totals
    const unhandled = buildDashboardUnhandledStats(this.warningEvents)
    const levelTotals = buildDashboardLevelTotals(this.warningEvents)
    if (!this.commandSummary) {
      this.kpiUnhandledHigh = unhandled.kpiUnhandledHigh
      this.kpiCriticalTotal = levelTotals.kpiCriticalTotal
      this.kpiMidTotal = levelTotals.kpiMidTotal
      this.kpiLowTotal = levelTotals.kpiLowTotal
    }
    collectDashboardNewDangerEvents(this.warningEvents, this.seenAlertIds).forEach((event) => {
      this.triggerDangerNotification(event)
    })
  },

  async loadDeptData(force = false) {
    const nextState = await fetchDashboardDeptState(this.periodRange, force)
    this.deptDataList = nextState.deptDataList
    this.deptPersonStatsList = nextState.deptPersonStatsList
  },

  startAutoRefresh() {
    this._refreshTask?.start()
  },

  onVisibilityChange() {
    if (document.hidden) {
      this._timeTask?.stop()
      this._refreshTask?.stop()
      this._kpiRefreshTask?.stop()
      this._refreshTextTask?.stop()
    } else {
      this.fetchData(true)
      this._timeTask?.start()
      this.startAutoRefresh()
      this._kpiRefreshTask?.start()
      this._refreshTextTask?.start()
    }
  },

  handleResize() {
    this._resizeTask?.start()
  }
}
