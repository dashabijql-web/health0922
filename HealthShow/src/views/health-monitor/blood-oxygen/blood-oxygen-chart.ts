import { emptyOption, categoryAxis, valueAxis, deptGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, type ChartStore } from '@/utils/chart-helpers'

export function renderBloodOxygenDept(charts: ChartStore, el: HTMLElement | null | undefined, data: Array<Record<string, any>>, onSelectDept: (name: string) => void) {
  const c = initChart(charts, 'dept', el)
  if (!c) return
  if (!data.length) {
    c.setOption(emptyOption())
    return
  }
  const d = data.map(x => ({
    deptName: x.deptName || x.name,
    lowCount: x.lowCount || 0,
    highCount: x.highCount || 0
  }))
  c.setOption({
    backgroundColor: 'transparent',
    grid: deptGrid(),
    xAxis: valueAxis(),
    yAxis: { ...categoryAxis(d.map(x => x.deptName), { show: false }), inverse: true },
    series: [
      {
        name: '偏低',
        type: 'bar',
        stack: 'total',
        barWidth: '46%',
        data: d.map(x => x.lowCount),
        itemStyle: { color: gradH('#FFB84D', '#FFA726') },
        label: barLabel()
      },
      {
        name: '优秀',
        type: 'bar',
        stack: 'total',
        barWidth: '46%',
        data: d.map(x => x.highCount),
        itemStyle: { color: gradH('#4FC3F7', '#29B6F6'), borderRadius: [0, 4, 4, 0] },
        label: barLabel()
      }
    ]
  })
  c.off('click')
  c.on('click', (params) => {
    onSelectDept(params.name)
  })
}
