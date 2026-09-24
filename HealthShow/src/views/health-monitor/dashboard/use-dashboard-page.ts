import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, toRefs, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { buildDashboardAdmissionQueueItems, buildDashboardClosureLaneItems } from './dashboard-command-workflow'
import { alertTypeLabel, formatTimeAgo, formatWarnTime } from './dashboard-format'
import { createDashboardState } from './dashboard-state'
import { useDashboardComputed } from './use-dashboard-computed'
import { useDashboardCharts } from './use-dashboard-charts'
import { useDashboardData } from './use-dashboard-data'
import { useMineAi } from './use-mine-ai'
import { useDashboardActions } from './use-dashboard-actions'
import { useDashboardDetail } from './use-dashboard-detail'
import { useDashboardWorkflow } from './use-dashboard-workflow'
import { activateDashboardPage, mountDashboardPage, unmountDashboardPage } from './dashboard-lifecycle'
import { dashboardRuntimeMethods } from './dashboard-runtime'

export function useDashboardPage(): any {
  const model = reactive(createDashboardState())
  const stateRefs = toRefs(model)
  const route = useRoute()
  const router = useRouter()
  const ctx: any = model
  ctx.$router = router
  ctx.$route = route
  ctx.$nextTick = nextTick
  ctx.$message = ElMessage
  const dmScale = ref<HTMLElement | null>(null)
  const dmBody = ref<HTMLElement | null>(null)
  const unifiedTrendChart = ref<HTMLElement | null>(null)
  const refs = { dmScale: null as HTMLElement | null, dmBody: null as HTMLElement | null, unifiedTrendChart: null as HTMLElement | null }
  const syncRefs = () => {
    refs.dmScale = dmScale.value
    refs.dmBody = dmBody.value
    refs.unifiedTrendChart = unifiedTrendChart.value
  }
  ctx.$refs = refs

  const methodGroups = [
    dashboardRuntimeMethods
  ]
  methodGroups.forEach((methods) => {
    Object.entries(methods).forEach(([name, fn]) => {
      if (typeof fn === 'function') ctx[name] = fn.bind(ctx)
    })
  })
  const computedValues: Record<string, any> = { ...useDashboardComputed(model) }
  // 过渡桥接：还没迁移的旧方法仍通过 this.xxx 读取计算属性，把解包后的值暴露在共享上下文上
  Object.entries(computedValues).forEach(([name, value]) => {
    Object.defineProperty(ctx, name, {
      configurable: true,
      get: () => value.value
    })
  })
  const chartFns = useDashboardCharts(model, { computed: computedValues as any, router, unifiedTrendChart })
  // 过渡桥接：旧的运行时方法/生命周期仍通过 this.initXxx() 调用图表函数
  Object.assign(ctx, chartFns)
  const dataFns = useDashboardData(model, { computed: computedValues as any, charts: chartFns })
  const mineAiFns = useMineAi(model)
  // 过渡桥接：尚未迁移的旧方法（详情/处置/生命周期）仍通过 this.fetchXxx() 调用
  const workflowFns = useDashboardWorkflow(model, { router, data: dataFns })
  const detailFns = useDashboardDetail(model, { data: dataFns })
  const actionFns = useDashboardActions(model, { router, route, data: dataFns, charts: chartFns, workflow: workflowFns, detail: detailFns })
  Object.assign(ctx, dataFns, mineAiFns, workflowFns, detailFns, actionFns)
  computedValues.headerMetricStripItems = computed(() => (ctx.headerKpis || []).map((item: any, index: number) => ({
    key: `${item.label}-${index}`,
    label: item.label,
    value: item.valHtml ? String(item.valHtml).replace(/<[^>]+>/g, ' ') : String(item.val ?? '--'),
    note: ctx.normalizeHeaderMetricNote(item.sub),
    tone: ctx.resolveHeaderMetricTone(item.cls),
    clickable: Boolean(item.clickable),
    route: item.route
  })))
  computedValues.closureLaneItems = computed(() => buildDashboardClosureLaneItems({
    warningSummary: ctx.commandSummary?.warning,
    preShiftData: ctx.preShiftData
  }))
  computedValues.admissionQueueItems = computed(() => buildDashboardAdmissionQueueItems({
    preShiftData: ctx.preShiftData
  }))
  computedValues.primaryVitalCards = computed(() => (ctx.vitalCards || []).slice(0, 6))
  computedValues.supplementalVitalCards = computed(() => (ctx.vitalCards || []).slice(6))
  ctx.resolveHeaderMetricTone = (cls: string) => {
    if (cls === 'kpi-red') return 'danger'
    if (cls === 'kpi-orange') return 'warning'
    if (cls === 'kpi-green' || cls === 'kpi-teal') return 'success'
    return 'primary'
  }
  ctx.normalizeHeaderMetricNote = (note: unknown) => {
    if (!note) return ''
    const normalized = String(note).replace(/\s+/g, ' ').trim()
    if (normalized === '数据加载中...') return '等待刷新'
    return normalized.length > 18 ? `${normalized.slice(0, 18)}…` : normalized
  }
  ctx.onHeaderMetricSelect = (item: any) => {
    if (item?.route) void router.push(item.route)
  }

  onMounted(() => {
    syncRefs()
    mountDashboardPage(ctx)
  })
  onActivated(() => {
    syncRefs()
    activateDashboardPage(ctx)
  })
  onBeforeUnmount(() => {
    unmountDashboardPage(ctx)
  })
  watch(() => route.query, (query) => {
    workflowFns.openIncidentFromRoute(query)
  }, { immediate: true, deep: true })

  return {
    ...stateRefs,
    ...computedValues,
    ...toRefs(model),
    ...Object.fromEntries(methodGroups.flatMap((methods) => Object.keys(methods)).map((name) => [name, ctx[name]])),
    ...chartFns,
    ...dataFns,
    ...mineAiFns,
    ...workflowFns,
    ...detailFns,
    ...actionFns,
    alertTypeLabel,
    formatTimeAgo,
    formatWarnTime,
    $router: router,
    dmScale,
    dmBody,
    unifiedTrendChart
  }
}
