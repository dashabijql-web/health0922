// 令牌存在浏览器本地（localStorage）。无痕模式等情况下读写可能失败，失败时当作没有令牌。
const TOKEN_KEY = 'hv2-token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* 存不下就只在本次页面里有效 */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* 忽略 */
  }
}
