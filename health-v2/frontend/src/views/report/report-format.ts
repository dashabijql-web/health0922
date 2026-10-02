// 月报页面上的文字和数字格式（docs/07 第二部分）。只做显示，不算业务数字。
import type { AlertCategory, MetricRule, RiskPerson, Share, StabilityMetric } from '@/api/report'

/** 每页页脚的免责声明（docs/07 第二部分"二、13 页"、docs/01 第五节"健康提示仅供参考"） */
export const DISCLAIMER = '本报告的结论或建议仅供参考，不能作为临床或治疗的依据。任何医疗行为，请以专业医生的诊断为准。'

/** 页码不含封面和封底 */
export const PAGE_TOTAL = 13

/** 第 5、8–10 页的三项指标，顺序同截图 8：心率、体温、血氧 */
export const METRICS: StabilityMetric[] = ['HEART_RATE', 'TEMPERATURE', 'SPO2']

export const METRIC_NAME: Record<StabilityMetric, { name: string; unit: string; label: string }> = {
  HEART_RATE: { name: '心率', unit: '次/分', label: '心率(次/分)' },
  TEMPERATURE: { name: '体温', unit: '℃', label: '体温(℃)' },
  SPO2: { name: '血氧', unit: '%', label: '血氧(%)' }
}

/** 六类告警，名字同大屏格子 */
export const ALERT_NAME: Record<AlertCategory, string> = {
  SOS: 'SOS',
  FALL: '跌倒',
  HEART_RATE: '心率',
  BLOOD_PRESSURE: '血压',
  SPO2: '血氧',
  TEMPERATURE: '体温'
}

/** "2026-08" → "2026年08月" */
export function monthLabel(month: string | null | undefined): string {
  if (!month) return ''
  return `${month.slice(0, 4)}年${month.slice(5, 7)}月`
}

/** 饼图和表格里的名字：厂家没填的写"未录入" */
export function shareName(s: Share): string {
  return s.kind === 'UNRECORDED' || s.name === null ? '未录入' : s.name
}

/** 体温总带一位小数；心率、血氧是整数时不带 ".0" */
export function vitalText(metric: StabilityMetric, v: number | null | undefined): string {
  if (v === null || v === undefined) return '暂无数据'
  if (metric === 'TEMPERATURE') return v.toFixed(1)
  return Number.isInteger(v) ? String(v) : v.toFixed(1)
}

/** 正常范围：50–120 / ≥ 90 / ≤ 37.3；阈值停用时写"未启用（全部算正常）" */
export function rangeText(metric: StabilityMetric, r: MetricRule): string {
  if (!r.enabled) return '未启用（全部算正常）'
  const f = (v: number) => vitalText(metric, v)
  if (r.low !== null && r.high !== null) return `${f(r.low)}–${f(r.high)}`
  if (r.low !== null) return `≥ ${f(r.low)}`
  if (r.high !== null) return `≤ ${f(r.high)}`
  return '不限'
}

/** 风险职工的"主要问题"：如"心率不稳定；血氧偏低告警 5 次" */
export function riskProblems(p: RiskPerson): string {
  const parts = p.unstable.map((m) => `${METRIC_NAME[m].name}不稳定`)
  for (const a of p.alerts) parts.push(`${a.name}告警 ${a.count} 次`)
  return parts.join('；')
}

/** 两位数名次 "01" */
export function rankText(rank: number): string {
  return String(rank).padStart(2, '0')
}
