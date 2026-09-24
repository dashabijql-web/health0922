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
