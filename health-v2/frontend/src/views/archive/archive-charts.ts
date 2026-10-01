// 个人档案的图表配置（docs/05 第七节）：今日心率/血氧/体温折线、近几天步数柱状图。
// 纯函数：数据、颜色、"现在"都从参数传进来，不读页面状态。颜色取自 tokens.css（readChartColors）。
import type { EChartsCoreOption } from 'echarts/core'
import { graphic } from 'echarts/core'
import type { StepDay, Trend, VitalKey } from '@/api/archive'
import { parseBeijing } from '@/utils/format'

export interface ArchiveChartColors {
  line: string
  line2: string
  areaTop: string
  areaBottom: string
  point: string
  text: string
  grid: string
  tooltipBg: string
}

/** ECharts 画在 canvas 上不认 CSS 变量，这里读出实际值 */
export function readChartColors(el: Element = document.documentElement): ArchiveChartColors {
  const css = getComputedStyle(el)
  const v = (name: string) => css.getPropertyValue(name).trim()
  return {
    line: v('--chart-line'),
    line2: v('--chart-line-2'),
    areaTop: v('--chart-area-top'),
    areaBottom: v('--chart-area-bottom'),
    point: v('--chart-point'),
    text: v('--text-secondary'),
    grid: v('--chart-grid'),
    tooltipBg: v('--chart-tooltip-bg')
  }
}

export const METRIC_TEXT: Record<VitalKey, { name: string; unit: string }> = {
  HEART_RATE: { name: '心率', unit: '次/分' },
  SPO2: { name: '血氧', unit: '%' },
  TEMPERATURE: { name: '体温', unit: '℃' },
  BLOOD_PRESSURE: { name: '血压', unit: 'mmHg' }
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function pad(n: number) {
  return String(n).padStart(2, '0')
}

/** 毫秒 → 北京时间 "HH:mm" */
function beijingHhmm(ms: number): string {
  const d = new Date(ms + 8 * HOUR)
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`
}

/** 横轴刻度间隔：大约 6–11 格 */
function tickInterval(spanMs: number): number {
  for (const h of [0.5, 1, 1.5, 2, 3]) {
    if (spanMs / (h * HOUR) <= 11) return h * HOUR
  }
  return 4 * HOUR
}

/**
 * 折线的点：相邻两点隔得比 gapMinutes 久，说明中间没有数据，插一个空点让折线断开（docs/05 第七节"没有数据的时段不连线"）。
 *
 * @param pick 取哪个值（血压的收缩压 value、舒张压 value2）
 */
export function withGaps(trend: Trend, pick: 'value' | 'value2' = 'value'): [number, number | null][] {
  const out: [number, number | null][] = []
  let prev: number | null = null
  for (const p of trend.points) {
    const t = parseBeijing(p.time)
    if (prev !== null && t - prev > trend.gapMinutes * MINUTE) out.push([(prev + t) / 2, null])
    out.push([t, p[pick]])
    prev = t
  }
  return out
}

/**
 * 某天某项体征的折线。横轴是那天 0 点到现在（看过去的日子时到 24 点），纵轴按数据自动取范围。
 *
 * @param nowMs 浏览器的现在，只决定横轴右端画到哪
 */
export function buildTrendOption(trend: Trend, nowMs: number, colors: ArchiveChartColors,
                                 opts: { compact?: boolean } = {}): EChartsCoreOption {
  const dayStart = parseBeijing(`${trend.date} 00:00:00`)
  const end = Math.min(dayStart + DAY, Math.max(nowMs, dayStart + HOUR))
  const interval = tickInterval(end - dayStart)
  const { name, unit } = METRIC_TEXT[trend.metric]
  const bp = trend.metric === 'BLOOD_PRESSURE'
  const symbolSize = trend.points.length > 120 ? 3 : 5

  const line = (seriesName: string, data: [number, number | null][], color: string, area: boolean) => ({
    name: seriesName,
    type: 'line',
    data,
    connectNulls: false,
    showSymbol: true,
    symbol: 'circle',
    symbolSize,
    itemStyle: { color: bp ? color : colors.point },
    lineStyle: { color, width: 2 },
    areaStyle: area
      ? {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors.areaTop },
            { offset: 1, color: colors.areaBottom }
          ])
        }
      : undefined
  })

  return {
    animation: false,
    grid: opts.compact ? { left: 40, right: 14, top: 26, bottom: 24 } : { left: 52, right: 24, top: 40, bottom: 34 },
    legend: bp
      ? { top: 0, right: 8, itemWidth: 14, itemHeight: 8, textStyle: { color: colors.text, fontSize: 12 } }
      : undefined,
    tooltip: {
      trigger: 'axis',
      backgroundColor: colors.tooltipBg,
      borderColor: colors.line,
      textStyle: { color: colors.text },
      formatter: (params: unknown) => {
        const list = (params as { seriesName: string; value: [number, number | null] }[])
          .filter((x) => x.value[1] !== null)
        if (list.length === 0) return ''
        const head = beijingHhmm(list[0].value[0])
        return `${head}<br/>${list.map((x) => `${bp ? x.seriesName : name} ${x.value[1]} ${unit}`).join('<br/>')}`
      }
    },
    xAxis: {
      type: 'time',
      min: dayStart,
      max: end,
      interval,
      minInterval: interval,
      maxInterval: interval,
      axisLine: { lineStyle: { color: colors.grid } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: colors.text, fontSize: 12, hideOverlap: true, formatter: (v: number) => beijingHhmm(v) }
    },
    yAxis: {
      type: 'value',
      scale: true,
      max: trend.metric === 'SPO2' ? 100 : undefined,
      axisLine: { show: false },
      axisLabel: { color: colors.text, fontSize: 12 },
      splitLine: { lineStyle: { color: colors.grid, type: 'dashed' } }
    },
    series: bp
      ? [line('收缩压', withGaps(trend, 'value'), colors.line, false),
          line('舒张压', withGaps(trend, 'value2'), colors.line2, false)]
      : [line(name, withGaps(trend), colors.line, true)]
  }
}

/** 近几天的步数柱状图；那天没有记录的不画柱子（不是 0），悬停写"无记录"。 */
export function buildStepsOption(days: StepDay[], colors: ArchiveChartColors,
                                 opts: { compact?: boolean } = {}): EChartsCoreOption {
  return {
    animation: false,
    grid: opts.compact ? { left: 52, right: 14, top: 26, bottom: 24 } : { left: 60, right: 24, top: 30, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: colors.tooltipBg,
      borderColor: colors.line,
      textStyle: { color: colors.text },
      formatter: (params: unknown) => {
        const p = (params as { dataIndex: number }[])[0]
        const d = days[p.dataIndex]
        return d ? `${d.date}<br/>${d.steps === null ? '无记录' : `${d.steps} 步`}` : ''
      }
    },
    xAxis: {
      type: 'category',
      data: days.map((d) => d.date.slice(5)),
      axisLine: { lineStyle: { color: colors.grid } },
      axisTick: { show: false },
      axisLabel: { color: colors.text, fontSize: 12, hideOverlap: true }
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLine: { show: false },
      axisLabel: { color: colors.text, fontSize: 12 },
      splitLine: { lineStyle: { color: colors.grid, type: 'dashed' } }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 26,
        data: days.map((d) => d.steps),
        itemStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors.line },
            { offset: 1, color: colors.areaBottom }
          ])
        }
      }
    ]
  }
}
