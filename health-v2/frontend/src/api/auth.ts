import { get, post } from './request'

export interface UserInfo {
  id: number
  username: string
  displayName: string | null
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export interface LoginHint {
  text: string
}

/** 登录页提示；LOGIN_SHOW_DEFAULT_ACCOUNT=false 时后端返回 null。 */
export function fetchLoginHint() {
  return get<LoginHint | null>('/auth/login-hint', { silent: true })
}

export function login(username: string, password: string) {
  return post<LoginResult>('/auth/login', { username, password }, { silent: true })
}

export function logout() {
  return post<void>('/auth/logout', undefined, { silent: true })
}

export function fetchMe() {
  return get<UserInfo>('/auth/me', { silent: true })
}
