import { markRaw } from 'vue'
import * as echarts from '@/utils/echarts-setup-radar'

/**
 * 把 ECharts 实例绑定到 bucket[key] 上：同一个 DOM 且实例未销毁则复用，否则销毁旧实例、创建新实例。
 * 实例用 markRaw 包起来，避免被 Vue 深度代理。
 */
export function bindDashboardChart(bucket, key, dom, theme?: string) {
  if (!bucket || !dom) return null
  const current = bucket[key]
  if (current && current.isDisposed?.() === false && current.getDom?.() === dom) {
    return current
  }
  try {
    current?.dispose?.()
  } catch { /* 旧实例已失效，忽略 */ }
  const chart = markRaw(theme ? echarts.init(dom, theme) : echarts.init(dom))
  bucket[key] = chart
  return chart
}
