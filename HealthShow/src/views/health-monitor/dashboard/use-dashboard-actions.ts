import { nextTick } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import type { DashboardState } from './dashboard-state'
import type { useDashboardCharts } from './use-dashboard-charts'
import type { useDashboardData } from './use-dashboard-data'
import type { useDashboardDetail } from './use-dashboard-detail'
import type { useDashboardWorkflow } from './use-dashboard-workflow'

/** 页面级操作：跳转人员画像/设备列表、打开部门抽屉、部门抽屉里的事件联动、切换统计周期 */
export function useDashboardActions(
  state: DashboardState,
  deps: {
    router: Router
    route: RouteLocationNormalizedLoaded
    data: Pick<ReturnType<typeof useDashboardData>, 'fetchData'>
    charts: Pick<ReturnType<typeof useDashboardCharts>, 'initHourDistChart' | 'initWarnTypeChart' | 'initUnifiedTrendChart'>
    workflow: Pick<ReturnType<typeof useDashboardWorkflow>, 'openCommandIncident'>
    detail: Pick<ReturnType<typeof useDashboardDetail>, 'openHandleDialog'>
  }
) {
  const { router, route } = deps
  const { fetchData } = deps.data
  const { initHourDistChart, initWarnTypeChart, initUnifiedTrendChart } = deps.charts
  const { openCommandIncident } = deps.workflow
  const { openHandleDialog } = deps.detail

  function goToEmployeeProfile(item) {
    const code = item?.userCode || item?.empCode
    if (!code) return
    router.push({
      name: 'ImmersiveBody',
      query: {
        empCode: code,
        empName: item.userName || item.empName || '',
        deptName: item.deptName || '',
        jobTypeName: item.jobTypeName || '',
        phone: item.phone || '',
        imei: item.imei || '',
        from: route.fullPath
      }
    })
  }

  function openDepartmentDrawer(event) {
    if (!event?.deptName || event.deptName === '--') return
    state.currentDepartment = event.deptName
    state.departmentDrawerVisible = true
  }

  function findWarningEventByLocator(locator) {
    if (!locator) return null
    return (state.warningEvents || []).find((item) =>
      String(item.id) === String(locator.id) && item.occurredAt === locator.occurredAt) || null
  }

  function handleDepartmentShowEvent(deptEvent) {
    const original = findWarningEventByLocator(deptEvent)
    if (original) openCommandIncident(original)
  }

  function handleDepartmentHandleEvent(deptEvent) {
    const original = findWarningEventByLocator(deptEvent)
    if (original) openHandleDialog(original)
  }

  function handleDepartmentShowProfile(deptEvent) {
    const original = findWarningEventByLocator(deptEvent)
    if (original) goToEmployeeProfile(original)
  }

  function goToDeviceList(card) {
    router.push(card.route)
  }

  function switchPeriod(val) {
    if (state.activePeriod === val) return
    state.activePeriod = val
    // Clear period-scoped values immediately. Otherwise the old period can
    // remain visible under the new label while its request is in flight.
    state.checkData = {}
    state.personCounts = {}
    state.bodyIndicators = {}
    state.top5Data = []
    state.deptDataList = []
    state.deptPersonStatsList = []
    state.warningRates = []
    state.deviceStats = { total: 0, activeRate: 0, usageRate: 0, warningRate: 0 }
    state.warningTypesData = []
    state.trendDailyData = []
    state.warningDistData = { labels: [], counts: [] }
    state.commandSummary = null
    state.kpiTodayWarnings = 0
    state.kpiYesterdayWarnings = 0
    state.kpiUnhandledHigh = 0
    state.kpiCriticalTotal = 0
    state.kpiMidTotal = 0
    state.kpiLowTotal = 0
    state.dashboardDataState = 'loading'
    state.dashboardDataError = ''
    state.dashboardMissingSections = []
    state.lastRefreshTime = null
    state.lastRefreshText = '加载中...'
    nextTick(() => {
      initHourDistChart()
      initWarnTypeChart()
      initUnifiedTrendChart()
    })
    // force=true intentionally supersedes an older period request.
    return fetchData(true)
  }

  return {
    goToEmployeeProfile,
    openDepartmentDrawer,
    handleDepartmentShowEvent,
    handleDepartmentHandleEvent,
    handleDepartmentShowProfile,
    goToDeviceList,
    switchPeriod
  }
}
