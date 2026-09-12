import { createContext } from 'react'
import type { Credentials } from '@/api/client'

export interface AuthContextValue {
  credentials: Credentials | null
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
