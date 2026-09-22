import { ref } from 'vue'

export function useSafetyCommandInteractions() {
  const incidentDrawerVisible = ref(false)
  const currentEvent = ref(null)
  const personDrawerVisible = ref(false)
  const personDrawerUserCode = ref('')
  const personDrawerUserName = ref('')

  function showEventDetail(event) {
    currentEvent.value = event
    incidentDrawerVisible.value = true
  }

  function onShowPerson(person) {
    personDrawerUserCode.value = person.userCode || ''
    personDrawerUserName.value = person.name || ''
    personDrawerVisible.value = true
  }

  function onShowPersonFromEvent(event) {
    personDrawerUserCode.value = event.userCode || ''
    personDrawerUserName.value = event.user || ''
    personDrawerVisible.value = true
  }

  function onHandleEvent(event) {
    showEventDetail(event)
  }

  return {
    currentEvent,
    incidentDrawerVisible,
    onHandleEvent,
    onShowPerson,
    onShowPersonFromEvent,
    personDrawerUserCode,
    personDrawerUserName,
    personDrawerVisible,
    showEventDetail
  }
}
