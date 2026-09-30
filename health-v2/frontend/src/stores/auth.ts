import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<authApi.UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => user.value?.displayName || user.value?.username || '')

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.token
    user.value = result.user
    setToken(result.token)
  }

  async function loadUser() {
    user.value = await authApi.fetchMe()
  }

  /** 本地先清掉；通知后端失败（如令牌已过期）也不影响退出。 */
  async function logout() {
    const hadToken = !!token.value
    if (hadToken) {
      await authApi.logout().catch(() => undefined)
    }
    reset()
  }

  function reset() {
    token.value = null
    user.value = null
    clearToken()
  }

  return { token, user, isLoggedIn, displayName, login, loadUser, logout, reset }
})
