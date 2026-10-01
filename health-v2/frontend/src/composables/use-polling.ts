import { onBeforeUnmount, onMounted, ref, shallowRef, type Ref, type ShallowRef } from 'vue'
import { localHhmm } from '@/utils/format'

/**
 * 一个面板的数据（docs/05 第二节"面板状态"）：
 * - 第一次加载时 loading = true（骨架屏，不闪 0）；
 * - 刷新失败时保留上次的数据，failedAt 是上次成功的时刻 hh:mm；
 * - 从来没成功过又失败了，error = true（显示"加载失败"）。
 */
export interface Resource<T> {
  data: ShallowRef<T | null>
  loading: Ref<boolean>
  failedAt: Ref<string | null>
  error: Ref<boolean>
  load: () => Promise<boolean>
}

export function useResource<T>(fetcher: () => Promise<T>): Resource<T> {
  const data = shallowRef<T | null>(null)
  const loading = ref(true)
  const failedAt = ref<string | null>(null)
  const error = ref(false)
  let lastOk: string | null = null

  async function load(): Promise<boolean> {
    try {
      data.value = await fetcher()
      lastOk = localHhmm()
      failedAt.value = null
      error.value = false
      return true
    } catch {
      failedAt.value = data.value === null ? null : lastOk
      error.value = data.value === null
      return false
    } finally {
      loading.value = false
    }
  }

  return { data, loading, failedAt, error, load }
}

/**
 * 定时刷新（docs/05 第二节"刷新"）：一轮里并行刷新所有面板，一轮结束后再等 intervalMs 开始下一轮，
 * 所以不会两轮重叠。手动刷新和自动刷新共用这一个倒计时：手动刷新时取消正在等的那次，立刻刷新，再重新倒计时；
 * 正在刷新时再点手动刷新不会重复请求。
 */
export function usePolling(resources: Resource<unknown>[], intervalMs = 30_000) {
  const refreshing = ref(false)
  /** 最近一轮是否有面板刷新失败 */
  const lastRoundFailed = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let stopped = false

  async function refreshNow(): Promise<void> {
    if (refreshing.value || stopped) return
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    refreshing.value = true
    try {
      const results = await Promise.all(resources.map((r) => r.load()))
      lastRoundFailed.value = results.some((ok) => !ok)
    } finally {
      refreshing.value = false
      if (!stopped) timer = setTimeout(() => void refreshNow(), intervalMs)
    }
  }

  onMounted(() => void refreshNow())
  onBeforeUnmount(() => {
    stopped = true
    if (timer) clearTimeout(timer)
  })

  return { refreshing, lastRoundFailed, refreshNow }
}
