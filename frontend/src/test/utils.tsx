import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type {
  ActivityEvent,
  Assignment,
  DispatchBoardEntry,
  Driver,
  Job,
  Load,
  PagedResponse,
} from '@/types'

/**
 * Query client for tests: no retries and no caching between cases. Pass `gcTime` when the test
 * seeds a query and inspects it later — the default of 0 evicts entries that have no observer.
 */
export function createTestQueryClient({ gcTime = 0 } = {}) {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime, staleTime: 0 } },
  })
}

interface RenderOptions {
  route?: string
  path?: string
  /** Pass a client when the test needs to inspect the cache after a mutation. */
  queryClient?: QueryClient
}

export function renderRoute(
  element: React.ReactNode,
  { route = '/', path = '*', queryClient = createTestQueryClient() }: RenderOptions = {},
) {
  return render(
    <QueryClientProvider client={queryClient}>
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

export function makeDriver(overrides: Partial<Driver> = {}): Driver {
  return {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Alice Rivera',
    phone: '555-0100',
    licenseNumber: 'LIC-001',
    availability: 'AVAILABLE',
    status: 'ACTIVE',
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  }
}

export function makeAssignment(overrides: Partial<Assignment> = {}): Assignment {
  return {
    id: '44444444-4444-4444-4444-444444444444',
    jobId: '22222222-2222-2222-2222-222222222222',
    driverId: '33333333-3333-3333-3333-333333333333',
    state: 'PENDING',
    assignedAt: '2026-03-03T14:00:00Z',
    acceptedAt: null,
    expiresAt: '2026-03-03T14:15:00Z',
    createdBy: 'dispatcher',
    updatedBy: 'dispatcher',
    createdAt: '2026-03-03T14:00:00Z',
    updatedAt: '2026-03-03T14:00:00Z',
    ...overrides,
  }
}

/** A NEEDS_DISPATCH board entry by default; pass assignment fields to place it in another lane. */
export function makeBoardEntry(overrides: Partial<DispatchBoardEntry> = {}): DispatchBoardEntry {
  return {
    jobId: '22222222-2222-2222-2222-222222222222',
    loadId: '11111111-1111-1111-1111-111111111111',
    jobType: 'PICKUP',
    sequence: 1,
    jobStatus: 'UNASSIGNED',
    scheduledAt: '2026-03-03T15:00:00Z',
    origin: 'Chicago, IL',
    destination: 'Dallas, TX',
    lane: 'NEEDS_DISPATCH',
    assignmentId: null,
    assignmentState: null,
    assignedAt: null,
    expiresAt: null,
    assignedBy: null,
    driverId: null,
    driverName: null,
    driverPhone: null,
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

/** A load-level CREATE by default; pass job/assignment fields to move it down the hierarchy. */
export function makeActivityEvent(overrides: Partial<ActivityEvent> = {}): ActivityEvent {
  return {
    id: '77777777-7777-7777-7777-777777777777',
    sequence: 1,
    occurredAt: '2026-03-03T14:00:00Z',
    actor: 'dispatcher',
    entityType: 'LOAD',
    entityId: '11111111-1111-1111-1111-111111111111',
    action: 'CREATE',
    detail: 'PLANNED',
    loadId: '11111111-1111-1111-1111-111111111111',
    origin: 'Chicago, IL',
    destination: 'Dallas, TX',
    jobId: null,
    jobType: null,
    jobSequence: null,
    assignmentId: null,
    driverId: null,
    driverName: null,
    ...overrides,
  }
}
