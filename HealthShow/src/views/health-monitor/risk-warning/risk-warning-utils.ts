import dayjs from 'dayjs'

/** 预警级别 → 列表行/徽章的样式类：高危=critical，中度=high，轻度=medium，低危/正常=low */
export function warnClass(level?: string | null) {
  if (!level) return 'normal'
  const lvl = String(level).trim()
  if (['高危', '严重', '危险', '高'].includes(lvl)) return 'critical'
  if (['中度', '中', '警告'].includes(lvl)) return 'high'
  if (['轻度', '低', '提醒'].includes(lvl)) return 'medium'
  if (['低危', '正常'].includes(lvl)) return 'low'
  return 'normal'
}

export const fmtTime = (ts?: string | null) => (ts ? dayjs(ts).format('MM-DD HH:mm') : '--')
export const fmtTimeFull = (ts?: string | null) => (ts ? dayjs(ts).format('YYYY-MM-DD HH:mm:ss') : '--')

/**
 * 预警的定位键：预警表按月分表，只用 id 会串月，必须是 id + 产生时间（对应后端 warningId + occurredAt）。
 */
export const warningLocatorKey = (item?: { id?: unknown; createTime?: unknown } | null) =>
  `${item?.id ?? ''}@@${item?.createTime ?? ''}`
