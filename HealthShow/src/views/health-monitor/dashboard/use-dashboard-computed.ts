import { computed } from 'vue'
import dayjs from 'dayjs'
import {
  buildDashboardAiSummary,
  buildRiskDeptList,
  buildWarningTypeData,
  getFocusWarningEvents,
  getLatestDangerEvent
} from './dashboard-summary'
import {
  buildDashboardDeviceCards,
  buildDashboardHealthExceptionCards,
  buildDashboardHeaderKpis,
  buildDashboardMetricCards,
  buildDashboardTop5DisplayData,
  buildDashboardVitalCards
} from './dashboard-view-model'
import { buildDashboardAdmissionQueueItems, buildDashboardClosureLaneItems } from './dashboard-command-workflow'
import { normalizeHeaderMetricNote, resolveHeaderMetricTone } from './dashboard-format'
import type { DashboardState } from './dashboard-state'

/** 统一管控页的派生数据（只读计算属性），全部来自 state，不带副作用 */
export function useDashboardComputed(state: DashboardState) {
  const mineAiRendered = computed(() => {
    if (!state.mineAiReport) return ''
    return state.mineAiReport
      .replace(/^## (.+)$/gm, '<h4>$1</h4>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
  })

  const dashboardAiSummary = computed(() => {
    return buildDashboardAiSummary({
      mineAiReport: state.mineAiReport,
      kpiUnhandledHigh: state.kpiUnhandledHigh,
      preShiftData: state.preShiftData,
      riskDeptList: riskDeptList.value,
      latestDangerEvent: latestDangerEvent.value
    })
  })

  // 今日待处理预警的去重人数，来自服务端摘要；未取到时为 null，页面显示 --。
  const focusPersonTotal = computed(() => {
    const total = state.commandSummary?.warning?.pendingPersonToday
    return Number.isFinite(total) ? total : null
  })

  const focusWarningEvents = computed(() => {
    return getFocusWarningEvents(state.warningEvents)
  })

  const periodRange = computed(() => {
    const today = dayjs()
    if (state.activePeriod === 'day') {
      return { startTime: today.format('YYYY-MM-DD'), endTime: today.format('YYYY-MM-DD') }
    }
    if (state.activePeriod === 'week') {
      return {
        startTime: today.subtract(6, 'day').format('YYYY-MM-DD'),
        endTime: today.format('YYYY-MM-DD')
      }
    }
    return {
      startTime: today.subtract(29, 'day').format('YYYY-MM-DD'),
      endTime: today.format('YYYY-MM-DD')
    }
  })

  const periodLabel = computed(() => {
    return { day: '当日', week: '近7日', month: '近30日' }[state.activePeriod]
  })

  const trendDayCount = computed(() => {
    return { day: 1, week: 7, month: 30 }[state.activePeriod]
  })

  const trendXLabels = computed(() => {
    if (state.activePeriod === 'day') {
      return [dayjs().format('MM/DD')]
    }
    if (state.activePeriod === 'week') {
      return ['7天前', '6天前', '5天前', '4天前', '3天前', '昨天', '今天']
    }
    return Array.from({ length: 30 }, (_, i) => {
      const d = dayjs().subtract(29 - i, 'day')
      return (i % 7 === 0 || i === 29) ? d.format('MM/DD') : ''
    })
  })

  const trendBlockTitle = computed(() => {
    return { day: '今日体征汇总', week: '近7日体征趋势', month: '近30日体征趋势' }[state.activePeriod]
  })

  const trendPanelSubtitle = computed(() => {
    return state.activePeriod === 'day' ? '当日汇总（按日）' : `${periodLabel.value}数据走势`
  })

  const hourDistTitle = computed(() => {
    return { day: '今日预警时段', week: '近7日预警时段', month: '近30日预警时段' }[state.activePeriod]
  })

  const totalRecords = computed(() => {
    const d = state.checkData
    if (!d || Object.keys(d).length === 0) return null
    return (d.heartRate || 0) + (d.bloodOxygen || 0) + (d.steps || 0) + (d.temperature || 0) + (d.pressure || 0)
  })

  const kpiOnline = computed(() => {
    return Math.round(state.deviceStats.total * (state.deviceStats.usageRate || 0) / 100)
  })

  const deviceOnline = computed(() => {
    if (state.commandSummary?.device?.online !== undefined) return state.commandSummary.device.online
    return Math.round((state.deviceStats.total || 0) * (state.deviceStats.activeRate || 0) / 100)
  })

  const deviceOffline = computed(() => {
    if (state.commandSummary?.device?.offline !== undefined) return state.commandSummary.device.offline
    return Math.max(0, (state.deviceStats.total || 0) - deviceOnline.value)
  })

  const lowBatteryCount = computed(() => {
    const capability = state.commandSummary?.device?.lowBattery
    if (capability) return capability.status === 'AVAILABLE' ? capability.value : null
    if (state.deviceStats.lowBattery !== undefined && state.deviceStats.lowBattery !== null) {
      return state.deviceStats.lowBattery
    }
    if (state.deviceStats.lowBatteryRate && state.deviceStats.total) {
      return Math.round((state.deviceStats.total || 0) * (state.deviceStats.lowBatteryRate || 0) / 100)
    }
    return null
  })

  const warningRateList = computed(() => {
    return state.warningRates || []
  })

  const headerKpis = computed(() => {
    return buildDashboardHeaderKpis({
      kpiRealtimeOnline: state.kpiRealtimeOnline,
      kpiRealtimeTotal: state.kpiRealtimeTotal,
      kpiTodayWarnings: state.kpiTodayWarnings,
      kpiCriticalTotal: state.kpiCriticalTotal,
      kpiMidTotal: state.kpiMidTotal,
      kpiLowTotal: state.kpiLowTotal,
      warningEvents: state.warningEvents,
      personCounts: state.personCounts,
      realtimeWarningUsers: state.healthSnapshot?.warningUsers ?? state.healthSnapshot?.abnormalUsers,
      deviceActivationRate: state.deviceStats.total > 0 ? Math.round(state.deviceStats.activeRate || 0) : null,
      preShiftData: state.preShiftData,
      periodLabel: periodLabel.value,
      dataState: state.dashboardDataState,
      missingSections: state.dashboardMissingSections
    })
  })

  const totalPersons = computed(() => {
    const v = state.personCounts.totalPersons
    return v != null ? Number(v) : null
  })

  const metricCards = computed(() => {
    return buildDashboardMetricCards({
      metricList: state.metricList,
      personCounts: state.personCounts,
      checkData: state.checkData,
      kpiRealtimeTotal: state.kpiRealtimeTotal
    })
  })

  const healthExceptionCards = computed(() => {
    return buildDashboardHealthExceptionCards({
      vitalCards: vitalCards.value,
      warningEvents: state.warningEvents,
      healthSnapshot: state.healthSnapshot,
      dataAvailable: !['idle', 'loading', 'error'].includes(state.dashboardDataState)
        && !state.dashboardMissingSections.includes('bodyIndicators')
        && !state.dashboardMissingSections.includes('healthSnapshot')
    })
  })

  const healthSnapshotStatusText = computed(() => {
    if (state.dashboardDataState === 'error') return '统计失败'
    if (state.dashboardDataState === 'partial') return '部分失败'
    if (state.dashboardDataState === 'loading' && !state.lastRefreshTime) return '加载中'
    if (state.dashboardDataState === 'stale') return '数据过期'
    return {
      NORMAL: '正常',
      PARTIAL: '部分覆盖',
      STALE: '数据过期',
      NO_DATA: '暂无数据'
    }[state.healthSnapshot?.status] || (state.lastRefreshTime ? '暂无数据' : '加载中')
  })

  const vitalCards = computed(() => {
    return buildDashboardVitalCards({
      bodyIndicators: state.bodyIndicators,
      warningRates: state.warningRates
    })
  })

  const healthAssess = computed(() => {
    const b = state.bodyIndicators
    return [
      {
        label: '心率',
        pct: b.avgHeartRate ? Math.min(100, Math.round((b.avgHeartRate - 40) / (120 - 40) * 100)) : 0,
        color: (b.avgHeartRate > 100 || b.avgHeartRate < 55) ? '#ff5252' : '#38ef7d',
        tag: !b.avgHeartRate ? '--' : (b.avgHeartRate > 100) ? '偏快' : (b.avgHeartRate < 55) ? '偏慢' : '正常'
      },
      {
        label: '血氧',
        pct: b.avgBloodOxygen || 0,
        color: (b.avgBloodOxygen < 90) ? '#ff5252' : (b.avgBloodOxygen < 95) ? '#ffd200' : '#38ef7d',
        tag: !b.avgBloodOxygen ? '--' : (b.avgBloodOxygen < 90) ? '过低' : (b.avgBloodOxygen < 95) ? '偏低' : '良好'
      },
      {
        label: '压力',
        pct: Math.min(100, b.avgPressure || 0),
        color: (b.avgPressure > 80) ? '#ff5252' : (b.avgPressure > 60) ? '#ffd200' : '#38ef7d',
        tag: !b.avgPressure ? '--' : (b.avgPressure > 80) ? '过高' : (b.avgPressure > 60) ? '偏高' : '适中'
      }
    ]
  })

  const latestDangerEvent = computed(() => {
    return getLatestDangerEvent(state.warningEvents)
  })

  const top5DisplayData = computed(() => {
    return buildDashboardTop5DisplayData(state.top5Data)
  })

  const top5Max = computed(() => {
    const data = top5DisplayData.value
    return Math.max(...data.map((d) => d.count || 0), 1)
  })

  const trendItems = computed(() => {
    const base = [
      { key: 'heartRate', label: '心率', color: '#00d4ff' },
      { key: 'bloodOxygen', label: '血氧', color: '#67C23A' },
      { key: 'pressure', label: '压力', color: '#3eb7ff' }
    ]
    return base.map((item) => {
      const found = state.warningRates.find((rateItem) => rateItem.name && rateItem.name.includes(item.label))
      return { ...item, latest: found ? found.rate : 0 }
    })
  })

  const warnTypeData = computed(() => {
    return buildWarningTypeData({
      warningTypesData: state.warningTypesData,
      warningEvents: state.warningEvents
    })
  })

  // Re-shapes the already-loaded warningEvents into the field names
  // DepartmentIncidentDrawer expects (dept/user/status instead of
  // deptName/userName/handled). id + occurredAt are kept so emitted events
  // can be matched back to the original warningEvents item.
  const departmentDrawerEvents = computed(() => {
    return (state.warningEvents || []).map((event) => ({
      id: event.id,
      occurredAt: event.occurredAt,
      dept: event.deptName && event.deptName !== '--' ? event.deptName : '未分组',
      user: event.userName,
      type: event.type,
      time: event.time,
      location: event.location,
      owner: event.owner && event.owner !== '--' ? event.owner : '未分派',
      level: event.level === 'danger' ? 'critical' : (event.level === 'warn' ? 'high' : 'medium'),
      status: event.handled ? 'RESOLVED' : 'NEW'
    }))
  })

  const riskDeptList = computed(() => {
    return buildRiskDeptList(state.deptDataList)
  })

  const deviceCards = computed(() => {
    return buildDashboardDeviceCards({
      deviceStats: state.deviceStats,
      deviceOnline: deviceOnline.value,
      deviceOffline: deviceOffline.value,
      lowBatteryCount: lowBatteryCount.value,
      dataInterrupted: state.commandSummary?.device?.dataInterrupted,
      faulted: state.commandSummary?.device?.faulted,
      dataAvailable: !['idle', 'loading', 'error'].includes(state.dashboardDataState)
        && !state.dashboardMissingSections.includes('deviceActivation')
    })
  })

  /** 顶部指标条：把 headerKpis 整理成页面直接渲染的条目 */
  const headerMetricStripItems = computed(() => (headerKpis.value || []).map((item: any, index: number) => ({
    key: `${item.label}-${index}`,
    label: item.label,
    value: item.valHtml ? String(item.valHtml).replace(/<[^>]+>/g, ' ') : String(item.val ?? '--'),
    note: normalizeHeaderMetricNote(item.sub),
    tone: resolveHeaderMetricTone(item.cls),
    clickable: Boolean(item.clickable),
    route: item.route
  })))
  const closureLaneItems = computed(() => buildDashboardClosureLaneItems({
    warningSummary: state.commandSummary?.warning,
    preShiftData: state.preShiftData
  }))
  const admissionQueueItems = computed(() => buildDashboardAdmissionQueueItems({
    preShiftData: state.preShiftData
  }))
  const primaryVitalCards = computed(() => (vitalCards.value || []).slice(0, 6))
  const supplementalVitalCards = computed(() => (vitalCards.value || []).slice(6))

  return {
    mineAiRendered,
    dashboardAiSummary,
    focusPersonTotal,
    focusWarningEvents,
    periodRange,
    periodLabel,
    trendDayCount,
    trendXLabels,
    trendBlockTitle,
    trendPanelSubtitle,
    hourDistTitle,
    totalRecords,
    kpiOnline,
    deviceOnline,
    deviceOffline,
    lowBatteryCount,
    warningRateList,
    headerKpis,
    totalPersons,
    metricCards,
    healthExceptionCards,
    healthSnapshotStatusText,
    vitalCards,
    healthAssess,
    latestDangerEvent,
    top5DisplayData,
    top5Max,
    trendItems,
    warnTypeData,
    departmentDrawerEvents,
    riskDeptList,
    deviceCards,
    headerMetricStripItems,
    closureLaneItems,
    admissionQueueItems,
    primaryVitalCards,
    supplementalVitalCards
  }
}
