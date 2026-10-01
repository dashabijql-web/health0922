// 入口页、动态数据页、佩戴情况导出的接口（docs/05 第三、四节）。
// 拿不到的数据后端给 null，页面显示"暂无数据"/"未录入"，不显示 0（docs/05 第二节）。
import { download, get } from './request'

/** 带分页的接口返回（docs/01 第五节），总数由后端算 */
export interface Page<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface PositioningFreshness {
  /** 最新一份生效的 RYSS 的数据上传时间，"yyyy-MM-dd HH:mm:ss"；从没收到过时为 null */
  dataTime: string | null
  stale: boolean
}

export interface Stat {
  max: number
  min: number
  avg: number
}

export type VitalKey = 'HEART_RATE' | 'SPO2' | 'TEMPERATURE' | 'STEPS'

export interface PortalSummary {
  dataTime: string | null
  vitals: Record<VitalKey, Stat | null>
  keyPersonCount: number
  todayWatchCount: number
  inWellCount: number | null
  deviceCount: number
  positioning: PositioningFreshness
}

export type CollectedKey = 'HEART_RATE' | 'SPO2' | 'TEMPERATURE' | 'BLOOD_PRESSURE'
export type AlertCategory = 'SOS' | 'FALL' | 'HEART_RATE' | 'BLOOD_PRESSURE' | 'SPO2' | 'TEMPERATURE'

export interface Overview {
  /** 手表数据时间：库里最新一条体征的采集时间 */
  dataTime: string | null
  totalCollected: number
  collected: Record<CollectedKey, number>
  alerts: Record<AlertCategory, number>
  alertWindow: string
  deviceEventCount: number
}

export interface AlertPerson {
  cardCode: string | null
  deviceImei: string | null
  name: string | null
  dept: string | null
  eventCount: number
  occurTotal: number
  lastAt: string
  lastEvent: string
  lastValue: string | null
}

export interface DeviceEvent {
  id: number
  cardCode: string | null
  deviceImei: string | null
  name: string | null
  code: string
  event: string
  occurredAt: string
  lastOccurredAt: string
  occurCount: number
}

export interface KeyPerson {
  cardCode: string
  name: string | null
  dept: string | null
}

export interface HeadcountSeries {
  date: string
  /** 相邻两点隔得比它久就断开不连线（分钟） */
  gapMinutes: number
  points: { time: string; inWell: number }[]
}

export interface Headcount {
  inWell: number | null
  expected: number | null
  online: number | null
  offline: number | null
  onlineNormal: number | null
  onlineAlert: number | null
  positioning: PositioningFreshness
}

export type WatchState = 'ONLINE' | 'OFFLINE' | 'UNBOUND'

export interface InWellPerson {
  cardCode: string
  name: string | null
  dept: string | null
  areaName: string | null
  watchState: WatchState
  /** 所在基站在地图上摆放了没有（地图模式下没摆放的标"基站未摆放"） */
  stationPlaced: boolean
}

export interface StepRank {
  rank: number
  cardCode: string
  name: string | null
  dept: string | null
  steps: number
  /** 进度条长度（0–100），后端按第一名算好 */
  barPct: number
}

// 大屏每 30 秒刷新一次，失败由面板自己显示"刷新失败"，不弹提示
const quiet = { silent: true }

export const fetchPortalSummary = () => get<PortalSummary>('/portal/summary', quiet)
export const fetchOverview = () => get<Overview>('/dashboard/overview', quiet)
export const fetchHeadcount = () => get<Headcount>('/dashboard/headcount', quiet)
export const fetchKeyPersons = () => get<KeyPerson[]>('/dashboard/key-persons', quiet)
export const fetchHeadcountSeries = (date?: string) =>
  get<HeadcountSeries>('/dashboard/headcount-series', { ...quiet, params: { date } })
export const fetchStepsRank = (limit = 10) => get<StepRank[]>('/dashboard/steps-rank', { ...quiet, params: { limit } })
export const fetchInWellPersons = (keyword: string, page: number, size: number) =>
  get<Page<InWellPerson>>('/dashboard/in-well-persons', { ...quiet, params: { keyword: keyword || undefined, page, size } })

// 弹层里的列表是用户点开的，出错要提示
export const fetchAlertPersons = (category: AlertCategory, page: number, size: number) =>
  get<Page<AlertPerson>>('/dashboard/alerts', { params: { category, page, size } })
export const fetchDeviceEvents = (page: number, size: number) =>
  get<Page<DeviceEvent>>('/dashboard/device-events', { params: { page, size } })

/** 佩戴情况 Excel；date 不传时后端按昨天 */
export const downloadWearExcel = (date?: string) =>
  download('/export/wear', { params: { date }, timeout: 60000 })
