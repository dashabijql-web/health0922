import { useRoute, useRouter } from 'vue-router'

interface MineEntryPerson {
  empCode?: string
  empName?: string
  deptName?: string
  jobTypeName?: string
}

export function useMineEntryNavigation() {
  const route = useRoute()
  const router = useRouter()

  const goPortrait = (item: MineEntryPerson) => {
    if (!item?.empCode) return
    void router.push({
      name: 'ImmersiveBody',
      query: {
        empCode: item.empCode,
        empName: item.empName || '',
        deptName: item.deptName || '',
        jobTypeName: item.jobTypeName || ''
      }
    })
  }

  const backToProfile = () => {
    if (!route.query.empCode) return
    void router.push({
      name: 'ImmersiveBody',
      query: {
        empCode: route.query.empCode,
        empName: route.query.empName || ''
      }
    })
  }

  return { backToProfile, goPortrait }
}
