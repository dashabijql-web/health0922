// 月度汇总的接口（docs/07 第二部分"四、接口"）。某一块没有数据时后端给 null，页面显示"暂无数据"。
import { get } from './request'

export type StabilityMetric = 'HEART_RATE' | 'TEMPERATURE' | 'SPO2'
export type AlertCategory = 'SOS' | 'FALL' | 'HEART_RATE' | 'BLOOD_PRESSURE' | 'SPO2' | 'TEMPERATURE'

export interface ReportMonths {
  /** 有数据的月份，从早到晚，如 "2026-08" */
  months: string[]
  /** 默认打开的月份：上个月有数据就是上个月，否则最近有数据的月份；一个都没有为 null */
  defaultMonth: string | null
}

/** 饼图和表格的一行 */
export interface Share {
  /** kind 为 OTHER 时是"其他"，UNRECORDED 时为 null（显示"未录入"） */
  name: string | null
  kind: 'NAMED' | 'OTHER' | 'UNRECORDED'
  count: number
  /** 百分比，两位小数 */
  percent: number
}

export interface VitalStat {
  max: number
  min: number
  /** 按每天样本数加权的平均，一位小数 */
  avg: number
  samples: number
  persons: number
}

export interface MetricRule {
  low: number | null
  high: number | null
  enabled: boolean
  stablePct: number
  unstablePct: number
}

/** 一个岗位类别的评估参数（没配的已经用默认类别的补齐） */
export interface GroupRule {
  code: string
  name: string
  minEvalDays: number
  riskEventCount: number
  metrics: Record<StabilityMetric, MetricRule>
}

export interface UnstablePerson {
  cardCode: string
  name: string | null
  dept: string | null
  groupName: string
  /** 正常率（%），一位小数，向下取整 */
  normalPct: number
  min: number
  max: number
  days: number
}

export interface Stability {
  /** 有这项数据的人数 = 稳定 + 波动 + 不稳定 + 不参与 */
  persons: number
  stable: number
  fluctuating: number
  unstable: number
  notEvaluated: number
  /** "不稳定"里正常率最低的前 10 人 */
  unstableList: UnstablePerson[]
}

export interface StepRank {
  rank: number
  cardCode: string
  name: string | null
  dept: string | null
  /** 日均步数（四舍五入到整数） */
  avgSteps: number
  days: number
}

export interface RiskPerson {
  cardCode: string
  name: string | null
  dept: string | null
  groupName: string
  /** 本月六类告警事件数 */
  alertCount: number
  /** 被评为"不稳定"的指标 */
  unstable: StabilityMetric[]
  /** 六类告警按事件代码分的次数，name 是中文名（如"心率偏高"） */
  alerts: { code: string; name: string; count: number }[]
}

export interface MonthlyReport {
  month: string
  /** 这份数据算出来的时间（已结束的月份缓存 1 天） */
  generatedAt: string
  overview: { workerCount: number; jobKindCount: number; byJobKind: Share[] } | null
  watchUsage: { userCount: number; deptCount: number; byDept: Share[] } | null
  vitals: Record<StabilityMetric, VitalStat | null> | null
  alerts: { persons: number; events: number; byCategory: { category: AlertCategory; persons: number; events: number }[] }
    | null
  /** 各岗位类别的评估参数，默认类别在最前；任何月份都有 */
  rules: GroupRule[]
  stability: Record<StabilityMetric, Stability | null> | null
  steps: {
    minDays: number
    /** 参与排行的人数 */
    ranked: number
    /** 有步数、但天数不够没参与排行的人数 */
    excluded: number
    low: StepRank[]
    high: StepRank[]
  } | null
  risk: { total: number; list: RiskPerson[] } | null
}

export function fetchReportMonths(): Promise<ReportMonths> {
  return get<ReportMonths>('/report/months', { silent: true })
}

export function fetchMonthlyReport(month: string): Promise<MonthlyReport> {
  return get<MonthlyReport>('/report/monthly', { params: { month }, silent: true })
}
