import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getDepartmentList } from '@/api/department'
import { getSleepPageData, getSleepQualityDistribution, getSleepTrend } from '@/api/sleep'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import type { ChartStore } from '@/utils/chart-helpers'
import { exportToExcel } from '@/utils/export-excel'
import {
  renderSleepBedtime,
  renderSleepDept,
  renderSleepDuration,
  renderSleepScore,
  renderSleepStage,
  renderSleepTrend,
  type DeptUpload,
  type LegendItem,
  type ScoreBucket,
  type TrendData
} from './sleep-charts'

type SleepRecord = Record<string, any>

const REFRESH_INTERVAL_MS = 60_000
const PAGE_SIZE = 20

const DURATION_TIPS = [
  { label: '<4小时', color: '#ff5252', desc: '严重不足，影响认知' },
  { label: '4-6小时', color: '#FFB84D', desc: '偏少，易疲劳' },
  { label: '6-8小时', color: '#4FC3F7', desc: '建议范围' },
  { label: '>8小时', color: '#52c41a', desc: '充足，状态最佳' }
]

const BEDTIME_TIPS = [
  { time: '21-22时', icon: '★', color: '#52c41a', desc: '最佳入睡时间' },
  { time: '22-23时', icon: 'OK', color: '#4FC3F7', desc: '良好，顺应生物钟' },
  { time: '23-24时', icon: '!', color: '#FFB84D', desc: '偏晚，影响深睡' },
  { time: '0时以后', icon: '✕', color: '#ff5252', desc: '过晚，损害健康' }
]

const QUALITY_COLORS: Record<string, string> = { excellent: '#52c41a', good: '#4FC3F7', fair: '#FFB84D', poor: '#ff5252' }
const QUALITY_LABELS: Record<string, string> = { excellent: '优秀(≥8h)', good: '良好(7-8h)', fair: '一般(6-7h)', poor: '较差(<6h)' }

export function scoreClass(score: number) {
  if (score >= 80) return 'sc-excellent'
  if (score >= 60) return 'sc-good'
  if (score >= 40) return 'sc-fair'
  return 'sc-poor'
}

export function fmtTime(value: unknown) {
  return value ? dayjs(value as string).format('MM-DD HH:mm') : ''
}

