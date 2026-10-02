// 月度汇总弹层的状态和动作（docs/07 第二部分）：月份列表、所选月份的报告、键盘翻页。模板只从返回值里取东西。
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, useTemplateRef } from 'vue'
import { fetchMonthlyReport, fetchReportMonths, type MonthlyReport } from '@/api/report'
import { PAGE_TOTAL } from './report-format'

/** MonthlyReportBook 暴露出来的方法 */
interface BookApi {
  next(): void
  prev(): void
  flipTo(index: number): void
}

export type LoadStatus = 'loading' | 'failed' | 'ready'

export function useMonthlyReportPage(close: () => void) {
  const book = useTemplateRef<BookApi>('book')
  const root = useTemplateRef<HTMLElement>('root')

  const months = ref<string[]>([])
  const month = ref<string | null>(null)
  const report = shallowRef<MonthlyReport | null>(null)
  const monthsStatus = ref<LoadStatus>('loading')
  const reportStatus = ref<LoadStatus>('loading')
  /** 当前展开的是第几页（封面 0，之后是左页的页码） */
  const page = ref(0)

  /** 各页用的状态：月份列表或报告没取到算失败；一个有数据的月份都没有时，各页直接显示"暂无数据" */
  const status = computed<LoadStatus>(() => {
    if (monthsStatus.value === 'failed' || reportStatus.value === 'failed') return 'failed'
    if (monthsStatus.value === 'loading' || reportStatus.value === 'loading') return 'loading'
    return 'ready'
  })

  // 快速切换月份时，只认最后一次请求的结果
  let seq = 0

  async function loadMonths() {
    monthsStatus.value = 'loading'
    try {
      const m = await fetchReportMonths()
      months.value = m.months
      month.value = m.defaultMonth
      monthsStatus.value = 'ready'
      await loadReport()
    } catch {
      monthsStatus.value = 'failed'
    }
  }

  async function loadReport() {
    const my = ++seq
    report.value = null
    if (!month.value) {
      reportStatus.value = 'ready'
      return
    }
    reportStatus.value = 'loading'
    try {
      const r = await fetchMonthlyReport(month.value)
      if (my !== seq) return
      report.value = r
      reportStatus.value = 'ready'
    } catch {
      if (my === seq) reportStatus.value = 'failed'
    }
  }

  function selectMonth(m: string) {
    if (m === month.value) return
    month.value = m
    void loadReport()
  }

  function retry() {
    if (monthsStatus.value === 'failed') void loadMonths()
    else void loadReport()
  }

  /** 目录里点一章：翻到那一页 */
  function goTo(pageNo: number) {
    book.value?.flipTo(pageNo)
  }

  /** ← → 翻页（PageUp / PageDown 也行），Esc 关闭。焦点在月份下拉框上时方向键留给下拉框 */
  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault()
      close()
      return
    }
    if (e.target instanceof HTMLSelectElement) return
    if (e.key === 'ArrowRight' || e.key === 'PageDown') {
      e.preventDefault()
      book.value?.next()
    } else if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
      e.preventDefault()
      book.value?.prev()
    }
  }

  const pageText = computed(() => {
    if (page.value === 0) return '封面'
    if (page.value >= PAGE_TOTAL) return `${PAGE_TOTAL} / ${PAGE_TOTAL}`
    return `${page.value}–${page.value + 1} / ${PAGE_TOTAL}`
  })

  onMounted(() => {
    window.addEventListener('keydown', onKeydown)
    root.value?.focus()
    void loadMonths()
  })
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

  return { months, month, report, status, page, pageText, selectMonth, retry, goTo }
}
