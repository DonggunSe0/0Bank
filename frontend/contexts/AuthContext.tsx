'use client'

import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { User } from '@/lib/types'
import { clearAccessToken, hasToken, setAccessToken } from '@/lib/token'
import { getMe } from '@/lib/api/users'
import { logout as apiLogout } from '@/lib/api/auth'

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (user: User, accessToken: string) => void
  logout: () => Promise<void>
  updateUser: (user: User) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let mounted = true

    async function restore() {
      if (!hasToken()) {
        if (mounted) setIsLoading(false)
        return
      }

      try {
        const me = await getMe()
        if (!mounted) return
        setUser(me)
        localStorage.setItem('yb_user', JSON.stringify(me))
      } catch {
        clearAccessToken()
        localStorage.removeItem('yb_user')
      } finally {
        if (mounted) setIsLoading(false)
      }
    }

    const stored = localStorage.getItem('yb_user')
    if (stored && hasToken()) {
      try {
        setUser(JSON.parse(stored))
      } catch {
        /* ignore */
      }
    }
    restore()

    return () => {
      mounted = false
    }
  }, [])

  useEffect(() => {
    // Listen for auth:logout event (fired by apiFetch on 401)
    const handler = () => {
      setUser(null)
      localStorage.removeItem('yb_user')
    }
    window.addEventListener('auth:logout', handler)
    return () => window.removeEventListener('auth:logout', handler)
  }, [])

  const login = useCallback(
    (u: User, accessToken: string) => {
      setAccessToken(accessToken)
      setUser(u)
      localStorage.setItem('yb_user', JSON.stringify(u))
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      if (hasToken()) await apiLogout()
    } catch {
      /* Local session cleanup must still run. */
    }
    clearAccessToken()
    setUser(null)
    localStorage.removeItem('yb_user')
  }, [])

  const updateUser = useCallback((u: User) => {
    setUser(u)
    localStorage.setItem('yb_user', JSON.stringify(u))
  }, [])

  return (
    <AuthContext.Provider
      value={{ user, isAuthenticated: !!user, isLoading, login, logout, updateUser }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
