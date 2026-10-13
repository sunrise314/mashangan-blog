import { ref } from 'vue'
import { getToken, setToken, clearToken, login as doLogin } from '../api/client'

export const token = ref<string>(getToken())

export function useAuth() {
  return {
    token,
    isLoggedIn: () => !!token.value,
    login: async (username: string, password: string) => {
      const r = await doLogin(username, password)
      setToken(r.token)
      token.value = r.token
    },
    logout: () => {
      clearToken()
      token.value = ''
    },
  }
}
