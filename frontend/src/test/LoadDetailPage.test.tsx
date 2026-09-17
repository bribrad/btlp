import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { LoadDetailPage } from '@/features/loads/LoadDetailPage'
import { makeJob, makeLoad, page, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)

const LOAD_ID = '11111111-1111-1111-1111-111111111111'

function renderDetail() {
  return renderRoute(<LoadDetailPage />, {
    route: `/loads/${LOAD_ID}`,
    path: '/loads/:id',
  })
}

describe('LoadDetailPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the full record and the load’s jobs', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/loads/')) return makeLoad() as never
      return page([makeJob(), makeJob({ id: 'job-2', jobType: 'DROPOFF', sequence: 2 })]) as never
    })

    renderDetail()

    expect(
      await screen.findByRole('heading', { name: 'Chicago, IL → Dallas, TX' }),
    ).toBeInTheDocument()
    expect(screen.getByText('ACME')).toBeInTheDocument()
    expect(screen.getByText(/1,250\.50/)).toBeInTheDocument()
    expect(screen.getByText('Fragile')).toBeInTheDocument()
    expect(screen.getAllByText(LOAD_ID).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/by dispatcher/)).toHaveLength(2) // created + updated

    const jobsTable = await screen.findByRole('table')
    expect(within(jobsTable).getByRole('link', { name: 'Pickup' })).toHaveAttribute(
      'href',
      `/jobs/${makeJob().id}`,
    )
    expect(within(jobsTable).getByRole('link', { name: 'Dropoff' })).toBeInTheDocument()
  })

  it('renders placeholders instead of blanks for missing optional fields', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/loads/')) {
        return makeLoad({
          customerId: null,
          notes: null,
          rateAmount: null,
          rateCurrency: null,
          pickupWindowStart: null,
          pickupWindowEnd: null,
        }) as never
      }
      return page([]) as never
    })

    renderDetail()

    await screen.findByRole('heading', { name: 'Chicago, IL → Dallas, TX' })
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(4)
  })

  it('explains a 404 rather than rendering an empty shell', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(404, 'NOT_FOUND', 'Load not found'))
    renderDetail()

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('That record no longer exists.')).toBeInTheDocument()
  })

  it('tells the dispatcher when a load simply has no jobs', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/loads/')) return makeLoad() as never
      return page([]) as never
    })

    renderDetail()

    expect(await screen.findByText('No jobs on this load')).toBeInTheDocument()
  })

  it('keeps the load visible when its jobs fail to load', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/loads/')) return makeLoad() as never
      throw new ApiRequestError(500, 'ERROR', 'boom')
    })

    renderDetail()

    expect(
      await screen.findByRole('heading', { name: 'Chicago, IL → Dallas, TX' }),
    ).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