export function useSleepPage() {
  // ── 状态 ──
  const currentTime = ref('')
  const yesterdayDate = ref('')
  const overview = ref({ uploadRate: 0, greenLineRate: 0, avgSleepTime: '--', avgScore: 0, totalCount: 0 })
  const stageLegend = ref<LegendItem[]>([])
  const durationLegend = ref<LegendItem[]>([])
  const deptUploadList = ref<DeptUpload[]>([])
  // 昨夜睡眠不足6小时的人员（最多20人）及其总人数，来自后端 /sleep/page-data
  const alertList = ref<SleepRecord[]>([])
  const alertTotal = ref(0)
  const detailList = ref<SleepRecord[]>([])
  const currentPage = ref(1)
  const recordDialog = reactive<{ visible: boolean; item: SleepRecord | null }>({ visible: false, item: null })

  // ── 图表容器与实例 ──
  const stageRef = ref<HTMLElement | null>(null)
  const scoreRef = ref<HTMLElement | null>(null)
  const trendRef = ref<HTMLElement | null>(null)
  const durationRef = ref<HTMLElement | null>(null)
  const bedtimeRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const listRef = ref<HTMLElement | null>(null)
  const charts: ChartStore = {}
  const notify = (text: string) => { ElMessage.info(text) }

  // ── 派生数据 ──
  const headerKpis = computed(() => {
    const o = overview.value
    return [
      { label: '数据上传率', val: (o.uploadRate || 0) + '%', cls: 'kpi-cyan' },
      { label: '绿线达标率', val: (o.greenLineRate || 0) + '%', cls: 'kpi-green' },
      { label: '平均睡眠时长', val: o.avgSleepTime || '--', cls: 'kpi-purple' },
      { label: '平均质量评分', val: (o.avgScore || 0) + '分', cls: 'kpi-amber' }
    ]
  })
  const statCards = computed(() => {
    const o = overview.value
    return [
      { label: '数据上传率', val: (o.uploadRate || 0) + '%', icon: '↑', color: '#00d4ff', bg: 'rgba(0,212,255,0.12)' },
      { label: '绿线达标率', val: (o.greenLineRate || 0) + '%', icon: 'OK', color: '#52c41a', bg: 'rgba(82,196,26,0.12)' },
      { label: '平均睡眠时长', val: o.avgSleepTime || '--', icon: '⏱', color: '#a78bfa', bg: 'rgba(167,139,250,0.12)' },
      { label: '平均质量评分', val: (o.avgScore || 0) + '分', icon: '★', color: '#FFB84D', bg: 'rgba(255,184,77,0.12)' }
    ]
  })
  const pagedList = computed(() => {
    const start = (currentPage.value - 1) * PAGE_SIZE
    return detailList.value.slice(start, start + PAGE_SIZE)
  })
  const totalPages = computed(() => Math.max(1, Math.ceil(detailList.value.length / PAGE_SIZE)))

  // ── 加载数据并绘图 ──
  async function renderStageAndDuration() {
    await nextTick()
    renderSleepStage(charts, stageRef.value, stageLegend.value, notify)
    renderSleepDuration(charts, durationRef.value, durationLegend.value, overview.value.totalCount, notify)
  }

  /** 返回各部门上传情况，供 loadDept 使用 */
  async function loadPageData(): Promise<DeptUpload[]> {
    let deptUpload: DeptUpload[] = []
    try {
      const res = await getSleepPageData()
      if (res.code === 200 && res.data) {
        const d = res.data
        if (d.overview) overview.value = { ...overview.value, ...d.overview }
        if (d.durationLegend?.length) durationLegend.value = d.durationLegend
        if (d.categoryLegend?.length) stageLegend.value = d.categoryLegend
        if (d.detailList?.length) detailList.value = d.detailList
        if (d.deptUpload?.length) deptUploadList.value = d.deptUpload
        alertList.value = d.alertList || []
        alertTotal.value = d.alertTotal || 0
        deptUpload = d.deptUpload || []
      }
    } catch { /* 接口失败时保留已有数据，图表按现有数据绘制 */ }
    await renderStageAndDuration()
    return deptUpload
  }

  async function loadTrend() {
    let data: TrendData = {}
    try {
      const res = await getSleepTrend(30)
      if (res.code === 200) data = res.data || {}
    } catch { /* 用空数据绘制 */ }
    await nextTick()
    renderSleepTrend(charts, trendRef.value, data, notify)
  }

  async function loadQualityDist() {
    let buckets: ScoreBucket[] = []
    try {
      const res = await getSleepQualityDistribution()
      if (res.code === 200 && res.data) {
        buckets = ['excellent', 'good', 'fair', 'poor']
          .filter((key) => res.data[key] > 0)
          .map((key) => ({ label: QUALITY_LABELS[key], count: res.data[key], color: QUALITY_COLORS[key] }))
      }
    } catch { /* 用空数据绘制 */ }
    await nextTick()
    renderSleepScore(charts, scoreRef.value, buckets, notify)
  }

  async function loadDept(deptUpload: DeptUpload[]) {
    let depts: any[] = []
    try {
      const res = await getDepartmentList()
      if (res.code === 200 && res.data) {
        depts = res.data.list || res.data.rows || res.data.records || res.data || []
      }
    } catch { /* 部门列表取不到时显示空态 */ }
    const uploadMap: Record<string, number> = {}
    deptUpload.forEach((d) => { uploadMap[d.deptName] = d.count })
    const list: DeptUpload[] | null = depts.length > 0
      ? depts
        .map((d) => {
          const deptName = d.deptName || d.name || d.label || ''
          return { deptName, count: uploadMap[deptName] ?? 0 }
        })
        .filter((d) => d.deptName)
      : null
    if (list?.length) deptUploadList.value = list
    await nextTick()
    renderSleepDept(charts, deptRef.value, list)
  }

  async function fetchData() {
    const deptUpload = await loadPageData()
    await Promise.allSettled([loadTrend(), loadQualityDist(), loadDept(deptUpload)])
  }

  // ── 导出与交互 ──
  async function exportExcel() {
    const list = detailList.value
    if (!list.length) { ElMessage.warning('暂无数据可导出'); return }
    const rows = list.map((r) => ({
      userName: r.userName || '--',
      deptName: r.deptName || '--',
      empCode: r.empCode || '--',
      sleepHours: r.sleepHours || '--',
      score: r.score ?? '--',
      levelText: r.levelText || '--',
      recordTime: r.recordTime ? dayjs(r.recordTime).format('YYYY-MM-DD HH:mm') : '--'
    }))
    const columns = [
      { label: '姓名', key: 'userName' },
      { label: '部门', key: 'deptName' },
      { label: '工号', key: 'empCode' },
      { label: '睡眠时长', key: 'sleepHours' },
      { label: '睡眠评分', key: 'score' },
      { label: '睡眠质量', key: 'levelText' },
      { label: '记录时间', key: 'recordTime' }
    ]
    await exportToExcel(rows, columns, `睡眠分析_${dayjs().format('YYYYMMDD')}`)
    ElMessage.success(`已导出 ${list.length} 条记录`)
  }

  function openRecordDialog(item: SleepRecord) {
    recordDialog.item = item
    recordDialog.visible = true
  }

  // ── 定时器与生命周期 ──
  const tickClock = () => { currentTime.value = dayjs().format('YYYY年MM月DD日 HH:mm:ss') }
  const clock = useIntervalTask(tickClock, 1000)
  const refresh = useIntervalTask(fetchData, REFRESH_INTERVAL_MS)
  const listScroll = useScrollLoop({ getElement: () => listRef.value, intervalMs: 80, step: 1, endPauseMs: 1500 })

  onMounted(() => {
    tickClock()
    clock.start()
    yesterdayDate.value = dayjs().subtract(1, 'day').format('MM月DD日')
    void fetchData()
    void nextTick(() => {
      listScroll.start()
      renderSleepBedtime(charts, bedtimeRef.value)
    })
    refresh.start()
  })

  onBeforeUnmount(() => {
    Object.values(charts).forEach((chart) => chart?.dispose())
  })

  return {
    alertList,
    alertTotal,
    bedtimeRef,
    bedtimeTips: BEDTIME_TIPS,
    currentPage,
    currentTime,
    deptRef,
    deptUploadList,
    detailList,
    durationLegend,
    durationRef,
    durationTips: DURATION_TIPS,
    exportExcel,
    fmtTime,
    headerKpis,
    listRef,
    openRecordDialog,
    overview,
    pagedList,
    recordDialog,
    scoreClass,
    scoreRef,
    stageLegend,
    stageRef,
    statCards,
    totalPages,
    trendRef,
    yesterdayDate
  }
}
