import { getHealthPortrait } from '@/api/health-portrait'
import { searchEmployeesForCommand } from '@/api/employee'
import { getRiskWarningList } from '@/api/risk-warning'
import { normalizeEmployeeWarnings } from './immersive-body-panels'

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
    heartRateTime: string
    bloodOxygenTime: string
    temperatureTime: string
    bloodPressureTime: string
    pressureTime: string
  }
  gender: number | null
  exercise: {
    todaySteps: number
    todayCalories: number
  }
}

export interface ImmersiveWarningStats {
  warnings: Record<string, any>[]
  warningTotal: number
  pendingTotal: number
  warning7Total: number
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
      stress: null,
      heartRateTime: '',
      bloodOxygenTime: '',
      temperatureTime: '',
      bloodPressureTime: '',
      pressureTime: ''
    },
    gender: null,
    exercise: {
      todaySteps: 0,
      todayCalories: 0
    }
  }
}

export async function searchImmersiveWorkers(query = ''): Promise<CommandEmployee[]> {
  const response = await searchEmployeesForCommand(query.trim(), 20)
  return response?.code === 200 && Array.isArray(response.data) ? response.data : []
}

export async function loadImmersiveWorker(person: CommandEmployee): Promise<ImmersiveWorker> {
  const worker = createImmersiveWorker(person)
  if (!person.empCode) return worker

  const portraitResult = await getHealthPortrait(person.empCode).catch(() => null)

  if (portraitResult?.code === 200) {
    const data = portraitResult.data || {}
    const vitals = data.vitals || {}
    worker.name = data.empName || worker.name
    worker.team = data.deptName || worker.team
    worker.role = data.jobTypeName || worker.role
    worker.freshnessStatus = ['fresh', 'stale', 'offline'].includes(vitals.freshnessStatus)
      ? vitals.freshnessStatus
      : 'no_data'
    worker.lastCollected = vitals.recordTime || ''
    worker.telemetry.netty = vitals.online ? 'online' : 'offline'
    worker.gender = numberOrNull(data.gender)
    worker.exercise = {
      todaySteps: numberOrNull(data.exercise?.todaySteps) || 0,
      todayCalories: numberOrNull(data.exercise?.todayCalories) || 0
    }
    const systolic = numberOrNull(vitals.systolic)
    const diastolic = numberOrNull(vitals.diastolic)
    worker.vitals = {
      heartRate: numberOrNull(vitals.heartRate),
      bloodOxygen: numberOrNull(vitals.bloodOxygen),
      bloodPressure: systolic !== null && diastolic !== null ? `${systolic}/${diastolic}` : null,
      temperature: normalizeTemperature(vitals.temperature),
      stress: numberOrNull(vitals.pressure),
      heartRateTime: vitals.heartRateTime || '',
      bloodOxygenTime: vitals.bloodOxygenTime || '',
      temperatureTime: vitals.temperatureTime || '',
      bloodPressureTime: vitals.bloodPressureTime || '',
      pressureTime: vitals.pressureTime || ''
    }
  }

  return worker
}

export async function loadEmployeeWarningStats(empCode: string): Promise<ImmersiveWarningStats> {
  const empty: ImmersiveWarningStats = { warnings: [], warningTotal: 0, pendingTotal: 0, warning7Total: 0 }
  if (!empCode) return empty

  const now = new Date()
  const endDate = formatDate(now)
  const start30 = formatDate(new Date(now.getTime() - 29 * 86400000))
  const start7 = formatDate(new Date(now.getTime() - 6 * 86400000))

  const [warnRes, pendingRes, warn7Res] = await Promise.allSettled([
    getRiskWarningList({ userCode: empCode, startDate: start30, endDate, page: 1, size: 4 }),
    getRiskWarningList({ userCode: empCode, handled: false, startDate: start30, endDate, page: 1, size: 1 }),
    getRiskWarningList({ userCode: empCode, startDate: start7, endDate, page: 1, size: 1 })
  ])

  const stats = { ...empty }
  if (warnRes.status === 'fulfilled' && warnRes.value?.data) {
    stats.warnings = normalizeEmployeeWarnings(warnRes.value.data)
    stats.warningTotal = Number(warnRes.value.data.total) || 0
  }
  if (pendingRes.status === 'fulfilled' && pendingRes.value?.data) {
    stats.pendingTotal = Number(pendingRes.value.data.total) || 0
  }
  if (warn7Res.status === 'fulfilled' && warn7Res.value?.data) {
    stats.warning7Total = Number(warn7Res.value.data.total) || 0
  }
  return stats
}
