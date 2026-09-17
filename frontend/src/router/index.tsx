import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AuthGuard } from '@/features/auth/AuthGuard'
import { LoginPage } from '@/features/auth/LoginPage'
import { PageLayout } from '@/components/PageLayout'
import { PlaceholderPage } from '@/components/PlaceholderPage'
import { LoadsListPage } from '@/features/loads/LoadsListPage'
import { LoadDetailPage } from '@/features/loads/LoadDetailPage'
import { LoadFormPage } from '@/features/loads/LoadFormPage'
import { JobsListPage } from '@/features/jobs/JobsListPage'
import { JobDetailPage } from '@/features/jobs/JobDetailPage'
import { JobFormPage } from '@/features/jobs/JobFormPage'

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
      { path: 'loads/new', element: <LoadFormPage /> },
      { path: 'loads/:id', element: <LoadDetailPage /> },
      { path: 'loads/:id/edit', element: <LoadFormPage /> },
      { path: 'jobs', element: <JobsListPage /> },
      { path: 'jobs/new', element: <JobFormPage /> },
      { path: 'jobs/:id', element: <JobDetailPage /> },
      { path: 'jobs/:id/edit', element: <JobFormPage /> },
      { path: 'dispatch', element: <PlaceholderPage title="Dispatch Board" /> },
      { path: 'timeline', element: <PlaceholderPage title="Activity Timeline" /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])
