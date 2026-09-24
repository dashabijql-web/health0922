import { markRaw } from 'vue'

type Row = Record<string, any>

/** 统一管控页的全部页面状态（一个 reactive 对象，由各个 useDashboardXxx 组合函数共同读写） */
export function createDashboardState() {
  return {
    currentTime: '',
    _timeTask: null,
    _refreshTask: null,
    _kpiRefreshTask: null,
    _refreshTextTask: null,
    _resizeTask: null,
    _resizeHandler: null,
    _onVisibilityChange: null,
    _fullscreenHandler: null,
    _dashboardRequestSeq: 0,
    checkData: {} as Row,
    personCounts: {} as Row,
    deptPersonModal: {
      visible: false,
      loading: false,
      dateRange: null,
      chart: null
    },
    deptDetailModal: {
      visible: false,
      loading: false,
      deptName: '',
      dateRange: null,
      chart: null
    },
    metricDetailModal: {
      visible: false,
      loading: false,
      metricType: '',
      metricLabel: '',
      metricColor: '#00d4ff',
      dateRange: null,
      chart: null
    },
    warnCurveModal: {
      visible: false,
      loading: false,
      hasData: false,
      title: '',
      event: null,
      records: [],
      chart: null
    },
    bodyIndicators: {} as Row,
    healthSnapshot: null as Row | null,
    top5Data: [] as Row[],
    deptDataList: [] as Row[],
    deptPersonStatsList: [] as Row[],
    warningRates: [] as Row[],
    deviceStats: { total: 0, activeRate: 0, usageRate: 0, warningRate: 0 } as Row,
    warningEvents: [] as Row[],
    warningStreamTotals: null as { total: number; pending: number; handled: number } | null,
    warningTypesData: [] as Row[],
    isFullscreen: false,
    seenAlertIds: markRaw(new Set()),
    mineAiReport: '',
    mineAiTime: '',
    mineAiLoading: false,
    mineAiDialogVisible: false,
    charts: markRaw({}),
    _chartRetryCounts: markRaw({}),
    _chartRetryToken: 0,
    metricList: [
      { key: 'heartRate', label: '心率', color: '#00d4ff', icon: 'Monitor' },
      { key: 'bloodOxygen', label: '血氧', color: '#67C23A', icon: 'FirstAidKit' },
      { key: 'steps', label: '步数', color: '#F56C6C', icon: 'Promotion' },
      { key: 'temperature', label: '体温', color: '#00c8c8', icon: 'Sunny' },
      { key: 'pressure', label: '压力', color: '#3eb7ff', icon: 'MagicStick' }
    ],
    activePeriod: 'day',
    periodOptions: [
      { label: '当日', value: 'day' },
      { label: '近7日', value: 'week' },
      { label: '近30日', value: 'month' }
    ],
    empDrawer: {
      visible: false,
      loading: false,
      userName: '',
      abnormalCount: 0,
      data: {},
      vitals: [],
      warnings: [],
      trendChart: null,
      radarChart: null
    },
    handleDialog: {
      visible: false,
      event: null,
      remark: '',
      submitting: false
    },
    incidentDrawerVisible: false,
    currentIncidentEvent: null as Row | null,
    routeIncidentKey: '',
    isRefreshing: false,
    // Tracks the aggregate dashboard request separately from the realtime snapshot.
    // A failed request must not be rendered as the initial all-zero state.
    dashboardDataState: 'idle',
    dashboardDataError: '',
    dashboardMissingSections: [] as string[],
    lastRefreshTime: null as number | null,
    lastRefreshText: '加载中...',
    kpiRealtimeOnline: 0,
    kpiRealtimeTotal: 0,
    kpiTodayWarnings: 0,
    kpiYesterdayWarnings: 0,
    kpiUnhandledHigh: 0,
    kpiCriticalTotal: 0,
    kpiMidTotal: 0,
    kpiLowTotal: 0,
    commandSummary: null as Row | null,
    VITAL_NORMAL_RANGES: {
      heartRate: { max: 15 },
      bloodOxygen: { max: 10 },
      temperature: { max: 10 },
      pressure: { max: 20 }
    },
    trendDailyData: [] as Row[],
    warningDistData: { labels: [], counts: [] } as Row,
    warningTrend7dData: [] as Row[],
    departmentDrawerVisible: false,
    currentDepartment: null as string | null,
    preShiftData: { totalToday: null, qualifiedCount: null, failedCount: null, preShiftRate: null } as Row
  }
}

export type DashboardState = ReturnType<typeof createDashboardState>
