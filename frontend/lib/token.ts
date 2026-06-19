const ACCESS_TOKEN_KEY = 'yb_access_token'

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(accessToken: string): void {
  if (typeof window === 'undefined') return
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
}

export function clearAccessToken(): void {
  if (typeof window === 'undefined') return
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export function hasToken(): boolean {
  return !!getAccessToken()
}
