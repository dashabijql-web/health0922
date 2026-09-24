export function buildDashboardAiSummary({
  mineAiReport,
  kpiUnhandledHigh,
  preShiftData,
  riskDeptList,
  latestDangerEvent
}) {
  const normalized = (mineAiReport || '')
    .replace(/^#+\s*/gm, '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\r?\n+/g, ' ')
    .trim()

  if (normalized) return normalized.slice(0, 160) + (normalized.length > 160 ? '…' : '')

  const parts: string[] = []
  if (kpiUnhandledHigh > 0) parts.push(`当前有 ${kpiUnhandledHigh} 条高危预警待处理`)
  if ((preShiftData?.failedCount || 0) > 0) parts.push(`${preShiftData.failedCount} 人未通过入井健康准入`)
  if (riskDeptList?.[0]) parts.push(`${riskDeptList[0].name} 为当前重点关注部门`)
  if (latestDangerEvent?.userName) parts.push(`${latestDangerEvent.userName} 是最新重点关注人员`)
  return parts.length
    ? parts.join('，') + '。'
    : '当前暂无明显高风险信号，可持续关注趋势变化、准入状态和设备在线情况。'
}

export function getFocusWarningEvents(warningEvents) {
  const seen = new Set()
  return (warningEvents || []).filter((event) => {
    if (event.handled) return false
    const personKey = event.userCode || event.userName
    if (!personKey || seen.has(personKey)) return false
    seen.add(personKey)
    return true
  }).slice(0, 4)
}

export function getLatestDangerEvent(warningEvents) {
  return (warningEvents || []).find((e) => e.level === 'danger' && !e.handled) || null
}

export function buildWarningTypeData({ warningTypesData, warningEvents }) {
  const colors = ['#ff5252', '#00d4ff', '#3eb7ff', '#ffd200', '#38ef7d', '#FFB84D']
  const source = (warningTypesData || []).length
    ? warningTypesData
    : (() => {
        const typeMap = {};
        (warningEvents || []).forEach((e) => {
          const key = e.type || '其他'
          typeMap[key] = (typeMap[key] || 0) + 1
        })
        return Object.entries(typeMap).map(([name, value]) => ({ name, value }))
      })()

  const total = source.reduce((sum, item) => sum + (item.value || item.count || 0), 0) || 1
  return source.slice(0, 6).map((item, index) => ({
    name: item.name,
    value: item.value || item.count || 0,
    color: colors[index],
    pct: Math.round((item.value || item.count || 0) / total * 100)
  }))
}

export function buildRiskDeptList(deptDataList) {
  if (!(deptDataList || []).length) return []
  const sorted = [...deptDataList].sort((a, b) => b.count - a.count)
  const max = sorted[0].count || 1
  const colors = ['#ff5252', '#ffd200', '#FFB84D', '#00d4ff', '#38ef7d', '#00c8c8', '#3eb7ff', '#67C23A', '#E6A23C', '#F56C6C']

  return sorted.map((item, index) => {
    const pct = Math.round(item.count / max * 100)
    const prev = item.prevCount || 0
    const delta = prev > 0 && Math.abs(item.count - prev) / prev <= 2
      ? Math.round((item.count - prev) / prev * 100)
      : null
    return {
      name: item.name,
      pct,
      count: item.count,
      color: colors[index % colors.length],
      delta
    }
  })
}
