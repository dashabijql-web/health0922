import { onMounted, reactive, ref, toRefs, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { alertTypeLabel, formatTimeAgo, formatWarnTime } from './dashboard-format'
import { createDashboardState } from './dashboard-state'
import { useDashboardActions } from './use-dashboard-actions'
import { useDashboardCharts } from './use-dashboard-charts'
import { useDashboardComputed } from './use-dashboard-computed'
import { useDashboardData } from './use-dashboard-data'
import { useDashboardDetail } from './use-dashboard-detail'
import { useDashboardLifecycle } from './use-dashboard-lifecycle'
import { useDashboardWorkflow } from './use-dashboard-workflow'
import { useMineAi } from './use-mine-ai'

/**
 * 统一管控页的组装：一个 reactive 状态 + 按职责拆分的组合函数。
 * 依赖方向（无环）：计算属性 -> 图表 -> 数据加载 -> 工作流 / 详情 -> 页面操作 -> 生命周期。
 */
export function useDashboardPage() {
  const state = reactive(createDashboardState())
  const route = useRoute()
  const router = useRouter()

  // 模板里的 ref="dmScale" / "dmBody" / "unifiedTrendChart" 会绑定到这三个 ref
  const dmScale = ref<HTMLElement | null>(null)
  const dmBody = ref<HTMLElement | null>(null)
  const unifiedTrendChart = ref<HTMLElement | null>(null)

  const computedValues = useDashboardComputed(state)
  const charts = useDashboardCharts(state, { computed: computedValues, router, unifiedTrendChart })
  const data = useDashboardData(state, { computed: computedValues, charts })
  const mineAi = useMineAi(state)
  const workflow = useDashboardWorkflow(state, { router, data })
  const detail = useDashboardDetail(state, { data })
  const actions = useDashboardActions(state, { router, route, data, charts, workflow, detail })
  const lifecycle = useDashboardLifecycle(state, { refs: { dmScale, dmBody }, data, charts, mineAi })

  // 从路由参数（warningId + occurredAt）还原事件抽屉，比如从别的页面跳过来
  watch(() => route.query, (query) => { workflow.openIncidentFromRoute(query) }, { immediate: true, deep: true })

  return {
    ...toRefs(state),
    ...computedValues,
    ...charts,
    ...data,
    ...mineAi,
    ...workflow,
    ...detail,
    ...actions,
    ...lifecycle,
    alertTypeLabel,
    formatTimeAgo,
    formatWarnTime,
    $router: router,
    dmScale,
    dmBody,
    unifiedTrendChart
  }
}
