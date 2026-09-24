import { nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveCommandCenterIncident } from '@/api/command-center'
import { bindDashboardChart } from './dashboard-chart-bind'
import {
  buildEmpTrendChartOption,
  buildEmpRadarChartOption,
  buildWarnCurveChartOption
} from './dashboard-chart-options'
import {
  buildDashboardWarnCurveSeries,
  fetchDashboardEmployeeDetail,
  fetchDashboardWarnCurveState
} from './dashboard-detail-data'
import type { DashboardState } from './dashboard-state'
import type { useDashboardData } from './use-dashboard-data'

/** 人员抽屉、预警曲线弹窗、预警处理对话框 */
export function useDashboardDetail(
  state: DashboardState,
  deps: { data: Pick<ReturnType<typeof useDashboardData>, 'fetchWarningEvents' | 'fetchKpiData'> }
) {
  const { fetchWarningEvents, fetchKpiData } = deps.data

  async function openEmployeeDrawer(item) {
    const userName = item.userName || item.name || ''
    const empCode = item.empCode || item.userCode || userName

    state.empDrawer.visible = true
    state.empDrawer.loading = true
    state.empDrawer.userName = userName
    state.empDrawer.abnormalCount = item.count || 0
    state.empDrawer.data = {}
    state.empDrawer.vitals = []
    state.empDrawer.warnings = []

    try {
      const detail = await fetchDashboardEmployeeDetail(empCode, userName)
      if (!detail) return
      state.empDrawer.data = detail.profile
      state.empDrawer.vitals = detail.vitals
      state.empDrawer.warnings = detail.warnings
      nextTick(() => {
        initEmpTrendChart(detail.trend)
        initEmpRadarChart(detail.healthScores)
      })
    } catch {
      // API 失败时 drawer 仍然打开，显示"暂无数据"
    } finally {
      state.empDrawer.loading = false
    }
  }

  function initEmpTrendChart(trend) {
    const dom = document.getElementById('empTrendChart')
    if (!dom) return
    const chart = bindDashboardChart(state.empDrawer, 'trendChart', dom)
    chart.setOption(buildEmpTrendChartOption(trend))
  }

  function initEmpRadarChart(scores) {
    const dom = document.getElementById('empRadarChart')
    if (!dom) return
    const chart = bindDashboardChart(state.empDrawer, 'radarChart', dom)
    chart.setOption(buildEmpRadarChartOption(scores))
  }

  function openHandleDialog(ev) {
    state.handleDialog.event = ev
    state.handleDialog.remark = ''
    state.handleDialog.visible = true
    state.handleDialog.submitting = false
  }

  async function submitHandle() {
    const event = state.handleDialog.event
    if (!event) return
    state.handleDialog.submitting = true
    try {
      await resolveCommandCenterIncident(event.id, {
        occurredAt: event.occurredAt,
        remark: state.handleDialog.remark
      })
      state.handleDialog.visible = false
      await fetchWarningEvents()
      await fetchKpiData()
      ElMessage.success('预警已处理')
    } catch {
      ElMessage.error('处理失败，请重试')
    } finally {
      state.handleDialog.submitting = false
    }
  }

  async function openWarnCurve(ev) {
    if (!ev.userCode || !ev.time) return
    state.warnCurveModal.event = ev
    state.warnCurveModal.title = `${ev.userName} · ${ev.type} 附近时段健康曲线`
    state.warnCurveModal.loading = true
    state.warnCurveModal.hasData = false
    state.warnCurveModal.records = []
    state.warnCurveModal.visible = true

    try {
      const curveState = await fetchDashboardWarnCurveState(ev)
      state.warnCurveModal.title = curveState.title
      state.warnCurveModal.records = curveState.records
      state.warnCurveModal.hasData = curveState.hasData
    } catch {
      state.warnCurveModal.hasData = false
    } finally {
      state.warnCurveModal.loading = false
    }
  }

  function initWarnCurveChart(elArg) {
    const dom = elArg
    if (!dom || !state.warnCurveModal.hasData) return
    const records = state.warnCurveModal.records
    const event = state.warnCurveModal.event
    const seriesDef = buildDashboardWarnCurveSeries(records, event?.type)
    const times = records.map(r => r.time ? String(r.time).slice(11, 16) : '')
    const warnTime = event?.time ? String(event.time).slice(11, 16) : null

    const chart = bindDashboardChart(state.warnCurveModal, 'chart', dom)
    chart.setOption(buildWarnCurveChartOption({
      times,
      series: seriesDef.map(item => ({
        label: item.label,
        color: item.color,
        values: records.map(record => record[item.key] != null
          ? (item.divisor ? +(record[item.key] / item.divisor).toFixed(1) : record[item.key])
          : null)
      })),
      warnTime,
      warnLevel: event?.level
    }))
  }

  return { openEmployeeDrawer, openHandleDialog, submitHandle, openWarnCurve, initWarnCurveChart }
}
