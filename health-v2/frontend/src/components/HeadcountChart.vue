<template>
  <div ref="el" class="headcount-chart" role="img" :aria-label="label"></div>
</template>

<script setup lang="ts">
// 上线人数曲线（docs/07 公共组件 HeadcountChart，ECharts）。横轴当天 0 点到现在，纵轴井下人数；
// 没有数据的时段不画点、不连线。图表实例在组件卸载时释放。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type ECharts } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { HeadcountSeries } from '@/api/dashboard'
import { buildHeadcountOption, type ChartColors } from './headcount-chart'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ series: HeadcountSeries; nowMs: number }>()

const el = ref<HTMLElement | null>(null)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

const label = computed(() => {
  const last = props.series.points.at(-1)
  return last ? `上线职工曲线，最新 ${last.inWell} 人` : '上线职工曲线'
})

/** 颜色取自 tokens.css，ECharts 画在 canvas 上不认 CSS 变量，这里读出实际值 */
function colors(): ChartColors {
  const css = getComputedStyle(el.value ?? document.documentElement)
  const v = (name: string) => css.getPropertyValue(name).trim()
  return {
    line: v('--chart-line'),
    areaTop: v('--chart-area-top'),
    areaBottom: v('--chart-area-bottom'),
    point: v('--chart-point'),
    text: v('--text-secondary'),
    grid: v('--chart-grid')
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildHeadcountOption(props.series, props.nowMs, colors()), { notMerge: true })
}

onMounted(() => {
  if (!el.value) return
  chart = init(el.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

watch(() => [props.series, props.nowMs], render)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.headcount-chart {
  width: 100%;
  height: 100%;
}
</style>
