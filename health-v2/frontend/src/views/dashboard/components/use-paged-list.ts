import { ref, shallowRef, watch, type Ref } from 'vue'
import type { Page } from '@/api/dashboard'

/** 弹层里的分页列表：打开时从第 1 页加载，翻页时加载那一页。 */
export function usePagedList<T>(open: Ref<boolean>, fetcher: (page: number, size: number) => Promise<Page<T>>,
                                size = 10) {
  const page = ref(1)
  const data = shallowRef<Page<T> | null>(null)
  const loading = ref(false)
  const failed = ref(false)

  async function load(p: number) {
    loading.value = true
    try {
      data.value = await fetcher(p, size)
      page.value = p
      failed.value = false
    } catch {
      failed.value = true
    } finally {
      loading.value = false
    }
  }

  watch(open, (o) => {
    if (o) {
      data.value = null
      void load(1)
    }
  }, { immediate: true })

  const pages = () => Math.max(1, Math.ceil((data.value?.total ?? 0) / size))
  return { page, data, loading, failed, load, pages }
}
