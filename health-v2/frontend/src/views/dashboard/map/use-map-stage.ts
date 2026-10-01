// 地图模式的状态和动作（docs/05 第五节、docs/06 第四～六节）。模板只从返回值里取东西。
// 地图本身（图层、样式）在 mine-map.ts；这里管：人员和基站数据、体征卡、搜索、名单过滤、摆放模式。
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import Overlay from 'ol/Overlay'
import Translate from 'ol/interaction/Translate'
import type Feature from 'ol/Feature'
import type Point from 'ol/geom/Point'
import { ApiError } from '@/api/request'
import {
  deleteStationMark,
  fetchMapConfig,
  fetchMapPersons,
  fetchPersonCard,
  fetchStations,
  saveStationMark,
  searchMapPersons,
  type MapConfig,
  type MapFilter,
  type MapPerson,
  type PersonCard,
  type Station
} from '@/api/map'
import { usePolling, useResource } from '@/composables/use-polling'
import {
  createMineMap,
  LOCATE_RESOLUTION,
  personFeatures,
  STATION_HIDE_RESOLUTION,
  stationFeature,
  type MineMap,
  type PersonGroupProps,
  type StationProps
} from './mine-map'

export type PlaceFilter = 'all' | 'unplaced' | 'placed'

/** 弹窗：一个人的体征卡，或同一基站几个人的名单 */
export interface Popup {
  kind: 'card' | 'group'
  /** 挂在地图上的位置（基站坐标）；人所在基站没摆放时为 null，卡片停在地图上方 */
  coord: [number, number] | null
  group: MapPerson[]
}

export interface MapStageRefs {
  mapEl: Ref<HTMLElement | null>
  popupEl: Ref<HTMLElement | null>
}

