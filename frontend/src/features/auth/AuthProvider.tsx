import { useCallback, useEffect, useState } from 'react'
import {
  api,
  clearCredentials,
  loadCredentials,
  saveCredentials,
  type Credentials,
} from '@/api/client'
import { AuthContext } from './authContext'

interface MeResponse {
  username: string
  authorities: string[]
}

interface AuthState {
  credentials: Credentials | null
  isLoading: boolean
}

/** Provides auth state and login/logout actions to the component tree. */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    credentials: loadCredentials(),
    isLoading: false,
  })

  // Validate stored credentials on mount — clear if stale/rejected
  useEffect(() => {
    const stored = loadCredentials()
    if (!stored) return
    api
      .get<MeResponse>('/me', stored)
      .catch(() => {
        clearCredentials()
        setState({ credentials: null, isLoading: false })
      })
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    setState(s => ({ ...s, isLoading: true }))
    const me = await api.get<MeResponse>('/me', { username, password, role: '' })
    const creds: Credentials = { username, password, role: me.authorities[0] ?? '' }
    saveCredentials(creds)
    setState({ credentials: creds, isLoading: false })
  }, [])

  const logout = useCallback(() => {
    clearCredentials()
    setState({ credentials: null, isLoading: false })
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
