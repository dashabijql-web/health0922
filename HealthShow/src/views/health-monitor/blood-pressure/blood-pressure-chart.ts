import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, hourlyGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, gradV, type ChartStore } from '@/utils/chart-helpers'

export function renderBloodPressureDept(charts: ChartStore, el: HTMLElement | null | undefined, data: Array<Record<string, any>>, onSelectDept: (name: string) => void) {
  const c = initChart(charts, 'dept', el)
  if (!c) return
  if (!data.length) {
    c.setOption(emptyOption())
    return
  }
  const d = data.slice(0, 12)
  c.setOption({
    backgroundColor: 'transparent',
    legend: { data: ['收缩压', '舒张压'], right: 10, top: 6, textStyle: { color: '#8ba6c8', fontSize: 11 }, itemWidth: 10, itemHeight: 10, icon: 'rect' },
    grid: { ...deptGrid(), top: '14%' },
    xAxis: valueAxis(),
    yAxis: { ...categoryAxis(d.map(x => x.deptName), { show: false }), inverse: true },
    series: [
      {
        name: '收缩压',
        type: 'bar',
        stack: 'none',
        barWidth: '35%',
        data: d.map(x => x.avgSystolic || 0),
        itemStyle: { color: gradH('#a78bfa', '#7c3aed') },
        label: barLabel()
      },
      {
        name: '舒张压',
        type: 'bar',
        stack: 'none',
        barWidth: '35%',
        data: d.map(x => x.avgDiastolic || 0),
        itemStyle: { color: gradH('#38bdf8', '#0284c7'), borderRadius: [0, 4, 4, 0] },
        label: barLabel()
      }
    ]
  })
  c.off('click')
  c.on('click', params => {
    const name = d[params.dataIndex]?.deptName
    if (!name) return
    onSelectDept(name)
  })
}

export function renderBloodPressureHourly(charts: ChartStore, el: HTMLElement | null | undefined, sysVals: Array<number | null>, diaVals: Array<number | null>) {
  const c = initChart(charts, 'hourly', el)
  if (!c) return
  const hours = Array.from({ length: 24 }, (_, i) => i + ':00')
  c.setOption({
    backgroundColor: 'transparent',
    tooltip: chartTooltip(p => {
      const sys = p.find(x => x.seriesName === '收缩压')
      const dia = p.find(x => x.seriesName === '舒张压')
      return `${p[0].name}<br/>` +
        `收缩压：<b style="color:#a78bfa">${sys?.value ?? '--'}</b> mmHg<br/>` +
        `舒张压：<b style="color:#38bdf8">${dia?.value ?? '--'}</b> mmHg`
    }),
    legend: {
      data: ['收缩压', '舒张压'],
      right: 4,
      top: 2,
      textStyle: { color: '#8ba6c8', fontSize: 10 },
      itemWidth: 12,
      itemHeight: 3
    },
    grid: { ...hourlyGrid(), top: '18%' },
    xAxis: { ...categoryAxis(hours, { fontSize: 9, interval: 3, lineColor: 'rgba(0,212,255,0.15)' }), boundaryGap: false },
    yAxis: valueAxis({ fontSize: 9, splitColor: 'rgba(0,212,255,0.06)', min: v => v.min > 0 ? v.min - 8 : 50, max: v => v.max > 0 ? v.max + 8 : 160 }),
    series: [
      {
        name: '收缩压',
        type: 'line',
        data: sysVals,
        smooth: true,
        symbol: 'none',
        connectNulls: false,
        lineStyle: { color: '#a78bfa', width: 1.5 },
        areaStyle: { color: gradV('rgba(167,139,250,0.22)', 'rgba(167,139,250,0.02)') }
      },
      {
        name: '舒张压',
        type: 'line',
        data: diaVals,
        smooth: true,
        symbol: 'none',
        connectNulls: false,
        lineStyle: { color: '#38bdf8', width: 1.5 },
        areaStyle: { color: gradV('rgba(56,189,248,0.18)', 'rgba(56,189,248,0.02)') }
      }
    ]
  })
}
