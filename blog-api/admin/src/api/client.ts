const TOKEN_KEY = 'token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(t: string) {
  localStorage.setItem(TOKEN_KEY, t)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function api<T = any>(method: string, path: string, body?: any): Promise<T> {
  const opts: RequestInit = {
    method,
    headers: { 'Authorization': 'Bearer ' + getToken(), 'Content-Type': 'application/json' },
  }
  if (body !== undefined) opts.body = JSON.stringify(body)
  const r = await fetch(path, opts)
  if (r.status === 401 || r.status === 403) {
    clearToken()
    window.location.href = '/admin/'
    throw new Error('未授权')
  }
  if (!r.ok) {
    const t = await r.text()
    throw new Error(t || r.statusText)
  }
  const text = await r.text()
  return (text ? JSON.parse(text) : null) as T
}

export async function uploadFile(path: string, file: File, field = 'file'): Promise<any> {
  const fd = new FormData()
  fd.append(field, file)
  const r = await fetch(path, {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + getToken() },
    body: fd,
  })
  if (!r.ok) {
    const t = await r.text()
    throw new Error(t || r.statusText)
  }
  return await r.json()
}

export async function login(username: string, password: string): Promise<{ token: string }> {
  const r = await fetch('/api/admin/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!r.ok) throw new Error('用户名或密码错误')
  return await r.json()
}
