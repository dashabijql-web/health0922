// 个人档案的状态和动作（docs/05 第七、八节）。模板只从返回值里取东西。
// 每 60 秒刷新一次（docs/05 第二节），手动操作（录入年龄、加入/移出名单）后立刻重读个人信息。
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addToList,
  fetchPersonAlerts,
  fetchPersonDetail,
  fetchSteps,
  fetchTrend,
  removeFromList,
  saveAge,
  type ListType,
  type PersonDetail,
  type StepDay,
  type Trend,
  type VitalKey
} from '@/api/archive'
import { ApiError } from '@/api/request'
import { usePolling, useResource } from '@/composables/use-polling'
import { localDate } from '@/utils/format'
import { buildStepsOption, buildTrendOption, readChartColors } from './archive-charts'

export const ALERT_PAGE_SIZE = 10
export const STEP_DAYS = 7
/** "查看详情"里步数看近 30 天 */
export const DETAIL_STEP_DAYS = 30
export const MIN_AGE = 16
export const MAX_AGE = 75

export type DetailMetric = VitalKey | 'STEPS'

export function usePersonArchivePage(cardCode: string) {
  // ---- 数据（一轮里并行刷新，60 秒一轮） ----
  const notFound = ref(false)
  const detail = useResource(async () => {
    try {
      return await fetchPersonDetail(cardCode)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) notFound.value = true
      throw e
    }
  })
  const heart = useResource(() => fetchTrend(cardCode, 'HEART_RATE'))
  const spo2 = useResource(() => fetchTrend(cardCode, 'SPO2'))
  const temp = useResource(() => fetchTrend(cardCode, 'TEMPERATURE'))
  const steps = useResource(() => fetchSteps(cardCode, STEP_DAYS))
  const alertPage = ref(1)
  const alerts = useResource(() => fetchPersonAlerts(cardCode, alertPage.value, ALERT_PAGE_SIZE))

  const all = [detail, heart, spo2, temp, steps, alerts]
  const { lastRoundFailed, refreshNow } = usePolling(all, 60_000)

  /** 折线横轴右端：浏览器的现在，每 30 秒走一下 */
  const nowMs = ref(Date.now())
  const ticker = setInterval(() => (nowMs.value = Date.now()), 30_000)
  onBeforeUnmount(() => clearInterval(ticker))

  const failedBanner = computed(() => {
    if (!lastRoundFailed.value || notFound.value) return null
    const at = all.map((r) => r.failedAt.value).find((t) => t)
    return at ? `刷新失败，显示的是 ${at} 的数据` : '刷新失败'
  })

  // ---- 图表配置（纯函数算，颜色取自 tokens.css） ----
  const colors = readChartColors()
  const option = (t: Trend | null) => (t && t.points.length > 0
    ? buildTrendOption(t, nowMs.value, colors, { compact: true }) : null)
  const heartOption = computed(() => option(heart.data.value))
  const spo2Option = computed(() => option(spo2.data.value))
  const tempOption = computed(() => option(temp.data.value))
  const stepsOption = computed(() => {
    const d = steps.data.value
    return d && d.some((x) => x.steps !== null) ? buildStepsOption(d, colors, { compact: true }) : null
  })

  // ---- 标签：数据汇总 / 预警记录 ----
  const tab = ref<'summary' | 'alerts'>('summary')
  const alertPages = computed(() => Math.max(1, Math.ceil((alerts.data.value?.total ?? 0) / ALERT_PAGE_SIZE)))
  const alertsBusy = ref(false)
  async function goAlertPage(p: number) {
    if (p < 1 || p > alertPages.value || alertsBusy.value) return
    const old = alertPage.value
    alertPage.value = p
    alertsBusy.value = true
    const ok = await alerts.load()
    alertsBusy.value = false
    if (!ok) alertPage.value = old
  }

  // ---- 年龄 ----
  const ageOpen = ref(false)
  const ageInput = ref('')
  const ageSaving = ref(false)
  const ageError = computed(() => {
    const s = ageInput.value.trim()
    if (s === '') return '请输入年龄'
    if (!/^\d+$/.test(s)) return '请输入整数'
    const n = Number(s)
    return n < MIN_AGE || n > MAX_AGE ? `年龄应在 ${MIN_AGE}–${MAX_AGE} 之间` : null
  })
  function openAge() {
    ageInput.value = detail.data.value?.age != null ? String(detail.data.value.age) : ''
    ageOpen.value = true
  }
  async function submitAge() {
    if (ageError.value || ageSaving.value) return
    ageSaving.value = true
    try {
      const r = await saveAge(cardCode, Number(ageInput.value.trim()))
      // 立即显示，再重读一次个人信息
      const d = detail.data.value
      if (d) detail.data.value = { ...d, age: r.age, ageUpdatedBy: r.updatedBy, ageUpdatedAt: r.updatedAt }
      ageOpen.value = false
      ElMessage.success('年龄已保存')
      void detail.load()
    } catch {
      // request.ts 已经弹出后端的提示
    } finally {
      ageSaving.value = false
    }
  }

  // ---- 重点监护 / 今日关注（再点一次移出） ----
  const listBusy = ref<ListType | null>(null)
  async function toggleList(type: ListType) {
    const d = detail.data.value
    if (!d || listBusy.value) return
    listBusy.value = type
    const name = type === 'KEY' ? '重点监护' : '今日关注'
    try {
      const entry = d.lists[type]
      if (entry) {
        await removeFromList(entry.id)
        ElMessage.success(`已移出${name}`)
      } else {
        await addToList(cardCode, type)
        ElMessage.success(type === 'KEY' ? '已加入重点监护' : '已加入今日关注（今天 24 点自动失效）')
      }
    } catch {
      // 提示已由 request.ts 弹出；名单可能被别人改过，下面重读
    } finally {
      await detail.load()
      listBusy.value = null
    }
  }

  // ---- 查看详情：放大图表，可换指标和日期 ----
  const zoomOpen = ref(false)
  const zoomMetric = ref<DetailMetric>('HEART_RATE')
  const zoomDate = ref(localDate())
  const zoomTrend = shallowRef<Trend | null>(null)
  const zoomSteps = shallowRef<StepDay[] | null>(null)
  const zoomLoading = ref(false)
  const zoomFailed = ref(false)
  let zoomSeq = 0

  async function loadZoom() {
    const mine = ++zoomSeq
    zoomLoading.value = true
    zoomFailed.value = false
    try {
      if (zoomMetric.value === 'STEPS') {
        const s = await fetchSteps(cardCode, DETAIL_STEP_DAYS)
        if (mine === zoomSeq) zoomSteps.value = s
      } else {
        const t = await fetchTrend(cardCode, zoomMetric.value, zoomDate.value)
        if (mine === zoomSeq) zoomTrend.value = t
      }
    } catch {
      if (mine === zoomSeq) zoomFailed.value = true
    } finally {
      if (mine === zoomSeq) zoomLoading.value = false
    }
  }
  function openZoom(metric: DetailMetric) {
    zoomMetric.value = metric
    zoomDate.value = localDate()
    zoomTrend.value = null
    zoomSteps.value = null
    zoomOpen.value = true
    void loadZoom()
  }
  function setZoomMetric(metric: DetailMetric) {
    if (metric === zoomMetric.value) return
    zoomMetric.value = metric
    zoomTrend.value = null
    zoomSteps.value = null
    void loadZoom()
  }
  function setZoomDate(date: string) {
    const today = localDate()
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date > today || date === zoomDate.value) return
    zoomDate.value = date
    zoomTrend.value = null
    void loadZoom()
  }
  function shiftZoomDate(days: number) {
    const d = new Date(`${zoomDate.value}T00:00:00`)
    d.setDate(d.getDate() + days)
    setZoomDate(localDate(d))
  }
  const zoomOption = computed(() => {
    if (zoomMetric.value === 'STEPS') {
      const s = zoomSteps.value
      return s && s.some((x) => x.steps !== null) ? buildStepsOption(s, colors) : null
    }
    const t = zoomTrend.value
    return t && t.points.length > 0 ? buildTrendOption(t, nowMs.value, colors) : null
  })
  const zoomIsToday = computed(() => zoomDate.value >= localDate())

  return {
    notFound, detail, heart, spo2, temp, steps, alerts, refreshNow, failedBanner,
    heartOption, spo2Option, tempOption, stepsOption,
    tab, alertPage, alertPages, alertsBusy, goAlertPage,
    ageOpen, ageInput, ageSaving, ageError, openAge, submitAge,
    listBusy, toggleList,
    zoomOpen, zoomMetric, zoomDate, zoomTrend, zoomSteps, zoomLoading, zoomFailed, zoomOption, zoomIsToday,
    openZoom, setZoomMetric, setZoomDate, shiftZoomDate
  }
}

export type PersonArchivePage = ReturnType<typeof usePersonArchivePage>
export type { PersonDetail }
