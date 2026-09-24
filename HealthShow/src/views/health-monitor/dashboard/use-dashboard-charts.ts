import type { Ref } from 'vue'
import type { Router } from 'vue-router'
import { bindDashboardChart } from './dashboard-chart-bind'
import {
  buildHourDistChartOption,
  buildUnifiedTrendChartOption,
  buildWarnTypeChartOption,
  buildDeptPersonChartOption,
  emptyChartOption
} from './dashboard-chart-options'
import { resolveDashboardWarningDistDate, resolveDashboardWarningDistSeries } from './dashboard-chart-data'
import type { DashboardState } from './dashboard-state'
import type { useDashboardComputed } from './use-dashboard-computed'

const CHART_RETRY_LIMIT = 12
const CHART_RETRY_MS = 160

/**
 * 统一管控页的 4 张图：体征异常趋势、预警时段分布、预警 7 日趋势、预警类型分布。
 * 容器可能还没有尺寸（页面刚挂载或标签页切换中），所以取容器时会按固定间隔重试几次。
 */
export function useDashboardCharts(
  state: DashboardState,
  deps: {
    computed: Pick<ReturnType<typeof useDashboardComputed>, 'warnTypeData'>
    router: Router
    unifiedTrendChart: Ref<HTMLElement | null>
  }
) {
  const { computed, router, unifiedTrendChart } = deps

  /** 取图表容器：有宽高才返回；没有则安排一次重试（重试次数有上限，页面卸载后作废） */
  function getReadyChartDom(target: string | HTMLElement | null | undefined, retryFn?: () => void) {
    const dom = typeof target === 'string' ? document.getElementById(target) : target
    if (!dom) return null
    const retryKey = typeof target === 'string' ? target : (dom.id || retryFn?.name || 'chart')
    if (dom.clientWidth > 0 && dom.clientHeight > 0) {
      state._chartRetryCounts[retryKey] = 0
      return dom
    }
    if (!retryFn) return null
    const count = state._chartRetryCounts[retryKey] || 0
    if (count >= CHART_RETRY_LIMIT) return null
    state._chartRetryCounts[retryKey] = count + 1
    const token = state._chartRetryToken
    setTimeout(() => {
      if (state._chartRetryToken !== token) return
      retryFn()
    }, CHART_RETRY_MS)
    return null
  }

  function initWarningTrend7dChart() {
    const dom = getReadyChartDom('warningTrend7dChart', initWarningTrend7dChart)
    if (!dom) return
    const chart = bindDashboardChart(state.charts, 'warningTrend7d', dom)
    const rows = state.warningTrend7dData || []
    if (!rows.length) {
      chart.setOption(emptyChartOption('暂无预警趋势数据'), true)
      return
    }
    chart.setOption(buildDeptPersonChartOption({
      days: rows.map((r) => r.date),
      series: [{
        name: '预警总数',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.count || r.cnt || 0),
        lineStyle: { width: 2, color: '#ff8c00' },
        itemStyle: { color: '#ff8c00' },
        symbol: 'circle',
        symbolSize: 5,
        areaStyle: { color: 'rgba(255,140,0,.12)' }
      }]
    }), true)
  }

  function initUnifiedTrendChart() {
    const dom = getReadyChartDom(unifiedTrendChart.value, initUnifiedTrendChart)
    if (!dom) return
    const chart = bindDashboardChart(state.charts, 'unifiedTrend', dom)

    const rawData = state.trendDailyData
    chart.setOption(buildUnifiedTrendChartOption({
      rawData,
      emptyMessage: state.activePeriod === 'day'
        ? (state.dashboardDataState === 'ready' ? '今日暂无汇总' : '今日汇总待刷新')
        : '暂无数据'
    }), true)
    chart.getZr().off('click')
    if (!rawData.length) return
    const dates = rawData.map((d) => d.date)
    chart.getZr().on('click', (e) => {
      const pt = chart.convertFromPixel({ seriesIndex: 0 }, [e.offsetX, e.offsetY])
      if (!pt) return
      const idx = Math.round(pt[0])
      if (idx < 0 || idx >= dates.length) return
      const date = dates[idx]
      if (!date) return
      void router.push({ path: '/alert-management/records', query: { startDate: date, endDate: date } })
    })
  }

  function initWarnTypeChart() {
    const dom = getReadyChartDom('warnTypeChart', initWarnTypeChart)
    if (!dom) return
    const chart = bindDashboardChart(state.charts, 'warnType', dom)
    chart.setOption(buildWarnTypeChartOption(computed.warnTypeData.value), true)
  }

  function initHourDistChart() {
    const dom = getReadyChartDom('hourDistChart', initHourDistChart)
    if (!dom) return
    const chart = bindDashboardChart(state.charts, 'hourDist', dom)

    const dist = state.warningDistData || { labels: [], counts: [] }
    const seriesData = resolveDashboardWarningDistSeries(dist, state.activePeriod)
    chart.setOption(buildHourDistChartOption({
      labels: seriesData.labels,
      vals: seriesData.vals,
      activePeriod: state.activePeriod,
      maxVal: seriesData.maxVal,
      yMax: seriesData.yMax
    }), true)
    chart.off('click')
    chart.on('click', (params) => {
      if (params.value === 0) return
      const date = resolveDashboardWarningDistDate(dist, state.activePeriod, params.dataIndex)
      if (!date) return
      void router.push({ path: '/alert-management/records', query: { startDate: date, endDate: date } })
    })
  }

  return { initWarningTrend7dChart, initUnifiedTrendChart, initWarnTypeChart, initHourDistChart }
}
