import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { AuthGuard } from '@/features/auth/AuthGuard'

// Mock useAuth so tests don't need a real AuthProvider/network
vi.mock('@/features/auth/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '@/features/auth/useAuth'
const mockUseAuth = vi.mocked(useAuth)

function renderWithRouter(credentials: unknown) {
  mockUseAuth.mockReturnValue({
    credentials: credentials as ReturnType<typeof useAuth>['credentials'],
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  })

  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/unauthorized" element={<div>Unauthorized</div>} />
        <Route
          path="/protected"
          element={
            <AuthGuard>
              <div>Protected Content</div>
            </AuthGuard>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AuthGuard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('redirects unauthenticated users to /login', () => {
    renderWithRouter(null)
    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('allows dispatcher role through', () => {
    renderWithRouter({ username: 'dispatcher', password: 'x', role: 'DISPATCHER' })
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('allows admin role through', () => {
    renderWithRouter({ username: 'admin', password: 'x', role: 'ADMIN' })
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('allows Spring-prefixed ROLE_DISPATCHER through', () => {
    renderWithRouter({ username: 'dispatcher', password: 'x', role: 'ROLE_DISPATCHER' })
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('redirects wrong role (DRIVER) to /unauthorized', () => {
    renderWithRouter({ username: 'driver', password: 'x', role: 'DRIVER' })
    expect(screen.getByText('Unauthorized')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('redirects wrong role (BILLING) to /unauthorized', () => {
    renderWithRouter({ username: 'billing', password: 'x', role: 'BILLING' })
    expect(screen.getByText('Unauthorized')).toBeInTheDocument()
  })
})
