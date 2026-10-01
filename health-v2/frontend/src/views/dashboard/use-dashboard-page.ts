// 动态数据页的状态和动作（docs/05 第四节）。模板只从返回值里取东西。
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchHeadcount,
  fetchHeadcountSeries,
  fetchInWellPersons,
  fetchKeyPersons,
  fetchOverview,
  fetchStepsRank,
  type AlertCategory,
  type InWellPerson
} from '@/api/dashboard'
import { usePolling, useResource } from '@/composables/use-polling'
import { shortTime } from '@/utils/format'

export const PERSON_PAGE_SIZE = 20
export const STEPS_LIMIT = 10

export function useDashboardPage() {
  const route = useRoute()
  const router = useRouter()

  // ---- 模式：展示模式（默认）/ 地图模式（?mode=map，阶段 4） ----
  const mode = computed(() => (route.query.mode === 'map' ? 'map' : 'display'))
  function toggleMode() {
    void router.replace({ query: mode.value === 'map' ? {} : { mode: 'map' } })
  }
  /** 点人员表的一行：切到地图模式并带上卡编码；在地图上定位要等阶段 4 */
  function locatePerson(p: InWellPerson) {
    void router.replace({ query: { mode: 'map', card: p.cardCode } })
  }

  // ---- 数据 ----
  const personPage = ref(1)
  const overview = useResource(fetchOverview)
  const headcount = useResource(fetchHeadcount)
  const keyPersons = useResource(fetchKeyPersons)
  const series = useResource(() => fetchHeadcountSeries())
  const steps = useResource(() => fetchStepsRank(STEPS_LIMIT))
  const persons = useResource(() => fetchInWellPersons('', personPage.value, PERSON_PAGE_SIZE))

  const { lastRoundFailed, refreshNow } = usePolling([overview, headcount, keyPersons, series, steps, persons])

  /** 曲线横轴右端：数据是今天的就到现在，是别的日子就到那天 24 点 */
  const seriesNow = ref(Date.now())
  const nowTicker = setInterval(() => (seriesNow.value = Date.now()), 30_000)
  onBeforeUnmount(() => clearInterval(nowTicker))

  async function goPersonPage(delta: number) {
    const total = persons.data.value?.total ?? 0
    const pages = Math.max(1, Math.ceil(total / PERSON_PAGE_SIZE))
    const next = Math.min(pages, Math.max(1, personPage.value + delta))
    if (next === personPage.value) return
    personPage.value = next
    await persons.load()
  }

  // ---- 顶部横幅、角落时间 ----
  const positioning = computed(() => headcount.data.value?.positioning ?? null)
  const staleBanner = computed(() => {
    const p = positioning.value
    if (!p || !p.stale) return null
    return p.dataTime ? `定位数据已过期，最后更新 ${shortTime(p.dataTime)}` : '还没有收到定位数据'
  })
  const failedBanner = computed(() => {
    if (!lastRoundFailed.value) return null
    const at = [overview, headcount, keyPersons, series, steps, persons]
      .map((r) => r.failedAt.value)
      .find((t) => t)
    return at ? `刷新失败，显示的是 ${at} 的数据` : '刷新失败'
  })
  const positioningTime = computed(() => shortTime(positioning.value?.dataTime))
  const watchTime = computed(() => shortTime(overview.data.value?.dataTime))

  // ---- 弹层 ----
  const exportOpen = ref(false)
  const deviceEventsOpen = ref(false)
  const alertCategory = ref<AlertCategory | null>(null)

  function openMonthly() {
    // 月度汇总（翻书）在阶段 6 做
    ElMessage.info('建设中')
  }

  return {
    mode,
    toggleMode,
    locatePerson,
    overview,
    headcount,
    keyPersons,
    series,
    seriesNow,
    steps,
    persons,
    personPage,
    goPersonPage,
    refreshNow,
    staleBanner,
    failedBanner,
    positioningTime,
    watchTime,
    exportOpen,
    deviceEventsOpen,
    alertCategory,
    openMonthly
  }
}
