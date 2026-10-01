// 地图模式、基站摆放的接口（docs/05 第五节、docs/06 第五节）。坐标是 EPSG:4527 的原始数字（先东后北，米）。
import { del, get, put } from './request'
import type { Page, WatchState } from './dashboard'

export interface MapConfig {
  /** GeoServer WMS 地址（站内路径，由前端开发服务器 / nginx 转给 GeoServer） */
  wmsUrl: string
  /** 固定 1.1.1（docs/06 第二节轴顺序） */
  wmsVersion: string
  lineLayer: string
  labelLayer: string
  projection: string
  /** [minX, minY, maxX, maxY] */
  extent: [number, number, number, number]
}

export interface MapPerson {
  cardCode: string
  name: string | null
  dept: string | null
  areaName: string | null
  stationCode: string | null
  stationName: string | null
  /** 所在基站摆放了没有；没摆放时 x、y 为 null，地图上不画 */
  placed: boolean
  x: number | null
  y: number | null
  /** 定位时间：这条位置依据的那份 RYSS 的数据上传时间 */
  posTime: string
  watchState: WatchState
  /** 有"仍在发生"的六类告警 */
  alerting: boolean
}

export type MapFilter = 'all' | 'key' | 'today'

export interface Vital {
  value: number
  value2: number | null
  collectedAt: string
  /** 超过"数据较旧"阈值没更新 */
  stale: boolean
}

export type VitalKey = 'HEART_RATE' | 'SPO2' | 'TEMPERATURE' | 'BLOOD_PRESSURE'

export interface PersonCard {
  cardCode: string
  name: string | null
  dept: string | null
  /** 不在井下时为 null */
  position: {
    areaName: string | null
    stationCode: string | null
    stationName: string | null
    placed: boolean
    x: number | null
    y: number | null
    posTime: string
    stationEnterTime: string | null
    stationRunStatus: number | null
    /** 所在基站通讯中断或故障，位置可能不准 */
    stationAbnormal: boolean
  } | null
  vitals: Record<VitalKey, Vital | null>
  battery: { pct: number; time: string | null } | null
  watch: { state: WatchState; lastSeenAt: string | null }
  alerts: { occurredAt: string; lastOccurredAt: string; category: string; event: string; value: string | null }[]
}

export interface Station {
  stationCode: string
  /** 显示名称：用户起的名字 > 厂家名称 > 区域名称 + 编码后 6 位 */
  name: string
  vendorName: string | null
  displayName: string | null
  areaCode: string
  areaName: string | null
  /** 0 通讯正常、1 通讯中断、2 故障、9 未知；还没收到过为 null */
  runStatus: number | null
  placed: boolean
  x: number | null
  y: number | null
  /** 摆放的版本号，改、删时带回去；未摆放为 0 */
  version: number
  updatedBy: string | null
  updatedAt: string | null
}

export interface StationList {
  total: number
  unplaced: number
  list: Station[]
}

export interface MarkSnapshot {
  x: number
  y: number
  displayName: string | null
}

export type StationAction = 'STATION_PLACE' | 'STATION_MOVE' | 'STATION_RENAME' | 'STATION_DELETE'

export interface StationLog {
  id: number
  username: string
  action: StationAction
  stationCode: string
  stationName: string
  before: MarkSnapshot | null
  after: MarkSnapshot | null
  createdAt: string
}

// 地图每 30 秒刷新，失败由地图角落显示"刷新失败"，不弹提示
const quiet = { silent: true }

export const fetchMapConfig = () => get<MapConfig>('/map/config', quiet)
export const fetchMapPersons = (filter: MapFilter, keyword?: string) =>
  get<MapPerson[]>('/map/persons', { ...quiet, params: { filter, keyword: keyword || undefined } })
export const fetchStations = () => get<StationList>('/map/stations', quiet)

// 用户点开、提交的，出错要提示
/** silent：定时刷新已打开的体征卡时不弹提示 */
export const fetchPersonCard = (cardCode: string, silent = false) =>
  get<PersonCard>(`/map/persons/${cardCode}/card`, { silent })
export const searchMapPersons = (keyword: string) =>
  get<MapPerson[]>('/map/persons', { params: { filter: 'all', keyword } })
export const fetchStationLogs = (stationCode: string | null, page: number, size: number) =>
  get<Page<StationLog>>('/map/station-logs', { params: { stationCode: stationCode ?? undefined, page, size } })

/** 新增（version 传 0）或修改摆放。冲突时后端返回 409，提示"这个基站刚刚被 xxx 修改，请刷新" */
// 摆放的出错提示由摆放面板自己显示（409 要刷新列表），这里不弹
export const saveStationMark = (
  stationCode: string,
  body: { x: number; y: number; displayName: string | null; version: number }
) => put<Station>(`/map/stations/${stationCode}/mark`, body, quiet)
export const deleteStationMark = (stationCode: string, version: number) =>
  del<void>(`/map/stations/${stationCode}/mark`, { ...quiet, params: { version } })
