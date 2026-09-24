import { nextTick, ref, type Ref } from 'vue'
import dayjs from 'dayjs'
import type { EChartsType } from 'echarts/core'
import { getHealthRecords } from '@/api/health'
import { renderVitalChart } from './risk-warning-charts'

type Row = Record<string, any>

export interface VitalSummaryItem { label: string; val: string; sub: string; color: string }

/**
 * 预警详情抽屉里的“预警时刻前后体征曲线”。
 * 取数策略：先以预警时间为中心自动扩窗（±1h → ±3h → ±6h），直到数据点 ≥ 3；
 * 仍不足 3 个点时，退到员工画像里的近 7 天日均趋势；都没有则显示空态。
 */
export function useWarningVital(chartEl: Ref<HTMLElement | null>) {
  const vitalLoading = ref(false)
  const vitalEmpty = ref(false)
  const vitalChartRange = ref('')
  const vitalSummary = ref<VitalSummaryItem[]>([])
  let chart: EChartsType | null = null

  function dispose() {
    if (chart) { chart.dispose(); chart = null }
  }

  /** 打开抽屉时调用：先清空旧状态，等抽屉里的图表容器渲染出来再取数 */
  async function open(item: Row) {
    vitalLoading.value = true
    vitalEmpty.value = false
    vitalSummary.value = []
    await nextTick()
    await load(item)
  }

  async function load(item: Row) {
    try {
      const warnTime = dayjs(item.createTime)
      const empCode = item.empCode || item.userCode || item.empId || ''
      let list: Row[] = []
      let isDaily = false // 是否降级为按天

      // 第一层：自动扩窗
      for (const hours of [1, 3, 6]) {
        const start = warnTime.subtract(hours, 'hour')
        const end = warnTime.add(hours, 'hour')
        try {
          const r = await getHealthRecords({
            pageNum: 1, pageSize: 200,
            empCode, userCode: empCode,
            startTime: start.format('YYYY-MM-DD HH:mm:ss'),
            endTime: end.format('YYYY-MM-DD HH:mm:ss')
          })
          const rows: Row[] = r?.data?.list || r?.data?.records || (Array.isArray(r?.data) ? r.data : [])
          const range = `${start.format('HH:mm')} – ${end.format('HH:mm')}（±${hours}h）`
          if (rows.length >= 3) {
            list = rows
            vitalChartRange.value = range
            break
          } else if (rows.length > 0 && list.length === 0) {
            // 暂存最好结果，继续尝试更大窗口
            list = rows
            vitalChartRange.value = range
          }
        } catch { /* 这一档窗口取不到，换更大的窗口重试 */ }
      }

      // 第二层兜底：数据点仍 < 3，改用员工画像的近 7 天日均趋势
      if (list.length < 3 && empCode) {
        try {
          const { getHealthPortrait } = await import('@/api/health-portrait')
          const pr = await getHealthPortrait(empCode)
          const td = pr?.data?.trendData || pr?.data?.trend || pr?.data
          const dates: string[] = td?.dates || []
          const hArr = td?.heartRates || td?.heartRate || []
          const sArr = td?.bloodOxygens || td?.bloodOxygen || []
          const tArr = td?.temperatures || td?.temperature || []
          if (dates.length >= 2) {
            list = dates.map((d, i) => ({
              recordTime: d + ' 00:00:00',
              heartRate: hArr[i] || null,
              bloodOxygen: sArr[i] || null,
              temperature: tArr[i] || null
            }))
            isDaily = true
            vitalChartRange.value = '近7天日均趋势（采样间隔稀疏）'
          }
        } catch { /* 兜底也失败，按空态处理 */ }
      }

      if (!list.length) { vitalEmpty.value = true; vitalLoading.value = false; return }

      const timeOf = (d: Row) => d.time || d.recordTime || d.createTime
      list.sort((a, b) => new Date(timeOf(a)).getTime() - new Date(timeOf(b)).getTime())

      const times = list.map((d) => (isDaily ? dayjs(timeOf(d)).format('MM/DD') : dayjs(timeOf(d)).format('HH:mm')))
      const hrs = list.map((d) => (d.heartRate ? +d.heartRate : null))
      const spo2 = list.map((d) => (d.bloodOxygen ? +d.bloodOxygen : null))
      const temps = list.map((d) => {
        if (!d.temperature) return null
        const t = +d.temperature
        return t > 100 ? +(t / 10).toFixed(1) : +t.toFixed(1)
      })

      const validHr = hrs.filter((v): v is number => v != null)
      const validSpo2 = spo2.filter((v): v is number => v != null)
      const validTemp = temps.filter((v): v is number => v != null)
      vitalSummary.value = [
        { label: '最高心率', val: validHr.length ? Math.max(...validHr) + 'bpm' : '--', sub: '正常 60-100', color: '#ef4444' },
        { label: '最低血氧', val: validSpo2.length ? Math.min(...validSpo2) + '%' : '--', sub: '正常 ≥95%', color: '#f97316' },
        { label: '峰值体温', val: validTemp.length ? Math.max(...validTemp) + '°C' : '--', sub: '正常 36-37.5', color: '#22c55e' },
        { label: '数据点数', val: list.length + '条', sub: '采样点', color: '#00d4ff' }
      ]

      vitalLoading.value = false
      await nextTick()
      chart = renderVitalChart(chartEl.value, chart, times, hrs, spo2, temps, isDaily ? '' : warnTime.format('HH:mm'))
    } catch {
      vitalEmpty.value = true
      vitalLoading.value = false
    }
  }

  return { vitalLoading, vitalEmpty, vitalChartRange, vitalSummary, openVital: open, disposeVital: dispose }
}
