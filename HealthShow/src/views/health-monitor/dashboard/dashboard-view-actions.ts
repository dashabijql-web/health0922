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

  goToDeviceList(card) {
    this.$router.push(card.route)
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
    this.dashboardDataState = 'loading'
    this.dashboardDataError = ''
    this.dashboardMissingSections = []
    this.lastRefreshTime = null
    this.lastRefreshText = '加载中...'
    this.$nextTick(() => {
      this.initHourDistChart()
      this.initWarnTypeChart()
      this.initUnifiedTrendChart()
    })
    // force=true intentionally supersedes an older period request.
    return this.fetchData(true)
  }
}
