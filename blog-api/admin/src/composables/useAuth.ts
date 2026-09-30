import { ref } from 'vue'
import { getToken, setToken, clearToken, login as doLogin } from '../api/client'

export const token = ref<string>(getToken())

const ANALYTICS_KEY = 'analytics_token'

export function useAuth() {
  return {
    token,
    isLoggedIn: () => !!token.value,
    /** 看板 API 鉴权密码：登录后台时一并保存，避免二次输入口令。 */
    getAnalyticsToken: () => localStorage.getItem(ANALYTICS_KEY) || '',
    login: async (username: string, password: string) => {
      const r = await doLogin(username, password)
      setToken(r.token)
      token.value = r.token
      // SPA 登录密码同时也是 analytics 接口的口令（ANALYTICS_PASSWORD）
      localStorage.setItem(ANALYTICS_KEY, password)
    },
    logout: () => {
      clearToken()
      localStorage.removeItem(ANALYTICS_KEY)
      token.value = ''
    },
  }
}
