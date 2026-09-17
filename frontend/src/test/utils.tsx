import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Job, Load, PagedResponse } from '@/types'

/** Query client for tests: no retries and no caching between cases. */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  })
}

interface RenderOptions {
  route?: string
  path?: string
}

export function renderRoute(
  element: React.ReactNode,
  { route = '/', path = '*' }: RenderOptions = {},
) {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={path} element={element} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

export function page<T>(content: T[], overrides: Partial<PagedResponse<T>> = {}): PagedResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  }
}

export function makeLoad(overrides: Partial<Load> = {}): Load {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    customerId: 'ACME',
    origin: 'Chicago, IL',
    destination: 'Dallas, TX',
    pickupWindowStart: '2026-03-03T14:00:00Z',
    pickupWindowEnd: '2026-03-03T18:00:00Z',
    dropoffWindowStart: '2026-03-05T14:00:00Z',
    dropoffWindowEnd: '2026-03-05T18:00:00Z',
    rateAmount: 1250.5,
    rateCurrency: 'USD',
    notes: 'Fragile',
    status: 'PLANNED',
    createdBy: 'dispatcher',
    updatedBy: 'dispatcher',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  }
}

export function makeJob(overrides: Partial<Job> = {}): Job {
  return {
    id: '22222222-2222-2222-2222-222222222222',
    loadId: '11111111-1111-1111-1111-111111111111',
    jobType: 'PICKUP',
    sequence: 1,
    status: 'UNASSIGNED',
    scheduledAt: '2026-03-03T15:00:00Z',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  }
}
