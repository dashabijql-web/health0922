import { buildDeptChartOption, buildDeptPersonChartOption } from './dashboard-chart-options'
import { fetchDashboardDeptPersonChartData } from './dashboard-chart-data'
import { bindDashboardChart } from './dashboard-chart-methods'

const CHART_RETRY_LIMIT = 12
const CHART_RETRY_MS = 160

function formatDayRange(daysBack = 6) {
  const today = new Date()
  const start = new Date(today)
  start.setDate(start.getDate() - daysBack)
  const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return [fmt(start), fmt(today)]
}

export const dashboardViewActions: LegacyVueOptions = {
  goToEmployeeProfile(item) {
    const code = item?.userCode || item?.empCode
    if (!code) return
    this.$router.push({
      name: 'ImmersiveBody',
      query: {
        empCode: code,
        empName: item.userName || item.empName || '',
        deptName: item.deptName || '',
        jobTypeName: item.jobTypeName || '',
        phone: item.phone || '',
        imei: item.imei || '',
        from: this.$route.fullPath
      }
    })
  },

  openDepartmentDrawer(event) {
    if (!event?.deptName || event.deptName === '--') return
    this.currentDepartment = event.deptName
    this.departmentDrawerVisible = true
  },

  findWarningEventByLocator(locator) {
    if (!locator) return null
    return (this.warningEvents || []).find((item) =>
      String(item.id) === String(locator.id) && item.occurredAt === locator.occurredAt) || null
  },

  handleDepartmentShowEvent(deptEvent) {
    const original = this.findWarningEventByLocator(deptEvent)
    if (original) this.openCommandIncident(original)
  },

  handleDepartmentHandleEvent(deptEvent) {
    const original = this.findWarningEventByLocator(deptEvent)
    if (original) this.openHandleDialog(original)
  },

  handleDepartmentShowProfile(deptEvent) {
    const original = this.findWarningEventByLocator(deptEvent)
    if (original) this.goToEmployeeProfile(original)
  },

  openDeptPersonModal() {
    this.deptPersonModal.dateRange = formatDayRange()
    this.deptPersonModal.visible = true
  },

  async loadDeptPersonChart(elArg) {
    const modal = this.deptPersonModal
    modal.loading = true
    const [startTime, endTime] = modal.dateRange
    try {
      const chartData = await fetchDashboardDeptPersonChartData({ startTime, endTime })
      if (!chartData) return
      await this.$nextTick()
      const el = elArg || this.$refs.deptPersonChartRef
      if (!el) return
      bindDashboardChart(modal, 'chart', el)
      modal.chart.setOption(buildDeptPersonChartOption(chartData))
    } finally {
      modal.loading = false
    }
  },

  switchPeriod(val) {
    if (this.activePeriod === val) return
    this.activePeriod = val
    // Clear period-scoped values immediately. Otherwise the old period can
    // remain visible under the new label while its request is in flight.
    this.checkData = {}
    this.personCounts = {}
    this.bodyIndicators = {}
    this.top5Data = []
    this.deptDataList = []
    this.deptPersonStatsList = []
    this.warningRates = []
    this.deviceStats = { total: 0, activeRate: 0, usageRate: 0, warningRate: 0 }
    this.warningTypesData = []
    this.trendDailyData = []
    this.warningDistData = { labels: [], counts: [] }
    this.commandSummary = null
    this.kpiTodayWarnings = 0
    this.kpiYesterdayWarnings = 0
    this.kpiUnhandledHigh = 0
    this.kpiCriticalTotal = 0
    this.kpiMidTotal = 0
    this.kpiLowTotal = 0
    this.onDutyStats.onDuty = 0
    this.onDutyStats.offDuty = 0
    this.dashboardDataState = 'loading'
    this.dashboardDataError = ''
    this.dashboardMissingSections = []
    this.lastRefreshTime = null
    this.lastRefreshText = '加载中...'
    this.$nextTick(() => {
      this.initHourDistChart()
      this.initWarnTypeChart()
      this.initUnifiedTrendChart()
      this.initDeptChart()
      this.initDeviceCharts()
    })
    // force=true intentionally supersedes an older period request.
    return this.fetchData(true)
  },

  getReadyChartDom(target, retryFn) {
    const dom = typeof target === 'string' ? document.getElementById(target) : target
    if (!dom) return null
    const retryKey = typeof target === 'string' ? target : (dom.id || retryFn?.name || 'chart')
    if (dom.clientWidth > 0 && dom.clientHeight > 0) {
      if (this._chartRetryCounts) this._chartRetryCounts[retryKey] = 0
      return dom
    }
    if (!retryFn) return null
    const count = this._chartRetryCounts?.[retryKey] || 0
    if (count >= CHART_RETRY_LIMIT) return null
    if (this._chartRetryCounts) this._chartRetryCounts[retryKey] = count + 1
    const token = this._chartRetryToken
    setTimeout(() => {
      if (this._chartRetryToken !== token) return
      retryFn.call(this)
    }, CHART_RETRY_MS)
    return null
  },

  initDeptChart() {
    const dom = this.getReadyChartDom('deptDataChart', this.initDeptChart)
    if (!dom) return
    const chart = bindDashboardChart(this.charts, 'dept', dom)
    chart.setOption(buildDeptChartOption({
      deptStatsList: this.deptPersonStatsList,
      deptDataList: this.deptDataList,
      riskDeptList: this.riskDeptList
    }), true)
    chart.off('click')
    chart.on('click', params => {
      if (params.name) this.openDeptDetailModal(params.name)
    })
    chart.getZr().setCursorStyle('pointer')
    this.$nextTick(() => { chart.resize() })
  }
}
