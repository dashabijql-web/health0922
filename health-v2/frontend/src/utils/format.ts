// 页面上的显示格式（docs/05 第二节）。后端给的时间是北京时间 "yyyy-MM-dd HH:mm:ss"，原样截取，不经过浏览器时区换算。

/** 卡号：卡编码后 5 位（完整 17 位放在悬停提示里） */
export function cardNo(cardCode: string | null | undefined): string {
  return cardCode ? cardCode.slice(-5) : ''
}

/** "2026-09-24 10:13:19" → "10:13" */
export function hhmm(dateTime: string | null | undefined): string {
  return dateTime ? dateTime.slice(11, 16) : ''
}

/** "2026-09-24 10:13:19" → "10:13:19" */
export function hhmmss(dateTime: string | null | undefined): string {
  return dateTime ? dateTime.slice(11, 19) : ''
}

/** 今天的只写时分，不是今天的带上月日，免得把昨天的数据看成今天的 */
export function shortTime(dateTime: string | null | undefined, today = localDate()): string {
  if (!dateTime) return ''
  return dateTime.startsWith(today) ? hhmm(dateTime) : `${dateTime.slice(5, 10)} ${hhmm(dateTime)}`
}

/** 浏览器本地的今天 "yyyy-MM-dd"（只用来判断要不要写日期） */
export function localDate(d = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 浏览器本地的现在 "HH:mm"（"刷新失败，显示的是 hh:mm 的数据"用） */
export function localHhmm(d = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

/** "yyyy-MM-dd HH:mm:ss" → 毫秒（按北京时间解析，和浏览器所在时区无关） */
export function parseBeijing(dateTime: string): number {
  return Date.parse(`${dateTime.replace(' ', 'T')}+08:00`)
}