export function useMapStage(refs: MapStageRefs, locateRequest: Ref<{ card: string | null; seq: number }>) {
  // ---------------------------------------------------------------- 数据
  const config = shallowRef<MapConfig | null>(null)
  const configError = ref(false)
  const baseMapError = ref(false)
  const filter = ref<MapFilter>('all')
  /** 当前显示的人员是按哪个名单取回来的（切换名单后、新数据回来之前，人数不能算到新名单头上） */
  const shownFilter = ref<MapFilter>('all')
  const persons = useResource(async () => {
    const f = filter.value
    const list = await fetchMapPersons(f)
    shownFilter.value = f
    return list
  })
  const stations = useResource(fetchStations)
  /** 打开的体征卡，跟着地图一起每 30 秒刷新 */
  const cardCode = ref<string | null>(null)
  const card = useResource<PersonCard | null>(() =>
    cardCode.value ? fetchPersonCard(cardCode.value, true) : Promise.resolve(null)
  )
  const { refreshing, lastRoundFailed, refreshNow } = usePolling([persons, stations, card])
  const failedAt = computed(() =>
    lastRoundFailed.value ? persons.failedAt.value ?? stations.failedAt.value ?? card.failedAt.value : null
  )

  // ---------------------------------------------------------------- 地图
  let mine: MineMap | null = null
  let overlay: Overlay | null = null
  const mapReady = ref(false)
  const stationsShown = ref(true)
  const resolution = ref(Infinity)

  async function loadConfig(): Promise<void> {
    try {
      config.value = await fetchMapConfig()
      configError.value = false
    } catch {
      configError.value = true
      return
    }
    const el = refs.mapEl.value
    if (!el || mine) return
    mine = createMineMap(el, config.value, (ok) => (baseMapError.value = !ok))
    overlay = new Overlay({
      element: refs.popupEl.value ?? undefined,
      positioning: 'bottom-center',
      offset: [0, -22],
      stopEvent: true,
      // 卡片靠近边上时把地图挪一挪，让整张卡露出来；上边留出搜索框的高度
      autoPan: { margin: 90, animation: { duration: 250 } }
    })
    mine.map.addOverlay(overlay)
    const translate = new Translate({ layers: [mine.editLayer] })
    translate.on('translateend', (e) => {
      const f = e.features.item(0) as Feature<Point> | undefined
      const c = f?.getGeometry()?.getCoordinates()
      if (c) pending.value = [c[0], c[1]]
    })
    mine.map.addInteraction(translate)
    mine.map.on('singleclick', (e) => onMapClick(e.coordinate as [number, number], e.pixel))
    mine.map.on('pointermove', (e) => {
      if (e.dragging || !mine) return
      const hit = hitFeature(e.pixel)
      const target = mine.map.getTargetElement()
      if (hit && (hit.get('props') as StationProps).kind === 'station' && (hit.get('props') as StationProps).pending) {
        target.style.cursor = 'move'
      } else if (hit && !awaitingClick.value) {
        target.style.cursor = 'pointer'
      } else {
        target.style.cursor = awaitingClick.value ? 'crosshair' : ''
      }
    })
    mine.map.getView().on('change:resolution', () => {
      resolution.value = mine?.map.getView().getResolution() ?? Infinity
    })
    resolution.value = mine.map.getView().getResolution() ?? Infinity
    // 开发模式下把地图挂到 window 上，验收脚本用它读底图像素、换算坐标（生产构建里没有）
    if (import.meta.env.DEV) (window as unknown as { __hv2MineMap?: MineMap }).__hv2MineMap = mine
    mapReady.value = true
    drawPersons()
    drawStations()
  }

  onMounted(() => void loadConfig())
  onBeforeUnmount(() => {
    mine?.destroy()
    mine = null
  })

  /** 手动刷新：地图配置没加载成功时先重试它 */
  async function refresh() {
    if (configError.value) await loadConfig()
    await refreshNow()
  }

  function hitFeature(pixel: number[]): Feature | undefined {
    if (!mine) return undefined
    const m = mine
    return m.map.forEachFeatureAtPixel(pixel, (f) => f as Feature, {
      hitTolerance: 4,
      layerFilter: (l) => l === m.editLayer || l === m.personLayer || (placing.value && l === m.stationLayer)
    })
  }

  function onMapClick(coord: [number, number], pixel: number[]) {
    if (awaitingClick.value) {
      // 摆放：在地图上点一下，就是这个基站的位置（之后可拖动微调）
      pending.value = coord
      return
    }
    const hit = hitFeature(pixel)
    const props = hit?.get('props') as PersonGroupProps | StationProps | undefined
    if (props?.kind === 'persons') {
      const coordOf = [props.persons[0].x as number, props.persons[0].y as number] as [number, number]
      if (props.persons.length === 1) openPerson(props.persons[0])
      else openPopup({ kind: 'group', coord: coordOf, group: props.persons })
    } else if (props?.kind === 'station') {
      if (!props.pending) selectStation(props.code)
    } else {
      closePopup()
    }
  }

  // ---------------------------------------------------------------- 人员

  watch(() => persons.data.value, drawPersons)

  function drawPersons() {
    if (!mine) return
    mine.persons.clear()
    mine.persons.addFeatures(personFeatures(persons.data.value ?? []))
    // 打开着体征卡的人换了基站：卡片跟过去（位置没变就不动，免得把用户拖开的地图又挪回来）
    const open = popup.value
    if (open?.kind === 'card' && cardCode.value) {
      const p = findPerson(cardCode.value)
      const coord: [number, number] | null = p?.placed ? [p.x as number, p.y as number] : null
      if (p && (coord?.[0] !== open.coord?.[0] || coord?.[1] !== open.coord?.[1])) {
        openPopup({ kind: 'card', coord, group: [p] })
      }
    }
  }

  const unplacedPersons = computed(() => (persons.data.value ?? []).filter((p) => !p.placed))

  function findPerson(code: string): MapPerson | undefined {
    return persons.data.value?.find((p) => p.cardCode === code)
  }

  /** 名单过滤：重点监护 / 今日关注，再点一次取消 */
  async function toggleFilter(f: Exclude<MapFilter, 'all'>) {
    filter.value = filter.value === f ? 'all' : f
    closePopup()
    await persons.load()
  }

  // ---------------------------------------------------------------- 体征卡、名单弹窗

  const popup = ref<Popup | null>(null)

  function openPopup(p: Popup) {
    popup.value = p
    overlay?.setPosition(p.coord ?? undefined)
  }

  function closePopup() {
    popup.value = null
    cardCode.value = null
    card.data.value = null
    overlay?.setPosition(undefined)
  }

  /** 打开某人的体征卡：基站已摆放就挂在他的图标上，没摆放就停在地图上方并提示（docs/06 第四节"定位到某人"） */
  function openPerson(p: MapPerson) {
    const changed = cardCode.value !== p.cardCode
    cardCode.value = p.cardCode
    if (changed) {
      card.data.value = null
      card.loading.value = true
    }
    openPopup({ kind: 'card', coord: p.placed ? [p.x as number, p.y as number] : null, group: [p] })
    void card.load()
  }

  /** 定位到某人：地图平滑移过去并放大，再弹体征卡；没摆放的地图不动 */
  async function locate(code: string) {
    let p = findPerson(code)
    if (!p && filter.value !== 'all') {
      filter.value = 'all'
      await persons.load()
      p = findPerson(code)
    }
    if (!p) {
      ElMessage.warning('这个人现在不在井下')
      return
    }
    if (p.placed && mine) {
      const view = mine.map.getView()
      view.animate({
        center: [p.x as number, p.y as number],
        resolution: Math.min(view.getResolution() ?? LOCATE_RESOLUTION, LOCATE_RESOLUTION),
        duration: 600
      })
    }
    openPerson(p)
  }

  // 人员表点某一行（?mode=map&card=…）：等地图和人员都加载好了再定位；同一行再点一次也重新定位（seq 变了）
  let lastLocate = ''
  watch(
    [() => locateRequest.value.seq, () => locateRequest.value.card, () => persons.data.value, mapReady],
    () => {
      const req = locateRequest.value
      const key = `${req.card}#${req.seq}`
      if (!req.card || !persons.data.value || !mapReady.value || key === lastLocate) return
      lastLocate = key
      void locate(req.card)
    },
    { immediate: true }
  )

  // ---------------------------------------------------------------- 搜索

  const keyword = ref('')
  const searchResults = ref<MapPerson[] | null>(null)
  const searching = ref(false)

  async function search() {
    const kw = keyword.value.trim()
    searchResults.value = null
    if (!kw || searching.value) return
    searching.value = true
    try {
      const list = await searchMapPersons(kw)
      if (list.length === 0) ElMessage.info(`井下没有找到"${kw}"`)
      else if (list.length === 1) await locate(list[0].cardCode)
      else searchResults.value = list
    } catch {
      /* request.ts 已提示 */
    } finally {
      searching.value = false
    }
  }

  async function pickSearchResult(p: MapPerson) {
    searchResults.value = null
    await locate(p.cardCode)
  }

  // ---------------------------------------------------------------- 基站、摆放模式（docs/06 第五节）

  const placing = ref(false)
  const placeFilter = ref<PlaceFilter>('all')
  const placeKeyword = ref('')
  const selectedCode = ref<string | null>(null)
  /** 还没保存的新位置 */
  const pending = ref<[number, number] | null>(null)
  const nameDraft = ref('')
  const saving = ref(false)
  const deleteOpen = ref(false)
  const logsOpen = ref(false)

  const selectedStation = computed<Station | null>(
    () => stations.data.value?.list.find((s) => s.stationCode === selectedCode.value) ?? null
  )
  /** 选中了一个没摆放的基站：鼠标变十字，等着在地图上点位置 */
  const awaitingClick = computed(() => placing.value && !!selectedStation.value && !selectedStation.value.placed)
  const dirty = computed(() => {
    const s = selectedStation.value
    if (!s) return false
    return pending.value !== null || nameDraft.value.trim() !== (s.displayName ?? '')
  })
  const placeList = computed(() => {
    const kw = placeKeyword.value.trim()
    return (stations.data.value?.list ?? []).filter(
      (s) =>
        (placeFilter.value === 'all' || (placeFilter.value === 'placed') === s.placed) &&
        (!kw || s.name.includes(kw) || (s.areaName ?? '').includes(kw) || s.stationCode.includes(kw))
    )
  })

  watch([() => stations.data.value, placing, selectedCode, pending], drawStations)
  // 缩放时只改显示/隐藏，不重建图标（缩放动画每一帧都会触发）
  watch([placing, stationsShown, resolution], updateStationVisibility)

  /** 缩小时不画基站；左下角按钮可以整个隐藏；摆放模式里一直画 */
  function updateStationVisibility() {
    mine?.stationLayer.setVisible(placing.value || (stationsShown.value && resolution.value <= STATION_HIDE_RESOLUTION))
  }

  function drawStations() {
    if (!mine) return
    const list = stations.data.value?.list ?? []
    mine.stations.clear()
    mine.editing.clear()
    const sel = selectedStation.value
    mine.stations.addFeatures(
      list
        .filter((s) => s.placed && s.stationCode !== (placing.value ? sel?.stationCode : null))
        .map((s) => stationFeature(s, s.x as number, s.y as number, { showName: placing.value }))
    )
    // 摆放模式里选中的基站放在单独的一层，可以拖动
    if (placing.value && sel) {
      const at = pending.value ?? (sel.placed ? [sel.x as number, sel.y as number] : null)
      if (at) mine.editing.addFeature(stationFeature(sel, at[0], at[1], { selected: true, pending: pending.value !== null }))
    }
    updateStationVisibility()
  }

  function enterPlacement(code?: string | null) {
    placing.value = true
    closePopup()
    placeFilter.value = 'all'
    if (code) selectStation(code)
  }

  function exitPlacement() {
    placing.value = false
    selectedCode.value = null
    pending.value = null
    deleteOpen.value = false
  }

  function selectStation(code: string) {
    selectedCode.value = code
    pending.value = null
    const s = selectedStation.value
    nameDraft.value = s?.displayName ?? ''
    if (s?.placed && mine) {
      const view = mine.map.getView()
      view.animate({ center: [s.x as number, s.y as number], resolution: Math.min(view.getResolution() ?? 2, 2), duration: 400 })
    }
  }

  function cancelEdit() {
    pending.value = null
    nameDraft.value = selectedStation.value?.displayName ?? ''
  }

  /** 列表顺序里 code 之后的第一个未摆放基站（到底了从头找） */
  function nextUnplaced(code: string): Station | null {
    const list = stations.data.value?.list ?? []
    const i = list.findIndex((s) => s.stationCode === code)
    const ordered = [...list.slice(i + 1), ...list.slice(0, Math.max(i, 0))]
    return ordered.find((s) => !s.placed) ?? null
  }

  async function onEditError(error: unknown) {
    const message = error instanceof ApiError ? error.message : '保存失败'
    ElMessage.error(message)
    if (error instanceof ApiError && error.status === 409) {
      // 别人刚改过：重新读列表，丢掉这次没保存的改动
      await stations.load()
      cancelEdit()
    }
  }

  async function saveSelected() {
    const s = selectedStation.value
    if (!s || saving.value) return
    const at = pending.value ?? (s.placed ? [s.x as number, s.y as number] : null)
    if (!at) {
      ElMessage.info('先在地图上点一下这个基站的位置')
      return
    }
    const isNew = !s.placed
    saving.value = true
    try {
      await saveStationMark(s.stationCode, {
        x: Math.round(at[0] * 1000) / 1000,
        y: Math.round(at[1] * 1000) / 1000,
        displayName: nameDraft.value.trim() || null,
        version: s.version
      })
      pending.value = null
      await stations.load()
      await persons.load()
      ElMessage.success(isNew ? `已摆放：${s.name}` : '已保存')
      if (isNew) {
        // 保存后自动选中下一个未摆放的基站
        const next = nextUnplaced(s.stationCode)
        if (next) selectStation(next.stationCode)
        else {
          selectedCode.value = null
          ElMessage.success('全部基站都摆放好了')
        }
      } else {
        nameDraft.value = selectedStation.value?.displayName ?? ''
      }
    } catch (error) {
      await onEditError(error)
    } finally {
      saving.value = false
    }
  }

  async function deleteSelected() {
    const s = selectedStation.value
    deleteOpen.value = false
    if (!s || !s.placed || saving.value) return
    saving.value = true
    try {
      await deleteStationMark(s.stationCode, s.version)
      pending.value = null
      await stations.load()
      await persons.load()
      nameDraft.value = selectedStation.value?.displayName ?? ''
      ElMessage.success(`已删除位置：${s.name}，回到"未摆放"`)
    } catch (error) {
      await onEditError(error)
    } finally {
      saving.value = false
    }
  }

  /** 体征卡里"去摆放"：进入摆放模式并选中这个人所在的基站 */
  function goPlace(stationCode: string | null) {
    enterPlacement(stationCode)
  }

  return {
    config,
    configError,
    baseMapError,
    mapReady,
    filter,
    shownFilter,
    toggleFilter,
    persons,
    stations,
    unplacedPersons,
    refreshing,
    failedAt,
    refresh,
    stationsShown,
    // 弹窗
    popup,
    card,
    cardCode,
    openPerson,
    closePopup,
    // 搜索
    keyword,
    search,
    searching,
    searchResults,
    pickSearchResult,
    // 摆放
    placing,
    placeFilter,
    placeKeyword,
    placeList,
    selectedCode,
    selectedStation,
    awaitingClick,
    pending,
    nameDraft,
    dirty,
    saving,
    deleteOpen,
    logsOpen,
    enterPlacement,
    exitPlacement,
    selectStation,
    cancelEdit,
    saveSelected,
    deleteSelected,
    goPlace
  }
}
