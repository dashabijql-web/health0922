// 月报的图表配置（docs/07 第二部分）：工种/部门饼图、体征柱状图、告警柱状图、稳定性饼图。
// 纯函数：数据和颜色都从参数传进来，不读页面状态。颜色取自 tokens.css 的 --report-*（readReportColors）。
// 饼图只有月报用，在这里注册，跟着月报的分包按需下载（EChart.vue 只注册了折线和柱状图）。
import { use, type EChartsCoreOption } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import type { AlertCategory, Share, Stability, StabilityMetric, VitalStat } from '@/api/report'
import { ALERT_NAME, METRIC_NAME, shareName } from './report-format'

use([PieChart])

export interface ReportColors {
  palette: string[]
  white: string
  other: string
  unrecorded: string
  grid: string
  text: string
  /** 稳定 / 波动 / 不稳定 */
  status: [string, string, string]
}

/** ECharts 画在 canvas 上不认 CSS 变量，这里读出实际值 */
export function readReportColors(el: Element = document.documentElement): ReportColors {
  const css = getComputedStyle(el)
  const v = (name: string) => css.getPropertyValue(name).trim()
  const palette = [1, 2, 3, 4, 5, 6, 7, 8].map((i) => v(`--report-chart-${i}`))
  return {
    palette,
    white: v('--report-white'),
    other: v('--report-chart-other'),
    unrecorded: v('--report-chart-grid'),
    grid: v('--report-chart-grid'),
    text: v('--report-chart-text'),
    status: [palette[1], palette[2], palette[3]]
  }
}

const FONT = { fontSize: 13 }

/** 第 3、4 页：工种或部门的人数占比。图例在右上角（截图 7） */
export function sharePie(shares: Share[], colors: ReportColors): EChartsCoreOption {
  const data = shares.map((s, i) => ({
    name: shareName(s),
    value: s.count,
    itemStyle: {
      color: s.kind === 'OTHER' ? colors.other : s.kind === 'UNRECORDED' ? colors.unrecorded
        : colors.palette[i % colors.palette.length]
    }
  }))
  return {
    animation: false,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）', textStyle: FONT },
    legend: {
      orient: 'vertical', right: 0, top: 6, itemWidth: 22, itemHeight: 12, itemGap: 10,
      textStyle: { ...FONT, color: colors.text }
    },
    series: [{
      type: 'pie',
      center: ['42%', '54%'],
      radius: '68%',
      minAngle: 2,
      startAngle: 90,
      itemStyle: { borderColor: colors.white, borderWidth: 1 },
      label: { ...FONT, color: colors.text, formatter: '{b}' },
      labelLine: { length: 12, length2: 14 },
      data
    }]
  }
}

/** 第 5 页：心率、体温、血氧的最大、最小、平均（截图 8）。没有数据的那一项不画柱子 */
export function vitalsBar(vitals: Record<StabilityMetric, VitalStat | null>, metrics: StabilityMetric[],
  colors: ReportColors): EChartsCoreOption {
  const names = metrics.map((m) => METRIC_NAME[m].label)
  const series = (['max', 'min', 'avg'] as const).map((key, i) => ({
    type: 'bar',
    name: ['最大值', '最小值', '平均值'][i],
    barGap: 0,
    barMaxWidth: 42,
    itemStyle: { color: colors.palette[i] },
    label: { show: true, position: 'inside', ...FONT, color: i === 0 ? colors.white : colors.text },
    data: metrics.map((m) => vitals[m]?.[key] ?? null)
  }))
  return {
    animation: false,
    grid: { left: 44, right: 12, top: 18, bottom: 64 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, textStyle: FONT },
    legend: { bottom: 4, itemWidth: 26, itemHeight: 13, textStyle: { ...FONT, color: colors.text } },
    xAxis: { type: 'category', data: names, axisTick: { show: false }, axisLabel: { ...FONT, color: colors.text },
      axisLine: { lineStyle: { color: colors.text } } },
    yAxis: { type: 'value', axisLabel: { ...FONT, color: colors.text }, splitLine: { lineStyle: { color: colors.grid } } },
    series
  }
}

/** 第 6 页：六类告警的事件数和涉及人数 */
export function alertsBar(rows: { category: AlertCategory; persons: number; events: number }[],
  colors: ReportColors): EChartsCoreOption {
  return {
    animation: false,
    grid: { left: 44, right: 12, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, textStyle: FONT },
    legend: { top: 0, right: 0, itemWidth: 22, itemHeight: 12, textStyle: { ...FONT, color: colors.text } },
    xAxis: { type: 'category', data: rows.map((r) => ALERT_NAME[r.category]), axisTick: { show: false },
      axisLabel: { ...FONT, color: colors.text }, axisLine: { lineStyle: { color: colors.text } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { ...FONT, color: colors.text },
      splitLine: { lineStyle: { color: colors.grid } } },
    series: [
      { type: 'bar', name: '事件数（条）', barGap: 0, barMaxWidth: 26, itemStyle: { color: colors.palette[3] },
        label: { show: true, position: 'top', ...FONT, color: colors.text }, data: rows.map((r) => r.events) },
      { type: 'bar', name: '涉及人数（人）', barMaxWidth: 26, itemStyle: { color: colors.palette[0] },
        label: { show: true, position: 'top', ...FONT, color: colors.text }, data: rows.map((r) => r.persons) }
    ]
  }
}

/** 第 8–10 页：稳定、波动、不稳定的人数（不参与评估的人不在图里，写在正文里） */
export function stabilityPie(s: Stability, colors: ReportColors): EChartsCoreOption {
  const data = [
    { name: '稳定', value: s.stable, itemStyle: { color: colors.status[0] } },
    { name: '波动', value: s.fluctuating, itemStyle: { color: colors.status[1] } },
    { name: '不稳定', value: s.unstable, itemStyle: { color: colors.status[2] } }
  ]
  return {
    animation: false,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）', textStyle: FONT },
    legend: { orient: 'vertical', right: 0, top: 6, itemWidth: 22, itemHeight: 12,
      formatter: (name: string) => `${name}  ${data.find((d) => d.name === name)?.value ?? ''} 人`,
      textStyle: { ...FONT, color: colors.text } },
    series: [{
      type: 'pie',
      center: ['40%', '52%'],
      radius: ['36%', '66%'],
      startAngle: 90,
      itemStyle: { borderColor: colors.white, borderWidth: 2 },
      label: { ...FONT, color: colors.text, formatter: '{b}\n{d}%' },
      labelLine: { length: 10, length2: 12 },
      data
    }]
  }
}
