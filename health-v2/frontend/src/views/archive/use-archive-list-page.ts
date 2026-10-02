// 健康档案列表的状态和动作（docs/05 第六节）。模板只从返回值里取东西。
// 不自动刷新（docs/05 第二节）。查询条件和页码放在地址里（?dept=&jobKind=&keyword=&page=），
// 从个人档案返回时回到原来那一页。
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchArchiveFilters,
  fetchArchivePersons,
  type ArchiveFilters,
  type ArchivePerson,
  type ArchiveQuery
} from '@/api/archive'
import type { Page } from '@/api/dashboard'
import { ApiError } from '@/api/request'

/** 每页 12 张卡片（4 列 × 3 行） */
export const PAGE_SIZE = 12

function text(q: LocationQuery, key: string): string {
  const v = q[key]
  return typeof v === 'string' ? v : ''
}

export function useArchiveListPage() {
  const route = useRoute()
  const router = useRouter()

  // ---- 筛选：draft 是输入框里正在改的，applied 是点"查询"后生效的 ----
  const draft = reactive<ArchiveQuery>({ dept: null, jobKind: null, keyword: '' })
  const applied = shallowRef<ArchiveQuery>({ dept: null, jobKind: null, keyword: '' })
  const page = ref(1)

  const filters = shallowRef<ArchiveFilters>({ depts: [], jobKinds: [] })
  const filtersFailed = ref(false)

  // ---- 列表 ----
  const data = shallowRef<Page<ArchivePerson> | null>(null)
  const loading = ref(true)
  const failed = ref(false)
  let seq = 0

  const pages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / PAGE_SIZE)))

  async function load() {
    const mine = ++seq
    loading.value = true
    try {
      const result = await fetchArchivePersons(applied.value, page.value, PAGE_SIZE)
      if (mine !== seq) return
      data.value = result
      failed.value = false
      // 翻到的页已经不存在了（比如人少了），回到最后一页
      const last = Math.max(1, Math.ceil(result.total / PAGE_SIZE))
      if (result.list.length === 0 && page.value > last) {
        page.value = last
        syncUrl()
        await load()
      }
    } catch (e) {
      if (mine !== seq) return
      failed.value = true
      ElMessage.error(e instanceof ApiError ? `查询失败：${e.message}` : '查询失败')
    } finally {
      if (mine === seq) loading.value = false
    }
  }

  function syncUrl() {
    const q = applied.value
    const query: Record<string, string> = {}
    if (q.dept) query.dept = q.dept
    if (q.jobKind) query.jobKind = q.jobKind
    if (q.keyword) query.keyword = q.keyword
    if (page.value > 1) query.page = String(page.value)
    void router.replace({ query })
  }

  function search() {
    applied.value = { dept: draft.dept, jobKind: draft.jobKind, keyword: draft.keyword.trim() }
    draft.keyword = applied.value.keyword
    page.value = 1
    syncUrl()
    void load()
  }

  function reset() {
    draft.dept = null
    draft.jobKind = null
    draft.keyword = ''
    search()
  }

  function go(delta: number) {
    const next = Math.min(pages.value, Math.max(1, page.value + delta))
    if (next === page.value || loading.value) return
    page.value = next
    syncUrl()
    void load()
  }

  function openPerson(cardCode: string) {
    void router.push(`/archive/${cardCode}`)
  }

  /** "健康数据汇总"打开月度汇总（翻书，docs/05 第六节） */
  const monthlyOpen = ref(false)

  onMounted(() => {
    const q = route.query
    draft.dept = text(q, 'dept') || null
    draft.jobKind = text(q, 'jobKind') || null
    draft.keyword = text(q, 'keyword')
    applied.value = { dept: draft.dept, jobKind: draft.jobKind, keyword: draft.keyword }
    page.value = Math.max(1, Number.parseInt(text(q, 'page'), 10) || 1)
    void load()
    fetchArchiveFilters().then(
      (f) => (filters.value = f),
      () => (filtersFailed.value = true)
    )
  })

  return { draft, applied, page, pages, filters, filtersFailed, data, loading, failed, search, reset, go, openPerson,
    monthlyOpen }
}
