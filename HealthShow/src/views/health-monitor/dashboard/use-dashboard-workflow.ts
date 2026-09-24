import type { Router } from 'vue-router'
import type { DashboardState } from './dashboard-state'
import type { useDashboardData } from './use-dashboard-data'

/** 指挥处置入口：准入名单跳转、打开事件抽屉（含从路由参数还原）、处置后刷新 */
export function useDashboardWorkflow(
  state: DashboardState,
  deps: { router: Router; data: Pick<ReturnType<typeof useDashboardData>, 'fetchWarningEvents' | 'fetchKpiData'> }
) {
  const { router } = deps
  const { fetchWarningEvents, fetchKpiData } = deps.data

  function openAdmissionQueue(item) {
    if (item?.route) router.push(item.route)
  }

  function openCommandIncident(event) {
    if (!event?.id || !event?.occurredAt) return
    state.currentIncidentEvent = event
    state.incidentDrawerVisible = true
  }

  function openIncidentFromRoute(query) {
    if (!query?.warningId || !query?.occurredAt) return
    const key = `${query.warningId}:${query.occurredAt}`
    if (state.routeIncidentKey === key) return
    state.routeIncidentKey = key
    openCommandIncident({
      id: query.warningId,
      incidentId: query.incidentId || '',
      occurredAt: query.occurredAt,
      userName: query.person || '',
      location: query.area || ''
    })
  }

  async function handleIncidentUpdated() {
    await Promise.all([fetchWarningEvents(), fetchKpiData()])
  }

  return { openAdmissionQueue, openCommandIncident, openIncidentFromRoute, handleIncidentUpdated }
}
