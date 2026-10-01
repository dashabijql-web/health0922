// 上线职工曲线的 ECharts 配置（docs/09 第三节"底左"）：亮绿折线、青绿渐变面积、橙黄小圆点，网格线极淡。
// 纯函数：数据和颜色都从参数传进来，不读页面状态。
import type { EChartsCoreOption } from 'echarts/core'
import { graphic } from 'echarts/core'
import type { HeadcountSeries } from '@/api/dashboard'
import { parseBeijing } from '@/utils/format'

export interface ChartColors {
  line: string
  areaTop: string
  areaBottom: string
  point: string
  text: string
  grid: string
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE

/** 两个相邻的点隔得比 gapMinutes 久，说明中间没收到定位文件：在中间插一个空点，曲线在那里断开 */
export function withGaps(series: HeadcountSeries): [number, number | null][] {
  const out: [number, number | null][] = []
  let prev: number | null = null
  for (const p of series.points) {
    const t = parseBeijing(p.time)
    if (prev !== null && t - prev > series.gapMinutes * MINUTE) {
      out.push([(prev + t) / 2, null])
    }
    out.push([t, p.inWell])
    prev = t
  }
  return out
}

/** 横轴刻度间隔：大约 6–11 格，取 30 分钟、1 小时、1.5 小时、2 小时、3 小时中合适的 */
function tickInterval(spanMs: number): number {
  for (const h of [0.5, 1, 1.5, 2, 3]) {
    if (spanMs / (h * HOUR) <= 11) return h * HOUR
  }
  return 4 * HOUR
}

/** 纵轴：0 起，每 3 一格，最少到 18，数据更大时向上取整到 3 的倍数 */
function yMax(values: (number | null)[]): number {
  const max = Math.max(0, ...values.filter((v): v is number => v !== null))
  return Math.max(18, Math.ceil(max / 3) * 3)
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

/** 毫秒 → 北京时间 "HH:mm" */
function beijingHhmm(ms: number): string {
  const d = new Date(ms + 8 * HOUR)
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`
}

/**
 * @param series 后端返回的当天数据
 * @param nowMs  横轴右端：查看今天时是现在，查看过去的日子时是那天 24 点
 */
export function buildHeadcountOption(series: HeadcountSeries, nowMs: number, colors: ChartColors): EChartsCoreOption {
  const dayStart = parseBeijing(`${series.date} 00:00:00`)
  const end = Math.max(nowMs, dayStart + HOUR)
  const data = withGaps(series)
  const interval = tickInterval(end - dayStart)
  const max = yMax(data.map((d) => d[1]))
  return {
    animation: false,
    grid: { left: 36, right: 16, top: 14, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(4, 28, 25, 0.92)',
      borderColor: colors.line,
      textStyle: { color: colors.text },
      formatter: (params: unknown) => {
        const p = (params as { value: [number, number | null] }[]).find((x) => x.value[1] !== null)
        return p ? `${beijingHhmm(p.value[0])}　井下 ${p.value[1]} 人` : ''
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
      min: 0,
      max,
      interval: max / 6,
      axisLine: { show: false },
      axisLabel: { color: colors.text, fontSize: 12 },
      splitLine: { lineStyle: { color: colors.grid, type: 'dashed' } }
    },
    series: [
      {
        type: 'line',
        data,
        connectNulls: false,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: colors.point },
        lineStyle: { color: colors.line, width: 2 },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors.areaTop },
            { offset: 1, color: colors.areaBottom }
          ])
        }
      }
    ]
  }
}
