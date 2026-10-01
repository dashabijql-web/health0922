// 健康档案、个人档案、名单的接口（docs/05 第六～八节）。
// 拿不到的数据后端给 null，页面显示"暂无数据"/"未录入"，不显示 0（docs/05 第二节）。
import { del, get, post, put } from './request'
import type { Page, WatchState } from './dashboard'
import type { Vital, VitalKey } from './map'

export type { Vital, VitalKey }

export interface ArchiveFilters {
  depts: string[]
  jobKinds: string[]
}

export interface ArchivePerson {
  cardCode: string
  name: string | null
  jobKind: string | null
  dept: string | null
  /** 人工录入；没录入为 null（显示"未录入"） */
  age: number | null
}

export interface ArchiveQuery {
  dept: string | null
  jobKind: string | null
  keyword: string
}

export type ListType = 'KEY' | 'TODAY'

/** 名单里的一条 */
export interface ListEntry {
  id: number
  note: string | null
  /** 今日关注是当天，重点监护为 null */
  expireDate: string | null
  addedBy: string
  addedAt: string
}

export interface PersonDetail {
  cardCode: string
  name: string | null
  jobKind: string | null
  dept: string | null
  age: number | null
  ageUpdatedBy: string | null
  ageUpdatedAt: string | null
  watch: { state: WatchState; lastSeenAt: string | null }
  /** 没绑定手表或没收到过为 null */
  battery: { pct: number; time: string } | null
  /** 四项都在；从没测过的为 null */
  vitals: Record<VitalKey, Vital | null>
  /** 今天的步数；今天没有记录为 null */
  steps: { steps: number; updatedAt: string } | null
  /** 不在井下为 null */
  position: {
    areaName: string | null
    stationName: string | null
    placed: boolean
    posTime: string
    stationAbnormal: boolean
  } | null
  /** 不在名单里（或今日关注已过期）为 null */
  lists: Record<ListType, ListEntry | null>
}

export interface TrendPoint {
  time: string
  value: number
  /** 血压的舒张压，其余为 null */
  value2: number | null
}

export interface Trend {
  metric: VitalKey
  date: string
  /** 相邻两点隔得比它久，折线在那里断开 */
  gapMinutes: number
  points: TrendPoint[]
}

export interface StepDay {
  date: string
  /** 那天没有记录为 null */
  steps: number | null
}

export interface AlertRecord {
  id: number
  occurredAt: string
  lastOccurredAt: string
  /** THRESHOLD 体征越界 / DEVICE 设备报警 */
  src: 'THRESHOLD' | 'DEVICE'
  /** 六类之一；设备事件为 OTHER */
  category: string
  event: string
  /** 体征越界时触发的值；设备报警为 null */
  value: string | null
  count: number
}

export interface WatchListItem {
  id: number
  cardCode: string
  name: string | null
  dept: string | null
  type: ListType
  note: string | null
  expireDate: string | null
  addedBy: string
  addedAt: string
}

export function fetchArchiveFilters(): Promise<ArchiveFilters> {
  return get('/archive/filters', { silent: true })
}

export function fetchArchivePersons(q: ArchiveQuery, page: number, size: number): Promise<Page<ArchivePerson>> {
  return get('/archive/persons', {
    params: { dept: q.dept ?? undefined, jobKind: q.jobKind ?? undefined, keyword: q.keyword || undefined, page, size },
    silent: true
  })
}

export function fetchPersonDetail(cardCode: string): Promise<PersonDetail> {
  return get(`/archive/persons/${cardCode}`, { silent: true })
}

/** @param date "yyyy-MM-dd"，不传是今天 */
export function fetchTrend(cardCode: string, metric: VitalKey, date?: string): Promise<Trend> {
  return get(`/archive/persons/${cardCode}/trend`, { params: { metric, date }, silent: true })
}

export async function fetchSteps(cardCode: string, days: number): Promise<StepDay[]> {
  const r = await get<{ days: StepDay[] }>(`/archive/persons/${cardCode}/steps`, { params: { days }, silent: true })
  return r.days
}

export function fetchPersonAlerts(cardCode: string, page: number, size: number): Promise<Page<AlertRecord>> {
  return get(`/archive/persons/${cardCode}/alerts`, { params: { page, size }, silent: true })
}

/** 录入年龄（16–75），失败时弹出后端的提示 */
export function saveAge(cardCode: string, age: number): Promise<{ age: number; updatedBy: string; updatedAt: string }> {
  return put(`/archive/persons/${cardCode}/age`, { age })
}

export function addToList(cardCode: string, type: ListType, note?: string): Promise<WatchListItem> {
  return post('/watch-list', { cardCode, type, note })
}

export function removeFromList(id: number): Promise<void> {
  return del(`/watch-list/${id}`)
}
