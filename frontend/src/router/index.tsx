import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthGuard } from '@/features/auth/AuthGuard'
import { LoginPage } from '@/features/auth/LoginPage'
import { PageLayout } from '@/components/PageLayout'
import { PlaceholderPage } from '@/components/PlaceholderPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/unauthorized',
    element: (
      <div className="flex min-h-screen items-center justify-center text-muted-foreground">
        <p>403 — You don&apos;t have permission to access this page.</p>
      </div>
    ),
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <PageLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/loads" replace /> },
      { path: 'loads', element: <PlaceholderPage title="Loads" /> },
      { path: 'loads/:id', element: <PlaceholderPage title="Load Detail" /> },
      { path: 'jobs', element: <PlaceholderPage title="Jobs" /> },
      { path: 'jobs/:id', element: <PlaceholderPage title="Job Detail" /> },
      { path: 'dispatch', element: <PlaceholderPage title="Dispatch Board" /> },
      { path: 'timeline', element: <PlaceholderPage title="Activity Timeline" /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])
