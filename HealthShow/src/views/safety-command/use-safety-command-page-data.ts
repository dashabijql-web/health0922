import { onMounted, onUnmounted, ref } from 'vue'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { createEventBinding } from '@/utils/task-timer'
import { buildProcessedWarningData } from './safety-command-view-model'
import {
  fetchSafetyCommandCritical,
  fetchSafetyCommandData
} from './safety-command-runtime'

function updateClock(currentDate, currentTime) {
  const now = new Date()
  currentDate.value = now
    .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .replace(/\//g, '-')
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

export function useSafetyCommandPageData() {
  const commandSummary = ref({ warning: {}, device: {} })
  const dataAsOf = ref('')
  const currentDate = ref('')
  const currentTime = ref('')
  const handledCount = ref(0)
  const pendingWarnings = ref(null)
  const totalWarnings = ref(null)
  const criticalWarnings = ref(0)
  const events = ref<any[]>([])
  const warningTrend = ref<any[]>([])
  const riskPersons = ref<any[]>([])
  const departments = ref<any[]>([])

  const processWarningData = (raw) => {
    const nextState = buildProcessedWarningData(raw)
    events.value = nextState.events
    riskPersons.value = nextState.riskPersons
  }

  const fetchCritical = async () => fetchSafetyCommandCritical({
    processWarningData,
    handledCountRef: handledCount,
    pendingWarningsRef: pendingWarnings,
    totalWarningsRef: totalWarnings,
    criticalWarningsRef: criticalWarnings,
    commandSummaryRef: commandSummary,
    dataAsOfRef: dataAsOf
  })
  const fetchAllData = async () => fetchSafetyCommandData({
    handledCountRef: handledCount,
    pendingWarningsRef: pendingWarnings,
    totalWarningsRef: totalWarnings,
    criticalWarningsRef: criticalWarnings,
    commandSummaryRef: commandSummary,
    dataAsOfRef: dataAsOf,
    warningTrendRef: warningTrend,
    departmentsRef: departments,
    processWarningData
  })

  const { start: startClock, stop: stopClock } = useIntervalTask(() => updateClock(currentDate, currentTime), 1000)
  const { start: startCriticalPolling, stop: stopCriticalPolling } = useIntervalTask(fetchCritical, 5000)
  const { start: startAllPolling, stop: stopAllPolling } = useIntervalTask(fetchAllData, 30000)
  let visibilityBinding: ReturnType<typeof createEventBinding> | null = null

  const startPolling = () => {
    startCriticalPolling()
    startAllPolling()
  }

  const stopPolling = () => {
    stopCriticalPolling()
    stopAllPolling()
  }

  const onVisibilityChange = () => {
    if (document.hidden) {
      stopPolling()
      return
    }
    fetchAllData()
    startPolling()
  }

  onMounted(() => {
    updateClock(currentDate, currentTime)
    startClock()
    fetchAllData()
    startPolling()
    visibilityBinding = createEventBinding(() => document, 'visibilitychange', onVisibilityChange)
    visibilityBinding.start()
  })

  onUnmounted(() => {
    stopClock()
    stopPolling()
    visibilityBinding?.stop?.()
  })

  return {
    commandSummary,
    currentDate,
    currentTime,
    dataAsOf,
    departments,
    events,
    fetchAllData,
    fetchCritical,
    handledCount,
    pendingWarnings,
    totalWarnings,
    criticalWarnings,
    processWarningData,
    riskPersons,
    startPolling,
    stopPolling,
    warningTrend
  }
}
