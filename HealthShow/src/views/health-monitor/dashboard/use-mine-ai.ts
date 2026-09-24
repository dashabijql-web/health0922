import { ElMessage } from 'element-plus'
import { generateMineAiReport, getMineAiReport } from '@/api/ai'
import type { DashboardState } from './dashboard-state'

/** 全矿 AI 研判：生成、读取缓存、打开面板（结果只展示，不改动业务数据） */
export function useMineAi(state: DashboardState) {
  async function handleMineAi(force = false) {
    if (state.mineAiLoading) return
    state.mineAiLoading = true
    try {
      const res = await generateMineAiReport(force)
      if (res.code === 200 && res.data) {
        state.mineAiReport = res.data.reportContent
        state.mineAiTime = res.data.generateTime
        state.mineAiDialogVisible = true
        ElMessage.success('全矿 AI 分析完成')
      } else {
        ElMessage.error(res.message || '生成失败')
      }
    } catch {
      ElMessage.error('AI 服务暂时不可用，请稍后重试')
    } finally {
      state.mineAiLoading = false
    }
  }

  async function loadMineAiCache() {
    try {
      const res = await getMineAiReport()
      if (res.code === 200 && res.data) {
        state.mineAiReport = res.data.reportContent
        state.mineAiTime = res.data.generateTime
      }
    } catch {}
  }

  function toggleMineAiPanel() {
    if (state.mineAiReport) {
      state.mineAiDialogVisible = true
      return
    }
    return handleMineAi(false)
  }

  return { handleMineAi, loadMineAiCache, toggleMineAiPanel }
}
