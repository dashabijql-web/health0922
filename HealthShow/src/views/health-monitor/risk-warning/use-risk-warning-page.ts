import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  getDeptWarningStats,
  getRiskWarningList,
  getRiskWarningOverview,
  getRiskWarningTrend,
  getRiskWarningTypeDistribution,
  handleBatchRiskWarning,
  handleRiskWarning
} from '@/api/risk-warning'
import { PERIOD_OPTIONS } from '@/constants/periods'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import type { ChartStore } from '@/utils/chart-helpers'
import { exportToExcel } from '@/utils/export-excel'
import {
  renderRiskDept,
  renderRiskTrend,
  renderRiskTrendDay,
  type DeptStat,
  type TrendData,
  type WarningStat
} from './risk-warning-charts'
import { fmtTime, fmtTimeFull, warnClass, warningLocatorKey } from './risk-warning-utils'
import { useWarningVital } from './use-warning-vital'

type WarningRow = Record<string, any>

const PAGE_SIZE = 50
const REFRESH_INTERVAL_MS = 30_000
const DEFAULT_WARNING_TYPES = ['心率异常', '血氧偏低', '血氧过低', '体温异常', '血压偏高', '压力偏高', '跌倒报警', 'SOS报警']
const PERIOD_LABELS: Record<string, string> = { day: '今日', week: '近7日', month: '近30日' }

const emptyTrend = (): TrendData => ({
  dates: [],
  series: { heartRate: [], bloodOxygen: [], temperature: [], pressure: [], deviceAlarm: [] }
})

