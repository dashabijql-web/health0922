import { getRiskWarningOverview, getRiskWarningTrend, getDeptWarningStats } from '@/api/risk-warning'
import { getCommandCenterDashboardSummary, getCommandCenterIncidents } from '@/api/command-center'
import {
  buildDepartmentListFromWarningStats,
  normalizeWarningTrendData,
  resolveWarningTotals
} from './safety-command-view-model'

export async function fetchSafetyCommandCritical({
  processWarningData,
  handledCountRef,
  pendingWarningsRef,
  totalWarningsRef,
  criticalWarningsRef,
  commandSummaryRef,
  dataAsOfRef
}) {
  try {
    const today = getLocalToday()
    const [overviewResult, listResult, summaryResult] = await Promise.allSettled([
      getRiskWarningOverview(today, today),
      getCommandCenterIncidents({ scope: 'today', status: 'OPEN', page: 1, size: 50 }),
      getCommandCenterDashboardSummary()
    ])

    if (listResult.status === 'fulfilled' && listResult.value?.data) {
      processWarningData({ list: listResult.value.data.items || [] })
    }

    if (overviewResult.status === 'fulfilled' && overviewResult.value?.data) {
      const totals = resolveWarningTotals(overviewResult.value.data, pendingWarningsRef.value)
      handledCountRef.value = totals.handled
      pendingWarningsRef.value = totals.pending
      totalWarningsRef.value = totals.total
      criticalWarningsRef.value = totals.critical
    }

    applyCommandSummary(summaryResult, {
      commandSummaryRef,
      dataAsOfRef,
      pendingWarningsRef,
      criticalWarningsRef
    })
  } catch {}
}

export async function fetchSafetyCommandData({
  handledCountRef,
  pendingWarningsRef,
  totalWarningsRef,
  criticalWarningsRef,
  commandSummaryRef,
  dataAsOfRef,
  warningTrendRef,
  departmentsRef,
  processWarningData
}) {
  try {
    const today = getLocalToday()
    const results = await Promise.allSettled([
      getRiskWarningOverview(today, today),
      getCommandCenterIncidents({ scope: 'today', status: 'OPEN', page: 1, size: 50 }),
      getDeptWarningStats(today, today),
      getRiskWarningTrend(7),
      getCommandCenterDashboardSummary()
    ])

    if (results[1].status === 'fulfilled' && results[1].value?.data) {
      processWarningData({ list: results[1].value.data.items || [] })
    }

    if (results[0].status === 'fulfilled' && results[0].value?.data) {
      const data = results[0].value.data
      const totals = resolveWarningTotals(data, pendingWarningsRef.value)
      handledCountRef.value = totals.handled
      pendingWarningsRef.value = totals.pending
      totalWarningsRef.value = totals.total
      criticalWarningsRef.value = totals.critical
    }

    if (results[2].status === 'fulfilled' && results[2].value?.data) {
      departmentsRef.value = buildDepartmentListFromWarningStats(results[2].value.data)
    }

    if (results[3].status === 'fulfilled' && results[3].value?.data) {
      warningTrendRef.value = normalizeWarningTrendData(results[3].value.data)
    }

    applyCommandSummary(results[4], {
      commandSummaryRef,
      dataAsOfRef,
      pendingWarningsRef,
      criticalWarningsRef
    })

  } catch {}
}

function applyCommandSummary(result, refs) {
  if (result?.status !== 'fulfilled' || result.value?.code !== 200 || !result.value?.data) return
  const summary = result.value.data
  refs.commandSummaryRef.value = summary
  refs.dataAsOfRef.value = summary.dataAsOf || ''
  if (summary.warning) {
    refs.pendingWarningsRef.value = Number(summary.warning.pendingTotal || 0)
    refs.criticalWarningsRef.value = Number(summary.warning.criticalPending || 0)
  }
}

function getLocalToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
