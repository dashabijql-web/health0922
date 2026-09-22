import * as echarts from '@/utils/echarts-setup'

export function createAiChatChartRegistry() {
  const chartInstances = new Map<number | string, ReturnType<typeof echarts.init>>()

  function initChart(msgIndex: number | string, data: Array<Record<string, unknown>>, type: string) {
    const el = document.getElementById('viz-chart-' + msgIndex)
    if (!el) return
    disposeChart(msgIndex)

    const chart = echarts.init(el)
    chartInstances.set(msgIndex, chart)

    if (type === 'bar') {
      chart.setOption(buildBarOption(data))
    } else if (type === 'line') {
      chart.setOption(buildLineOption(data))
    }
  }

  function disposeChart(msgIndex: number | string) {
    const chart = chartInstances.get(msgIndex)
    if (!chart) return
    chart.dispose()
    chartInstances.delete(msgIndex)
  }

  function disposeAll() {
    chartInstances.forEach(chart => chart.dispose())
    chartInstances.clear()
  }

  return { initChart, disposeAll }
}

function buildBarOption(data: Array<Record<string, unknown>>) {
  const first = data[0]
  if (!first) return {}
  const keys = Object.keys(first)
  const textCol = keys.find(k => typeof first[k] === 'string') || keys[0]
  const numCols = keys.filter(k => typeof first[k] === 'number' && isFinite(first[k] as number))
  const mainCol = numCols[0] || keys.find(k => k !== textCol) || keys[0]

  return {
    backgroundColor: 'transparent',
    grid: { left: '30%', right: '8%', top: '5%', bottom: '5%', containLabel: false },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(0,20,50,0.9)',
      borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#c8d8e8' }
    },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#4a7098', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0,212,255,0.08)' } }
    },
    yAxis: {
      type: 'category',
      data: data.map(row => row[textCol]),
      axisLabel: { color: '#c8d8e8', fontSize: 11 },
      inverse: false
    },
    series: [{
      type: 'bar',
      data: data.map(row => Number(row[mainCol]).toFixed(2)),
      barMaxWidth: 18,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: 'rgba(0,100,200,0.7)' },
          { offset: 1, color: '#00d4ff' }
        ]),
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', color: '#c8d8e8', fontSize: 10, formatter: '{c}' }
    }]
  }
}

function buildLineOption(data: Array<Record<string, unknown>>) {
  const first = data[0]
  if (!first) return {}
  const keys = Object.keys(first)
  const timeKeys = ['record_time', 'create_time', 'update_time', 'warning_time', 'date', 'month', 'week', 'day']
  const timeCol = keys.find(k => timeKeys.some(t => k.toLowerCase().includes(t))) || keys[0]
  const numCols = keys.filter(k => typeof first[k] === 'number' && isFinite(first[k] as number))

  const categories = data.map(row => {
    const value = row[timeCol]
    return typeof value === 'string' ? value.slice(0, 16) : value
  })

  return {
    backgroundColor: 'transparent',
    grid: { left: '8%', right: '5%', top: '8%', bottom: '18%' },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0,20,50,0.9)',
      borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#c8d8e8' }
    },
    legend: numCols.length > 1 ? { textStyle: { color: '#c8d8e8' }, top: 0 } : { show: false },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: '#4a7098', fontSize: 10, rotate: 30 },
      axisLine: { lineStyle: { color: 'rgba(0,212,255,0.2)' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#4a7098', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0,212,255,0.08)' } }
    },
    series: numCols.map((col, index) => ({
      name: col,
      type: 'line',
      smooth: true,
      data: data.map(row => Number(row[col]).toFixed(2)),
      lineStyle: { color: index === 0 ? '#00d4ff' : '#00ff88', width: 2 },
      itemStyle: { color: index === 0 ? '#00d4ff' : '#00ff88' },
      areaStyle: index === 0 ? {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.2)' },
          { offset: 1, color: 'rgba(0,212,255,0)' }
        ])
      } : undefined
    }))
  }
}
