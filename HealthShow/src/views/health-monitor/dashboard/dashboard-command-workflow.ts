export function buildDashboardClosureLaneItems({
  warningSummary,
  preShiftData
}) {
  const critical = warningSummary?.criticalPending ?? 0
  const pending = warningSummary?.pendingTotal ?? 0
  const unassigned = warningSummary?.unassignedTotal ?? 0
  const overdue = warningSummary?.overdueTotal ?? 0
  return [
    {
      key: 'high',
      label: '高危闭环',
      value: critical,
      note: '通知页优先处置',
      tone: critical > 0 ? 'danger' : 'success',
      route: '/alert-management/records?handleStatus=unhandled'
    },
    {
      key: 'pending',
      label: '待办事件',
      value: pending,
      note: '不限产生日期，服务端全量统计',
      tone: pending > 0 ? 'warning' : 'success',
      route: '/alert-management/records'
    },
    {
      key: 'unassigned',
      label: '未分派事件',
      value: unassigned,
      note: '尚未明确处置责任人',
      tone: unassigned > 0 ? 'warning' : 'success',
      route: '/alert-management/records?handleStatus=unhandled'
    },
    {
      key: 'overdue',
      label: '已超时事件',
      value: overdue,
      note: '已配置 SLA 且超过时限',
      tone: overdue > 0 ? 'danger' : 'success',
      route: '/alert-management/records?handleStatus=unhandled'
    }
  ]
}

export function buildDashboardAdmissionQueueItems({ preShiftData }) {
  return [
    {
      key: 'passed',
      label: '准入通过',
      value: preShiftData?.qualifiedCount == null ? '--' : preShiftData.qualifiedCount,
      note: '查看今日合格名单',
      tone: 'success',
      route: { path: '/health-monitor/mine-entry', query: { status: 'pass', from: 'dashboard' } }
    },
    {
      key: 'prohibited',
      label: '禁止入井',
      value: preShiftData?.failedCount == null ? '--' : preShiftData.failedCount,
      note: '查看异常指标与人员',
      tone: (preShiftData?.failedCount || 0) > 0 ? 'danger' : 'success',
      route: { path: '/health-monitor/mine-entry', query: { status: 'fail', from: 'dashboard' } }
    }
  ]
}
