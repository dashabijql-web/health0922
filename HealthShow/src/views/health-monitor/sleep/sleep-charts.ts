import { emptyOption } from '@/utils/echarts-config'
import { gradV, initChart, type ChartStore } from '@/utils/chart-helpers'

export interface LegendItem { name: string; value: number; color: string }
export interface ScoreBucket { label: string; count: number; color: string }
export interface DeptUpload { deptName: string; count: number }
export interface TrendData { dates?: string[]; avgData?: number[]; scores?: number[] }
/** 点击图表时给用户的提示，由页面决定怎么显示（这里传 ElMessage.info）。 */
export type Notify = (text: string) => void

export function renderSleepStage(charts: ChartStore, el: HTMLElement | null | undefined, legend: LegendItem[], notify: Notify) {
  const c = initChart(charts, 'stage', el); if (!c) return
  c.setOption({
    backgroundColor: 'transparent',
    series: [{
      type: 'pie', radius: ['50%', '78%'], center: ['50%', '50%'],
      label: { show: false }, labelLine: { show: false },
      cursor: 'pointer',
      data: legend.map((x) => ({
        value: x.value, name: x.name,
        itemStyle: { color: x.color, borderRadius: 3, shadowColor: x.color + '55', shadowBlur: 8 }
      }))
    }]
  })
  c.on('click', (params) => { notify(`${params.name}：${params.value}%`) })
}

export function renderSleepScore(charts: ChartStore, el: HTMLElement | null | undefined, data: ScoreBucket[], notify: Notify) {
  const c = initChart(charts, 'score', el); if (!c) return
  const d = Array.isArray(data) ? data : []
  if (!d.length) {
    c.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#8ba6c8', fontSize: 13, fontWeight: 'normal' } }, series: [] })
    return
  }
  c.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.3)',
      textStyle: { color: '#e0f0ff', fontSize: 11 },
      formatter: (p) => `${p[0].name}<br/>人数：<b style="color:#a78bfa">${p[0].value}</b> 人`
    },
    grid: { left: '2%', right: '4%', top: '8%', bottom: '16%', containLabel: true },
    xAxis: {
      type: 'category', data: d.map((x) => x.label),
      axisLine: { lineStyle: { color: 'rgba(167,139,250,0.15)' } }, axisTick: { show: false },
      axisLabel: { color: '#8ba6c8', fontSize: 9 }
    },
    yAxis: {
      type: 'value', axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
      axisLabel: { color: '#8ba6c8', fontSize: 9 }
    },
    series: [{
      type: 'bar', barWidth: '55%',
      cursor: 'pointer',
      data: d.map((x) => ({
        value: x.count,
        itemStyle: { color: x.color, borderRadius: [4, 4, 0, 0], shadowColor: x.color + '44', shadowBlur: 6 }
      })),
      label: { show: true, position: 'top', color: '#a8c5e6', fontSize: 9 }
    }]
  })
  c.on('click', (params) => { notify(`${params.name}：${params.value} 人`) })
}

export function renderSleepTrend(charts: ChartStore, el: HTMLElement | null | undefined, data: TrendData, notify: Notify) {
  const c = initChart(charts, 'trend', el); if (!c) return
  const dates = data.dates || []
  if (!dates.length) {
    c.setOption(emptyOption('暂无睡眠趋势数据', 13))
    return
  }
  const hours = data.avgData || []
  const scores = data.scores || []
  const hasScores = scores.length === dates.length
  // 标签间隔按日期个数自适应：最多显示约 7 个，避免点数少时只剩第一个标签
  const labelInterval = Math.max(0, Math.ceil(dates.length / 7) - 1)
  c.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8,13,35,0.92)', borderColor: 'rgba(167,139,250,0.3)',
      textStyle: { color: '#e0f0ff', fontSize: 11 },
      formatter: (p) => `${p[0].name}<br/>
        <span style="color:#a78bfa">睡眠时长：${Number(p[0].value).toFixed(1)}h</span>` +
        (hasScores ? `<br/><span style="color:#52c41a">质量评分：${p[1]?.value ?? '--'}分</span>` : '')
    },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '12%', containLabel: true },
    xAxis: {
      type: 'category', data: dates, boundaryGap: true,
      axisLine: { lineStyle: { color: 'rgba(167,139,250,0.18)' } }, axisTick: { show: false },
      axisLabel: { color: '#8ba6c8', fontSize: 10, interval: labelInterval }
    },
    yAxis: [
      {
        type: 'value', name: '时长(h)', nameTextStyle: { color: '#a78bfa', fontSize: 10 },
        axisLine: { show: false }, axisTick: { show: false },
        splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
        axisLabel: { color: '#8ba6c8', fontSize: 9 },
        min: 0, max: 12
      },
      {
        type: 'value', name: '评分', nameTextStyle: { color: '#52c41a', fontSize: 10 },
        axisLine: { show: false }, axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { color: '#8ba6c8', fontSize: 9 },
        min: 0, max: 100
      }
    ],
    series: [
      {
        name: '睡眠时长', type: 'bar', yAxisIndex: 0, data: hours, barWidth: '55%',
        cursor: 'pointer',
        itemStyle: {
          color: gradV('rgba(167,139,250,0.9)', 'rgba(167,139,250,0.18)'),
          borderRadius: [3, 3, 0, 0]
        },
        markLine: {
          silent: true, symbol: 'none',
          data: [{ yAxis: 7, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 },
            label: { color: '#FFB84D', fontSize: 10, formatter: '建议7h' } }]
        }
      },
      ...(hasScores ? [{
        name: '质量评分', type: 'line', yAxisIndex: 1, data: scores, smooth: true, symbol: 'none',
        lineStyle: { color: '#52c41a', width: 2 },
        areaStyle: { color: gradV('rgba(82,196,26,0.18)', 'rgba(82,196,26,0.02)') },
        markPoint: {
          symbol: 'circle', symbolSize: 5,
          label: { fontSize: 9, fontFamily: 'Consolas', offset: [0, -12] },
          data: [
            { type: 'max', itemStyle: { color: '#52c41a' }, label: { color: '#52c41a', formatter: (p) => '▲' + p.value } },
            { type: 'min', itemStyle: { color: '#ff5252' }, label: { color: '#ff5252', formatter: (p) => '▼' + p.value } }
          ]
        }
      }] : [])
    ]
  })
  c.on('click', (params) => {
    if (params.seriesName === '睡眠时长') {
      notify(`${params.name} 睡眠时长：${Number(params.value).toFixed(1)}h`)
    } else if (params.seriesName === '质量评分') {
      notify(`${params.name} 质量评分：${params.value}分`)
    }
  })
}

