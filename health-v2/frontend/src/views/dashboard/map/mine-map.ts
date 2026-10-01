// 矿图：OpenLayers + proj4（docs/06 第二、四节）。这里只放和 Vue 无关的部分：坐标系、图层、样式、按基站合并人员。
// 图层从下往上：巷道线（WMS）、巷道名称（WMS）、基站、人员、正在摆放的基站。
import proj4 from 'proj4'
import OlMap from 'ol/Map'
import View from 'ol/View'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'
import ImageLayer from 'ol/layer/Image'
import VectorLayer from 'ol/layer/Vector'
import ImageWMS from 'ol/source/ImageWMS'
import VectorSource from 'ol/source/Vector'
import { defaults as defaultInteractions } from 'ol/interaction/defaults'
import { register } from 'ol/proj/proj4'
import { get as getProjection, type ProjectionLike } from 'ol/proj'
import { buffer as bufferExtent, type Extent } from 'ol/extent'
import { Circle as CircleStyle, Fill, Icon, Stroke, Style, Text } from 'ol/style'
import type { FeatureLike } from 'ol/Feature'
import type { MapConfig, MapPerson, Station } from '@/api/map'

/** 坐标系定义（docs/06 第二节）。CAD 坐标东向带 39 带号前缀，所以 x_0 = 39500000 */
const PROJ_DEFS: Record<string, string> = {
  'EPSG:4527': '+proj=tmerc +lat_0=0 +lon_0=117 +k=1 +x_0=39500000 +y_0=0 +ellps=GRS80 +units=m +no_defs'
}

/** 比这个分辨率（米/像素）更粗时不画基站：整个井田都在屏幕上时，基站图标挤成一片 */
export const STATION_HIDE_RESOLUTION = 4
/** 定位到某人时放大到的分辨率（米/像素） */
export const LOCATE_RESOLUTION = 1.2

/**
 * 注册坐标系，并给它设上地图范围。不设范围时 OpenLayers 按"全世界"（原点附近 ±20037508 米）建默认网格，
 * 而我们的东向坐标约 39480000，根本不在里面，缩放级别也是按全世界算的（docs/10 第 7 项，阶段 4 核对）。
 */
export function mineProjection(code: string, extent: Extent): ProjectionLike {
  if (!proj4.defs(code)) {
    const def = PROJ_DEFS[code]
    if (!def) throw new Error(`不认识的坐标系 ${code}`)
    proj4.defs(code, def)
    register(proj4)
  }
  const projection = getProjection(code)
  if (!projection) throw new Error(`坐标系 ${code} 注册失败`)
  projection.setExtent(extent)
  return projection
}

export interface MineMap {
  map: OlMap
  stations: VectorSource
  persons: VectorSource
  editing: VectorSource
  stationLayer: VectorLayer
  personLayer: VectorLayer
  editLayer: VectorLayer
  extent: Extent
  destroy: () => void
}

/**
 * 底图用 WMS 1.1.1：EPSG:4527 官方轴顺序是"先北后东"，WMS 1.3.0 会按官方顺序理解 BBOX，
 * 而 CAD 和我们的数据是"先东后北"，用 1.3.0 底图会整个错位或空白；1.1.1 永远先东后北（docs/06 第二节）。
 * 用 ImageWMS（一次请求一整张图）而不是瓦片：巷道名称不会在瓦片边上被切断或重复。
 */
function wmsLayer(config: MapConfig, layer: string, projection: ProjectionLike, onLoad: (ok: boolean) => void) {
  const source = new ImageWMS({
    url: config.wmsUrl,
    params: { LAYERS: layer, STYLES: '', VERSION: config.wmsVersion, FORMAT: 'image/png', TRANSPARENT: true },
    projection,
    ratio: 1.5,
    serverType: 'geoserver'
  })
  source.on('imageloaderror', () => onLoad(false))
  source.on('imageloadend', () => onLoad(true))
  return new ImageLayer({ source })
}

