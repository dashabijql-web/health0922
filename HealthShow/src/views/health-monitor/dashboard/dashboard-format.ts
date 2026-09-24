import dayjs from 'dayjs'

export function alertTypeLabel(type) {
  const map = { 1: '心率异常', 2: '血氧异常', 3: '体温异常', 4: '血压异常', 5: '综合异常' }
  return map[type] || '其他'
}

export function formatWarnTime(time) {
  if (!time) return '--'
  return dayjs(time).format('MM-DD HH:mm')
}

export function formatTimeAgo(timestamp) {
  if (!timestamp) return ''
  const diff = Date.now() - new Date(timestamp).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  return dayjs(timestamp).format('MM-DD HH:mm')
}

/** 顶部指标条的色调：kpi-red -> danger 等 */
export function resolveHeaderMetricTone(cls: string) {
  if (cls === 'kpi-red') return 'danger'
  if (cls === 'kpi-orange') return 'warning'
  if (cls === 'kpi-green' || cls === 'kpi-teal') return 'success'
  return 'primary'
}

/** 顶部指标条的备注：压成一行，最多 18 个字 */
export function normalizeHeaderMetricNote(note: unknown) {
  if (!note) return ''
  const normalized = String(note).replace(/\s+/g, ' ').trim()
  if (normalized === '数据加载中...') return '等待刷新'
  return normalized.length > 18 ? `${normalized.slice(0, 18)}…` : normalized
}
