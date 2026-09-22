import { getEmployeeHealthHistory } from '@/api/health'
import { getHealthPortrait } from '@/api/health-portrait'
import { searchEmployeesForCommand } from '@/api/employee'

export interface ImmersiveTrendPoint {
  time: string
  timestamp: number
  heartRate: number | null
  systolic: number | null
  diastolic: number | null
  bloodOxygen: number | null
  temperature: number | null
  stress: number | null
}

export interface ImmersiveWorker {
  id: string
  name: string
  team: string
  role: string
  imei: string
  freshnessStatus: 'fresh' | 'stale' | 'offline' | 'no_data'
  lastCollected: string
  telemetry: {
    battery: number | null
    netty: 'online' | 'offline' | 'reconnecting'
    signal: '5G' | '4G' | 'weak' | 'none'
    wearing: 'confirmed' | 'unworn' | 'unknown'
  }
  vitals: {
    heartRate: number | null
    bloodOxygen: number | null
    bloodPressure: string | null
    temperature: number | null
    stress: number | null
  }
  trend: ImmersiveTrendPoint[]
}

export interface CommandEmployee {
  empCode: string
  empName?: string
  deptName?: string
  jobTypeName?: string
  imei?: string
  online?: boolean
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function numberOrNull(value: unknown) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function normalizeTemperature(value: unknown) {
  const number = numberOrNull(value)
  if (number === null) return null
  return number > 100 ? Number((number / 10).toFixed(1)) : Number(number.toFixed(1))
}

export function createImmersiveWorker(person: CommandEmployee): ImmersiveWorker {
  return {
    id: person.empCode || '',
    name: person.empName || '未命名人员',
    team: person.deptName || '部门未录入',
    role: person.jobTypeName || '岗位未录入',
    imei: person.imei || '',
    freshnessStatus: 'no_data',
    lastCollected: '',
    telemetry: {
      battery: null,
      netty: person.online ? 'online' : 'offline',
      signal: 'none',
      wearing: 'unknown'
    },
    vitals: {
      heartRate: null,
      bloodOxygen: null,
      bloodPressure: null,
      temperature: null,
      stress: null
    },
    trend: []
  }
}

export async function searchImmersiveWorkers(query = ''): Promise<CommandEmployee[]> {
  const response = await searchEmployeesForCommand(query.trim(), 20)
  return response?.code === 200 && Array.isArray(response.data) ? response.data : []
}

export async function loadImmersiveWorker(person: CommandEmployee): Promise<ImmersiveWorker> {
  const worker = createImmersiveWorker(person)
  if (!person.empCode) return worker

  const today = new Date()
  const yesterday = new Date(today.getTime() - 86400000)
  const [portraitResult, historyResult] = await Promise.allSettled([
    getHealthPortrait(person.empCode),
    getEmployeeHealthHistory({
      userCode: person.empCode,
      startDate: formatDate(yesterday),
      endDate: formatDate(today)
    })
  ])

  if (portraitResult.status === 'fulfilled' && portraitResult.value?.code === 200) {
    const data = portraitResult.value.data || {}
    const vitals = data.vitals || {}
    worker.name = data.empName || worker.name
    worker.team = data.deptName || worker.team
    worker.role = data.jobTypeName || worker.role
    worker.freshnessStatus = ['fresh', 'stale', 'offline'].includes(vitals.freshnessStatus)
      ? vitals.freshnessStatus
      : 'no_data'
    worker.lastCollected = vitals.recordTime || ''
    worker.telemetry.netty = vitals.online ? 'online' : 'offline'
    const systolic = numberOrNull(vitals.systolic)
    const diastolic = numberOrNull(vitals.diastolic)
    worker.vitals = {
      heartRate: numberOrNull(vitals.heartRate),
      bloodOxygen: numberOrNull(vitals.bloodOxygen),
      bloodPressure: systolic !== null && diastolic !== null ? `${systolic}/${diastolic}` : null,
      temperature: normalizeTemperature(vitals.temperature),
      stress: numberOrNull(vitals.pressure)
    }
  }

  if (historyResult.status === 'fulfilled' && historyResult.value?.code === 200) {
    const points = Array.isArray(historyResult.value.data?.points) ? historyResult.value.data.points : []
    worker.trend = points.map((point: Record<string, unknown>) => {
      const timestamp = Date.parse(String(point.time || ''))
      return {
        time: String(point.time || '').slice(11, 16) || String(point.time || ''),
        timestamp: Number.isFinite(timestamp) ? timestamp : 0,
        heartRate: numberOrNull(point.heartRate),
        systolic: numberOrNull(point.systolic),
        diastolic: numberOrNull(point.diastolic),
        bloodOxygen: numberOrNull(point.bloodOxygen),
        temperature: normalizeTemperature(point.temperature),
        stress: numberOrNull(point.pressure)
      }
    })
  }

  return worker
}
