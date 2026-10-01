<template>
  <div ref="el" class="echart" role="img" :aria-label="label"></div>
</template>

<script setup lang="ts">
// 通用的 ECharts 容器：配置由调用方的纯函数算好传进来（如 views/archive/archive-charts.ts），这里只负责
// 创建、跟着容器大小变化、配置变了重画、卸载时释放图表实例。ECharts 按需引入（docs/01 第四节）。
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

onMounted(() => {
  if (!el.value) return
  chart = init(el.value)
  chart.setOption(props.option, { notMerge: true })
  observer = new ResizeObserver(() => chart?.resize())
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
