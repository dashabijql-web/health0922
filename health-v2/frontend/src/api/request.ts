import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from './token'

/** 后端统一返回格式 { code, message, data }（docs/01 第五节），code = 0 成功。 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 请求失败：status 是 HTTP 状态码（网络不通时为 0），message 是给用户看的提示。 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: number | null
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface RequestOptions extends AxiosRequestConfig {
  /** 为 true 时出错不弹提示，由调用方自己处理 */
  silent?: boolean
}

let unauthorizedHandler: (() => void) | null = null

/** 令牌失效（HTTP 401）时要做什么，由 main.ts 注册（跳到登录页），避免这里反过来依赖路由。 */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('satoken', token)
  }
  return config
})

function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    const status = error.response?.status ?? 0
    const body = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
    if (body && typeof body.message === 'string') {
      return new ApiError(body.message, status, typeof body.code === 'number' ? body.code : null)
    }
    if (status === 0) {
      return new ApiError(error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接服务器', 0, null)
    }
    return new ApiError(`请求失败（HTTP ${status}）`, status, null)
  }
  return new ApiError('请求失败', 0, null)
}

async function send<T>(config: RequestOptions): Promise<T> {
  const { silent, ...axiosConfig } = config
  try {
    const response = await http.request<ApiEnvelope<T>>(axiosConfig)
    const body = response.data
    if (body.code !== 0) {
      throw new ApiError(body.message || '请求失败', response.status, body.code)
    }
    return body.data
  } catch (raw) {
    const error = raw instanceof ApiError ? raw : toApiError(raw)
    const isLoginRequest = axiosConfig.url === '/auth/login'
    if (error.status === 401 && !isLoginRequest) {
      clearToken()
      unauthorizedHandler?.()
    }
    if (!silent) {
      ElMessage.error(error.message)
    }
    throw error
  }
}

export function get<T>(url: string, options: RequestOptions = {}): Promise<T> {
  return send<T>({ ...options, method: 'GET', url })
}

export function post<T>(url: string, data?: unknown, options: RequestOptions = {}): Promise<T> {
  return send<T>({ ...options, method: 'POST', url, data })
}