/** @param onBaseMap 底图图片每次加载完调用：ok = false 表示 GeoServer 没有正常返回 */
export function createMineMap(target: HTMLElement, config: MapConfig, onBaseMap: (ok: boolean) => void): MineMap {
  const extent = config.extent as Extent
  const projection = mineProjection(config.projection, extent)
  const stations = new VectorSource()
  const persons = new VectorSource()
  const editing = new VectorSource()
  const stationLayer = new VectorLayer({ source: stations, style: stationStyle })
  const personLayer = new VectorLayer({ source: persons, style: personStyle })
  const editLayer = new VectorLayer({ source: editing, style: stationStyle })

  const view = new View({
    projection,
    // 允许拖到范围外一点，免得边上的巷道贴着屏幕边
    extent: bufferExtent(extent, 600),
    constrainOnlyCenter: true,
    maxResolution: 16,
    minResolution: 0.08
  })
  const map = new OlMap({
    target,
    controls: [],
    interactions: defaultInteractions({ altShiftDragRotate: false, pinchRotate: false }),
    layers: [
      wmsLayer(config, config.lineLayer, projection, onBaseMap),
      wmsLayer(config, config.labelLayer, projection, onBaseMap),
      stationLayer,
      personLayer,
      editLayer
    ],
    view
  })
  view.fit(extent, { size: map.getSize(), padding: [70, 30, 30, 30] })
  return {
    map,
    stations,
    persons,
    editing,
    stationLayer,
    personLayer,
    editLayer,
    extent,
    destroy: () => map.setTarget(undefined)
  }
}

// ---------------------------------------------------------------- 基站

/** 基站运行状态的颜色：通讯正常青绿、中断灰、故障红；还不知道的也按灰（docs/06 第四节） */
const STATION_COLOR: Record<number, string> = { 0: '#19e6b0', 1: '#8a9a97', 2: '#ff4d4f' }
const STATION_UNKNOWN = '#8a9a97'

function towerSvg(color: string): string {
  return (
    '<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 22 22">' +
    `<circle cx="11" cy="11" r="10" fill="#04201c" fill-opacity="0.85" stroke="${color}" stroke-width="1.5"/>` +
    `<path d="M11 7.5v9M8.2 16.5L11 8l2.8 8.5M9 13.5h4" stroke="${color}" stroke-width="1.5" fill="none" stroke-linecap="round"/>` +
    `<path d="M7.5 6.5a5 5 0 0 0 0 5M14.5 6.5a5 5 0 0 1 0 5" stroke="${color}" stroke-width="1.2" fill="none" stroke-linecap="round"/>` +
    '</svg>'
  )
}

const iconCache = new Map<string, Icon>()
function svgIcon(svg: string, opts: { anchor?: [number, number]; scale?: number } = {}): Icon {
  const key = `${svg}|${opts.anchor}|${opts.scale}`
  let icon = iconCache.get(key)
  if (!icon) {
    icon = new Icon({ src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, anchor: opts.anchor ?? [0.5, 0.5], scale: opts.scale ?? 1 })
    iconCache.set(key, icon)
  }
  return icon
}

export interface StationProps {
  kind: 'station'
  code: string
  name: string
  runStatus: number | null
  /** 摆放模式里选中的 */
  selected: boolean
  /** 正在摆放、还没保存 */
  pending: boolean
  /** 显示名称（摆放模式） */
  showName: boolean
}

/** 摆放模式里放大到这个分辨率（米/像素）以内才写基站名称，免得字挤成一片 */
const STATION_NAME_RESOLUTION = 2.5

function stationStyle(feature: FeatureLike, resolution: number): Style[] {
  const p = feature.get('props') as StationProps
  const color = p.runStatus == null ? STATION_UNKNOWN : STATION_COLOR[p.runStatus] ?? STATION_UNKNOWN
  const styles: Style[] = []
  if (p.selected || p.pending) {
    styles.push(new Style({
      image: new CircleStyle({ radius: 16, fill: new Fill({ color: 'rgba(245,220,60,0.18)' }),
        stroke: new Stroke({ color: '#f5dc3c', width: 2, lineDash: p.pending ? [4, 3] : undefined }) })
    }))
  }
  styles.push(new Style({
    image: svgIcon(towerSvg(color), { scale: p.selected || p.pending ? 1.15 : 1 }),
    text: (p.showName && resolution <= STATION_NAME_RESOLUTION) || p.selected || p.pending
      ? new Text({
        text: p.pending ? `${p.name}（未保存）` : p.name,
        font: '12px "Noto Sans SC", sans-serif',
        offsetY: 20,
        fill: new Fill({ color: p.pending ? '#f5dc3c' : '#d8fff5' }),
        stroke: new Stroke({ color: 'rgba(3,13,12,0.9)', width: 3 })
      })
      : undefined
  }))
  return styles
}

