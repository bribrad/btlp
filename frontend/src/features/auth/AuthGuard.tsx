import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

const ALLOWED_ROLES = ['ROLE_DISPATCHER', 'ROLE_ADMIN', 'DISPATCHER', 'ADMIN']

interface AuthGuardProps {
  children: React.ReactNode
}

export function AuthGuard({ children }: AuthGuardProps) {
  const { credentials } = useAuth()
  const location = useLocation()

  if (!credentials) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  const role = credentials.role.toUpperCase()
  const hasAccess = ALLOWED_ROLES.some(r => role === r || role.includes(r.replace('ROLE_', '')))

  if (!hasAccess) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