export function useRiskWarningPage() {
  // ── 状态 ──
  const currentTime = ref('')
  const activePeriod = ref('day')
  const periodOptions = PERIOD_OPTIONS
  const warningStats = ref<WarningStat[]>([
    { label: '心率预警', value: 0, color: '#ef4444' },
    { label: '血氧预警', value: 0, color: '#f97316' },
    { label: '体温预警', value: 0, color: '#22c55e' },
    { label: '压力预警', value: 0, color: '#00d4ff' },
    { label: '设备报警', value: 0, color: '#a855f7' }
  ])
  // 总预警数以后端聚合为准，不用前端已加载的分类自行相加
  const periodWarningTotal = ref(0)
  const warningList = ref<WarningRow[]>([])
  const totalWarnings = ref(0)
  const warningTypes = ref<string[]>(DEFAULT_WARNING_TYPES)
  const currentPage = ref(1)
  const filterName = ref('')
  const filterLevel = ref('')
  const filterType = ref('')
  const trendData = ref<TrendData>(emptyTrend())
  const deptData = ref<DeptStat[]>([])
  const selectedKeys = ref<string[]>([])
  const batchHandling = ref(false)
  const detailVisible = ref(false)
  const detailRow = ref<WarningRow | null>(null)
  const handleNote = ref('')
  const handling = ref(false)
  const autoScrollPaused = ref(false)
  let manualPause = false

  // ── 图表容器与实例 ──
  const trendRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const listRef = ref<HTMLElement | null>(null)
  const vitalChartRef = ref<HTMLElement | null>(null)
  const charts: ChartStore = {}
  const { vitalLoading, vitalEmpty, vitalChartRange, vitalSummary, openVital, disposeVital } = useWarningVital(vitalChartRef)

  // ── 派生数据 ──
  const periodRange = computed(() => {
    const today = dayjs().format('YYYY-MM-DD')
    if (activePeriod.value === 'day') return { startDate: today, endDate: today }
    if (activePeriod.value === 'week') return { startDate: dayjs().subtract(6, 'day').format('YYYY-MM-DD'), endDate: today }
    return { startDate: dayjs().subtract(29, 'day').format('YYYY-MM-DD'), endDate: today }
  })
  const activePeriodLabel = computed(() => PERIOD_LABELS[activePeriod.value] || '当前时段')
  const statTitle = computed(() => ({ day: '今日预警统计', week: '近7日预警统计', month: '近30日预警统计' } as Record<string, string>)[activePeriod.value])
  const trendTitle = computed(() => ({ day: '今日预警分布', week: '近7天预警趋势', month: '近30天预警趋势' } as Record<string, string>)[activePeriod.value])
  const headerKpis = computed(() => {
    const [heartRate, bloodOxygen, temperature, pressure, deviceAlarm] = warningStats.value
    return [
      { key: 'total', label: '总预警', val: periodWarningTotal.value, tone: 'danger', note: `${activePeriodLabel.value}总量` },
      { key: 'heart-rate', label: '心率', val: heartRate.value, tone: 'danger', note: '优先级最高' },
      { key: 'blood-oxygen', label: '血氧', val: bloodOxygen.value, tone: 'warning', note: '异常波动' },
      { key: 'temperature', label: '体温', val: temperature.value, tone: 'success', note: '趋势跟踪' },
      { key: 'pressure', label: '压力', val: pressure.value, tone: 'primary', note: '班前复核' },
      { key: 'device-alarm', label: '设备报警', val: deviceAlarm.value, tone: 'alarm', note: '跌倒/SOS等' }
    ]
  })
  const statMax = computed(() => Math.max(1, ...warningStats.value.map((x) => x.value)))
  const statBarWidth = (val: number) => (val / statMax.value) * 100
  // 列表由服务端按筛选条件分页，前端不再二次过滤
  const filteredList = computed(() => warningList.value)
  const totalPages = computed(() => Math.max(1, Math.ceil(totalWarnings.value / PAGE_SIZE)))
  const listRowIndex = (index: number | string) => (currentPage.value - 1) * PAGE_SIZE + Number(index) + 1
  const pendingInFiltered = computed(() => filteredList.value.filter((x) => !x.handled))
  const allPendingSelected = computed(() =>
    pendingInFiltered.value.length > 0 && pendingInFiltered.value.every((x) => selectedKeys.value.includes(warningLocatorKey(x))))
  const somePendingSelected = computed(() =>
    pendingInFiltered.value.some((x) => selectedKeys.value.includes(warningLocatorKey(x))))

  // ── 加载数据并绘图 ──
  async function loadStats() {
    const { startDate, endDate } = periodRange.value
    try {
      const r = await getRiskWarningOverview(startDate, endDate)
      if (r.code === 200 && r.data) {
        const d = r.data
        const values = [d.heartRateCount, d.bloodOxygenCount, d.temperatureCount, d.pressureCount, d.deviceAlarmCount]
        warningStats.value.forEach((stat, i) => { stat.value = values[i] || 0 })
        periodWarningTotal.value = d.totalWarnings || 0
      }
    } catch { /* 保留上次数据 */ }
  }

  async function loadTrend() {
    if (activePeriod.value === 'day') return
    const days = activePeriod.value === 'week' ? 7 : 30
    try {
      const r = await getRiskWarningTrend(days)
      if (r.code === 200 && r.data) {
        if (r.data.dates?.length) {
          trendData.value = r.data
        } else if (Array.isArray(r.data) && r.data.length) {
          const next = emptyTrend()
          r.data.forEach((x: WarningRow) => {
            next.dates.push(x.date)
            next.series.heartRate.push(x.heartRate || 0)
            next.series.bloodOxygen.push(x.bloodOxygen || 0)
            next.series.temperature.push(x.temperature || 0)
            next.series.pressure.push(x.pressure || 0)
            next.series.deviceAlarm.push(x.deviceAlarm || 0)
          })
          trendData.value = next
        }
      }
    } catch { /* 保留上次数据 */ }
    await nextTick()
    renderRiskTrend(charts, trendRef.value, trendData.value)
  }

  async function loadDept() {
    const { startDate, endDate } = periodRange.value
    try {
      const r = await getDeptWarningStats(startDate, endDate)
      if (r.code === 200 && r.data?.length) deptData.value = r.data
    } catch { /* 保留上次数据 */ }
    await nextTick()
    renderRiskDept(charts, deptRef.value, deptData.value)
  }

  async function loadList() {
    try {
      const { startDate, endDate } = periodRange.value
      const r = await getRiskWarningList({
        page: currentPage.value,
        size: PAGE_SIZE,
        keyword: filterName.value.trim() || undefined,
        level: filterLevel.value || undefined,
        warningType: filterType.value || undefined,
        handled: null,
        startDate,
        endDate
      })
      if (r.code === 200 && r.data) {
        warningList.value = r.data.list || []
        totalWarnings.value = Number(r.data.total || 0)
        selectedKeys.value = []
      }
    } catch { /* 保留上次数据 */ }
  }

  async function loadTypes() {
    try {
      const r = await getRiskWarningTypeDistribution()
      if (r.code === 200 && Array.isArray(r.data)) {
        const types = r.data.map((item: WarningRow) => item.type).filter(Boolean)
        // 新库可以没有业务记录，保留默认类型选项，避免筛选菜单变成空白
        if (types.length) warningTypes.value = types
      }
    } catch { /* 保留默认类型 */ }
  }

  async function fetchData() {
    await Promise.allSettled([loadStats(), loadTrend(), loadDept(), loadTypes(), loadList()])
    await nextTick()
    // 今日分布图依赖 warningStats，必须等 loadStats 落地后再画，否则会画成“暂无数据”
    if (activePeriod.value === 'day') renderRiskTrendDay(charts, trendRef.value, warningStats.value)
  }

  function switchPeriod(period: string) {
    if (activePeriod.value === period) return
    activePeriod.value = period
    currentPage.value = 1
    void fetchData()
  }

  function applyListFilters() {
    currentPage.value = 1
    void loadList()
  }

  async function jumpPage(page: number) {
    const nextPage = Math.max(1, Math.min(page, totalPages.value))
    if (nextPage === currentPage.value) return
    currentPage.value = nextPage
    await loadList()
    await nextTick()
    if (listRef.value) listRef.value.scrollTop = 0
  }

  // ── 详情与处理 ──
  async function openDetail(item: WarningRow) {
    detailRow.value = item
    detailVisible.value = true
    await openVital(item)
  }

  function onDrawerClose() {
    disposeVital()
    handleNote.value = ''
    handling.value = false
  }

  async function doHandle() {
    const row = detailRow.value
    if (!row || handling.value) return
    handling.value = true
    try {
      const note = handleNote.value || '已确认处理'
      const res = await handleRiskWarning(row.id, { handleRemark: note, handleBy: 'admin', createTime: row.createTime })
      if (res.code === 200) {
        const key = warningLocatorKey(row)
        const inList = warningList.value.find((r) => warningLocatorKey(r) === key)
        if (inList) { inList.handled = true; inList.handleNote = note }
        detailRow.value = { ...row, handled: true, handleNote: note }
        ElMessage.success('处理成功')
      } else {
        ElMessage.error(res.message || '处理失败')
      }
    } catch {
      ElMessage.error('操作失败，请重试')
    } finally {
      handling.value = false
    }
  }

  function toggleSelectAll(e: Event) {
    const pendingKeys = pendingInFiltered.value.map((item) => warningLocatorKey(item))
    if ((e.target as HTMLInputElement).checked) {
      selectedKeys.value = [...new Set([...selectedKeys.value, ...pendingKeys])]
    } else {
      const pending = new Set(pendingKeys)
      selectedKeys.value = selectedKeys.value.filter((key) => !pending.has(key))
    }
  }

  function toggleSelect(item: WarningRow) {
    const key = warningLocatorKey(item)
    selectedKeys.value = selectedKeys.value.includes(key)
      ? selectedKeys.value.filter((x) => x !== key)
      : [...selectedKeys.value, key]
  }

  async function batchHandle() {
    if (!selectedKeys.value.length || batchHandling.value) return
    batchHandling.value = true
    try {
      const selected = new Set(selectedKeys.value)
      const locators = warningList.value
        .filter((row) => selected.has(warningLocatorKey(row)))
        .map((row) => ({ warningId: row.id, occurredAt: row.createTime }))
      const res = await handleBatchRiskWarning(locators)
      if (res.code === 200) {
        ElMessage.success(`已处理 ${locators.length} 条预警`)
        selectedKeys.value = []
        await loadList()
      } else {
        ElMessage.error(res.message || '批量处理失败')
      }
    } catch {
      ElMessage.error('批量处理失败，请重试')
    } finally {
      batchHandling.value = false
    }
  }

  function exportWarnings() {
    const cols = [
      { label: '序号', key: '_idx' },
      { label: '姓名', key: 'userName' },
      { label: '工号', key: 'empCode' },
      { label: '部门', key: 'deptName' },
      { label: '预警类型', key: 'warningType' },
      { label: '预警级别', key: 'warningLevel' },
      { label: '预警值', key: 'warningValue' },
      { label: '状态', key: '_status' },
      { label: '处理备注', key: 'handleNote' },
      { label: '时间', key: '_time' }
    ]
    const data = filteredList.value.map((row, i) => ({
      ...row,
      _idx: listRowIndex(i),
      _status: row.handled ? '已处理' : '待处理',
      _time: row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '--',
      empCode: row.empCode || row.userCode || '--'
    }))
    exportToExcel(data, cols, '风险预警记录')
  }

  // ── 列表自动滚动 ──
  const listScroll = useScrollLoop({
    getElement: () => listRef.value,
    intervalMs: 40,
    step: 1,
    endPauseMs: 2000,
    shouldScroll: () => !autoScrollPaused.value,
    onReachEnd: () => {
      void nextTick(() => {
        if (listRef.value) listRef.value.scrollTop = 0
        autoScrollPaused.value = manualPause
      })
    }
  })
  const pauseAutoScroll = () => { autoScrollPaused.value = true }
  const resumeAutoScroll = () => { if (!manualPause) autoScrollPaused.value = false }
  const toggleAutoScroll = () => {
    manualPause = !manualPause
    autoScrollPaused.value = manualPause
  }

  // ── 定时器与生命周期 ──
  const tickClock = () => { currentTime.value = dayjs().format('YYYY年MM月DD日 HH:mm:ss') }
  const clock = useIntervalTask(tickClock, 1000)
  const refresh = useIntervalTask(fetchData, REFRESH_INTERVAL_MS)

  onMounted(() => {
    tickClock()
    clock.start()
    void fetchData()
    void nextTick(() => listScroll.start())
    refresh.start()
  })

  onBeforeUnmount(() => {
    disposeVital()
    Object.values(charts).forEach((chart) => chart?.dispose())
  })

  return {
    activePeriod,
    allPendingSelected,
    applyListFilters,
    autoScrollPaused,
    batchHandle,
    batchHandling,
    currentPage,
    currentTime,
    deptRef,
    detailRow,
    detailVisible,
    doHandle,
    exportWarnings,
    filterLevel,
    filterName,
    filterType,
    filteredList,
    fmtTime,
    fmtTimeFull,
    handleNote,
    handling,
    headerKpis,
    jumpPage,
    listRef,
    listRowIndex,
    onDrawerClose,
    openDetail,
    pageSize: PAGE_SIZE,
    pauseAutoScroll,
    periodOptions,
    resumeAutoScroll,
    selectedKeys,
    somePendingSelected,
    statBarWidth,
    statTitle,
    switchPeriod,
    toggleAutoScroll,
    toggleSelect,
    toggleSelectAll,
    totalPages,
    totalWarnings,
    trendRef,
    trendTitle,
    vitalChartRange,
    vitalChartRef,
    vitalEmpty,
    vitalLoading,
    vitalSummary,
    warnClass,
    warningLocatorKey,
    warningStats,
    warningTypes
  }
}