export function stationFeature(s: Station, x: number, y: number, extra: Partial<StationProps> = {}): Feature<Point> {
  const f = new Feature(new Point([x, y]))
  f.setId(`station:${s.stationCode}`)
  const props: StationProps = {
    kind: 'station', code: s.stationCode, name: s.name, runStatus: s.runStatus,
    selected: false, pending: false, showName: false, ...extra
  }
  f.set('props', props)
  return f
}

// ---------------------------------------------------------------- 人员

/** 戴安全帽的小人（docs/09 第四节）；有仍在发生的告警时用告警红 */
function minerSvg(alerting: boolean): string {
  const body = alerting ? '#ff4d4f' : '#2f86f2'
  const ring = alerting ? '#ffb3b4' : '#9fd0ff'
  return (
    '<svg xmlns="http://www.w3.org/2000/svg" width="30" height="32" viewBox="0 0 30 32">' +
    `<circle cx="15" cy="15" r="13.5" fill="${body}" fill-opacity="0.25" stroke="${ring}" stroke-width="1.2"/>` +
    `<path d="M6.5 27c1-5 4.4-7.6 8.5-7.6s7.5 2.6 8.5 7.6z" fill="${body}"/>` +
    '<circle cx="15" cy="14.2" r="4.6" fill="#f3d6b8"/>' +
    '<path d="M9.4 12.6a5.6 5.6 0 0 1 11.2 0z" fill="#f4f7f8"/>' +
    '<rect x="8.2" y="12" width="13.6" height="1.8" rx="0.9" fill="#f4f7f8"/>' +
    '<circle cx="15" cy="9.6" r="1.2" fill="#ffe680"/>' +
    '</svg>'
  )
}

/** 人员图层上的一个点：一个人，或同一基站的几个人 */
export interface PersonGroupProps {
  kind: 'persons'
  stationCode: string
  persons: MapPerson[]
}

function personStyle(feature: FeatureLike): Style {
  const p = feature.get('props') as PersonGroupProps
  const alerting = p.persons.some((x) => x.alerting)
  if (p.persons.length === 1) {
    return new Style({
      image: svgIcon(minerSvg(alerting), { anchor: [0.5, 0.5] }),
      text: new Text({
        text: p.persons[0].name ?? p.persons[0].cardCode.slice(-5),
        font: '12px "Noto Sans SC", sans-serif',
        offsetY: 24,
        fill: new Fill({ color: alerting ? '#ffb3b4' : '#ffffff' }),
        stroke: new Stroke({ color: 'rgba(3,13,12,0.9)', width: 3 })
      })
    })
  }
  // 同一基站多人：绿色圆点写人数（截图 14 的 "4" "2" "3"）；里面有人在告警时圆点描红边
  return new Style({
    image: new CircleStyle({
      radius: 12,
      fill: new Fill({ color: 'rgba(98,198,74,0.92)' }),
      stroke: new Stroke({ color: alerting ? '#ff4d4f' : 'rgba(214,255,200,0.9)', width: alerting ? 3 : 1.5 })
    }),
    text: new Text({
      text: String(p.persons.length),
      font: 'bold 13px "Orbitron", sans-serif',
      fill: new Fill({ color: '#ffffff' })
    })
  })
}

/** 已摆放基站上的人，按基站合并成点；没摆放的不画（docs/05 第五节） */
export function personFeatures(list: MapPerson[]): Feature<Point>[] {
  const groups = new Map<string, MapPerson[]>()
  for (const p of list) {
    if (!p.placed || p.x == null || p.y == null || !p.stationCode) continue
    const g = groups.get(p.stationCode)
    if (g) g.push(p)
    else groups.set(p.stationCode, [p])
  }
  return [...groups.entries()].map(([stationCode, persons]) => {
    const f = new Feature(new Point([persons[0].x as number, persons[0].y as number]))
    f.setId(`persons:${stationCode}`)
    const props: PersonGroupProps = { kind: 'persons', stationCode, persons }
    f.set('props', props)
    return f
  })
}
