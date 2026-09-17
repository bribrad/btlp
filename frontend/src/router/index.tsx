import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthGuard } from '@/features/auth/AuthGuard'
import { LoginPage } from '@/features/auth/LoginPage'
import { PageLayout } from '@/components/PageLayout'
import { PlaceholderPage } from '@/components/PlaceholderPage'
import { LoadsListPage } from '@/features/loads/LoadsListPage'
import { LoadDetailPage } from '@/features/loads/LoadDetailPage'
import { JobsListPage } from '@/features/jobs/JobsListPage'
import { JobDetailPage } from '@/features/jobs/JobDetailPage'

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
      { path: 'loads', element: <LoadsListPage /> },
      { path: 'loads/:id', element: <LoadDetailPage /> },
      { path: 'jobs', element: <JobsListPage /> },
      { path: 'jobs/:id', element: <JobDetailPage /> },
      { path: 'dispatch', element: <PlaceholderPage title="Dispatch Board" /> },
      { path: 'timeline', element: <PlaceholderPage title="Activity Timeline" /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])
