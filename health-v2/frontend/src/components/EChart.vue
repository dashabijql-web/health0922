<template>
  <div ref="el" class="echart" role="img" :aria-label="label"></div>
</template>

<script setup lang="ts">
// 通用的 ECharts 容器：配置由调用方的纯函数算好传进来（如 views/archive/archive-charts.ts），这里只负责
// 创建、跟着容器大小变化、配置变了重画、卸载时释放图表实例。ECharts 按需引入（docs/01 第四节）；
// 只有部分页面用到的图表类型（如月报的饼图）由那个页面的 *-charts.ts 自己 use()，跟着它的分包按需下载。
// 容器还没有大小（比如在翻书里还没翻到、display: none）时先不创建，等有了大小再创建，免得 ECharts 报宽高为 0。
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ option: EChartsCoreOption; label: string }>()

const el = ref<HTMLElement | null>(null)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

function ensureChart() {
  const node = el.value
  if (!node || node.clientWidth === 0 || node.clientHeight === 0) return
  if (!chart) {
    chart = init(node)
    chart.setOption(props.option, { notMerge: true })
  } else {
    chart.resize()
  }
}

onMounted(() => {
  if (!el.value) return
  ensureChart()
  observer = new ResizeObserver(ensureChart)
  observer.observe(el.value)
})

watch(() => props.option, (o) => chart?.setOption(o, { notMerge: true }))

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
  height: 100%;
}
</style>
