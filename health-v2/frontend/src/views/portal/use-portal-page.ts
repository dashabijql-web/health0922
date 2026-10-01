// 入口页的状态（docs/05 第三节）。每 30 秒刷新一次，和动态数据页一样。
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPortalSummary } from '@/api/dashboard'
import { usePolling, useResource } from '@/composables/use-polling'
import { shortTime } from '@/utils/format'

export function usePortalPage() {
  const router = useRouter()
  const summary = useResource(fetchPortalSummary)
  const { lastRoundFailed } = usePolling([summary])

  const staleBanner = computed(() => {
    const p = summary.data.value?.positioning
    if (!p || !p.stale) return null
    return p.dataTime ? `定位数据已过期，最后更新 ${shortTime(p.dataTime)}` : '还没有收到定位数据'
  })
  const failedBanner = computed(() => {
    if (!lastRoundFailed.value) return null
    return summary.failedAt.value ? `刷新失败，显示的是 ${summary.failedAt.value} 的数据` : '刷新失败'
  })

  function openAdmin() {
    // 后台管理放最后做（docs/05 第一节、docs/09 第九节第 13 项）
    ElMessage.info('建设中')
  }

  function openDashboard() {
    void router.push({ name: 'dashboard' })
  }

  return { summary, staleBanner, failedBanner, openAdmin, openDashboard }
}