export function renderSleepDuration(charts: ChartStore, el: HTMLElement | null | undefined, legend: LegendItem[], totalCount: number, notify: Notify) {
  const c = initChart(charts, 'duration', el); if (!c) return
  const total = totalCount || 0
  c.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.25)',
      textStyle: { color: '#e0f0ff', fontSize: 11 },
      formatter: (p) => `${p.name}<br/>占比：<b style="color:${p.color}">${p.value}%</b><br/>约 ${Math.round(p.value * total / 100)} 人`
    },
    title: {
      text: '检测人数', subtext: total + '人',
      left: 'center', top: '36%',
      textStyle: { color: '#6a88ab', fontSize: 10 },
      subtextStyle: { color: '#a78bfa', fontSize: 16, fontWeight: 'bold', fontFamily: 'Consolas' }
    },
    series: [{
      type: 'pie', radius: ['42%', '64%'], center: ['50%', '52%'],
      label: { show: false }, labelLine: { show: false },
      cursor: 'pointer',
      data: legend.map((x) => ({
        value: x.value, name: x.name,
        itemStyle: { color: x.color, borderRadius: 3, shadowColor: x.color + '44', shadowBlur: 6 }
      }))
    }]
  })
  c.on('click', (params) => {
    const count = Math.round(Number(params.value || 0) * total / 100)
    notify(`${params.name}：${params.value}%（约 ${count} 人）`)
  })
}

export function renderSleepBedtime(charts: ChartStore, el: HTMLElement | null | undefined) {
  const c = initChart(charts, 'bedtime', el); if (!c) return
    c.setOption(emptyOption('暂无数据', 13))
}

export function renderSleepDept(charts: ChartStore, el: HTMLElement | null | undefined, data: DeptUpload[] | null) {
  const c = initChart(charts, 'dept', el); if (!c) return
  if (!data || !data.length) {
    c.setOption(emptyOption())
    return
  }
  const list = data
  const sorted = [...list].sort((a, b) => b.count - a.count)
  const getColor = (v) => v >= 90 ? '#52c41a' : v >= 75 ? '#4FC3F7' : v >= 60 ? '#FFB84D' : '#ff5252'
  const barMaxW = Math.max(10, Math.min(28, Math.floor(900 / sorted.length) - 4))
  c.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'none' },
      backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.25)',
      textStyle: { color: '#e0f0ff', fontSize: 11 },
      formatter: (p) => `${p[0].name}：<b style="color:${p[0].color}">${p[0].value}%</b>`
    },
    grid: { left: 32, right: 12, top: 24, bottom: 56 },
    xAxis: {
      type: 'category',
      data: sorted.map((x) => x.deptName),
      axisLine: { lineStyle: { color: 'rgba(167,139,250,0.15)' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#8ba6c8',
        fontSize: sorted.length > 15 ? 8 : 9,
        rotate: sorted.length > 12 ? 35 : 0,
        interval: 0,
        overflow: 'truncate',
        width: 52
      }
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
      axisLabel: { color: '#8ba6c8', fontSize: 9, formatter: (v) => v + '%' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: barMaxW,
      data: sorted.map((x) => ({
        value: x.count,
        itemStyle: {
          color: gradV(getColor(x.count), getColor(x.count) + '55'),
          borderRadius: [4, 4, 0, 0]
        }
      })),
      label: {
        show: true, position: 'top',
        color: '#a8c5e6', fontSize: sorted.length > 15 ? 8 : 9,
        formatter: (p) => p.value + '%'
      },
      showBackground: true,
      backgroundStyle: { color: 'rgba(167,139,250,0.05)', borderRadius: [4, 4, 0, 0] }
    }]
  })
}
