import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { JobsListPage } from '@/features/jobs/JobsListPage'
import { JobDetailPage } from '@/features/jobs/JobDetailPage'
import { makeJob, makeLoad, page, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)

const JOB_ID = '22222222-2222-2222-2222-222222222222'
const LOAD_ID = '11111111-1111-1111-1111-111111111111'

describe('JobsListPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders each job with its leg, schedule, and status', async () => {
    mockGet.mockResolvedValue(
      page([makeJob(), makeJob({ id: 'job-2', jobType: 'DROPOFF', sequence: 2, status: 'EN_ROUTE' })]),
    )

    renderRoute(<JobsListPage />, { route: '/jobs' })

    // The table shell renders immediately, and the filter <select> already contains every
    // status name — so wait on something only a data row has: the job type link.
    await screen.findByRole('link', { name: 'Pickup' })
    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(within(rows[1]).getByRole('link', { name: 'Pickup' })).toBeInTheDocument()
    expect(within(rows[1]).getByText('Unassigned')).toBeInTheDocument()
    expect(within(rows[2]).getByText('En route')).toBeInTheDocument()
    // Load column links back to the parent record.
    expect(within(rows[1]).getByRole('link', { name: '11111111' })).toHaveAttribute(
      'href',
      `/loads/${LOAD_ID}`,
    )
  })

  it('sends the status and type filters to the API', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(page([makeJob()]))
    renderRoute(<JobsListPage />, { route: '/jobs' })

    await user.selectOptions(await screen.findByLabelText('Filter by status'), 'EN_ROUTE')
    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('status=EN_ROUTE')),
    )

    await user.selectOptions(screen.getByLabelText('Filter by type'), 'DROPOFF')
    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('jobType=DROPOFF')),
    )
  })

  it('scopes the list to a load when the URL carries one', async () => {
    mockGet.mockResolvedValue(page([makeJob()]))
    renderRoute(<JobsListPage />, { route: `/jobs?loadId=${LOAD_ID}` })

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining(`loadId=${LOAD_ID}`)),
    )
    expect(screen.getByText(/Load 11111111/)).toBeInTheDocument()
  })

  it('shows an empty state when no jobs exist', async () => {
    mockGet.mockResolvedValue(page([]))
    renderRoute(<JobsListPage />, { route: '/jobs' })
    expect(await screen.findByText('No jobs yet')).toBeInTheDocument()
  })

  it('surfaces a failed request', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(500, 'ERROR', 'boom'))
    renderRoute(<JobsListPage />, { route: '/jobs' })
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})

describe('JobDetailPage', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderDetail() {
    return renderRoute(<JobDetailPage />, { route: `/jobs/${JOB_ID}`, path: '/jobs/:id' })
  }

  it('renders the job and its parent load', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/jobs/')) return makeJob() as never
      return makeLoad() as never
    })

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'Pickup · Leg 1' })).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: 'Chicago, IL → Dallas, TX' }),
    ).toHaveAttribute('href', `/loads/${LOAD_ID}`)
    expect(screen.getByText('ACME')).toBeInTheDocument()
  })

  it('keeps the job readable when its parent load fails to load', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.startsWith('/jobs/')) return makeJob() as never
      throw new ApiRequestError(500, 'ERROR', 'boom')
    })

    renderDetail()

    expect(await screen.findByRole('heading', { name: 'Pickup · Leg 1' })).toBeInTheDocument()
    expect(await screen.findByText(/Couldn’t load the parent record|Couldn't load the parent record/))
      .toBeInTheDocument()
  })

  it('explains a 404', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(404, 'NOT_FOUND', 'Job not found'))
    renderDetail()

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('That record no longer exists.')).toBeInTheDocument()
  })
})
