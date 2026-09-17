import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { JobFormPage } from '@/features/jobs/JobFormPage'
import { jobKeys } from '@/features/jobs/api'
import { createTestQueryClient, makeJob, makeLoad, page, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPut = vi.mocked(api.put)

const LOAD_ID = '11111111-1111-1111-1111-111111111111'
const JOB_ID = '22222222-2222-2222-2222-222222222222'

describe('JobFormPage — create', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue(page([makeLoad()]))
  })

  it('preselects the load it was opened from', async () => {
    renderRoute(<JobFormPage />, { route: `/jobs/new?loadId=${LOAD_ID}`, path: '/jobs/new' })
    await waitFor(() => expect(screen.getByLabelText(/Load/)).toHaveValue(LOAD_ID))
  })

  it('requires a load when opened without one', async () => {
    const user = userEvent.setup()
    renderRoute(<JobFormPage />, { route: '/jobs/new', path: '/jobs/new' })

    await user.click(screen.getByRole('button', { name: 'Create job' }))

    expect(await screen.findByText('Select a load')).toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('sends a blank leg as null so the server assigns the next one', async () => {
    const user = userEvent.setup()
    mockPost.mockResolvedValue(makeJob())
    renderRoute(<JobFormPage />, { route: `/jobs/new?loadId=${LOAD_ID}`, path: '/jobs/new' })

    await user.click(await screen.findByRole('button', { name: 'Create job' }))

    await waitFor(() => expect(mockPost).toHaveBeenCalledTimes(1))
    expect(mockPost).toHaveBeenCalledWith('/jobs', {
      loadId: LOAD_ID,
      jobType: 'PICKUP',
      sequence: null,
      scheduledAt: null,
    })
  })

  it('rejects a zero leg inline', async () => {
    const user = userEvent.setup()
    renderRoute(<JobFormPage />, { route: `/jobs/new?loadId=${LOAD_ID}`, path: '/jobs/new' })

    await user.type(await screen.findByLabelText(/Leg/), '0')
    await user.click(screen.getByRole('button', { name: 'Create job' }))

    expect(await screen.findByText('Must be greater than zero')).toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('reports a duplicate leg from the server', async () => {
    const user = userEvent.setup()
    mockPost.mockRejectedValue(
      new ApiRequestError(409, 'CONFLICT', 'Request conflicts with an existing resource.'),
    )
    renderRoute(<JobFormPage />, { route: `/jobs/new?loadId=${LOAD_ID}`, path: '/jobs/new' })

    await user.type(await screen.findByLabelText(/Leg/), '1')
    await user.click(screen.getByRole('button', { name: 'Create job' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/conflicts/i)
  })

  it('refreshes the cached lists so the new job shows up', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient({ gcTime: Infinity })
    // Seed a list query the way a visited jobs list would have.
    const listKey = jobKeys.list({ page: 0, size: 20 })
    queryClient.setQueryData(listKey, page([]))
    mockPost.mockResolvedValue(makeJob())

    renderRoute(<JobFormPage />, {
      route: `/jobs/new?loadId=${LOAD_ID}`,
      path: '/jobs/new',
      queryClient,
    })

    await user.click(await screen.findByRole('button', { name: 'Create job' }))

    await waitFor(() =>
      expect(queryClient.getQueryState(listKey)?.isInvalidated).toBe(true),
    )
    expect(queryClient.getQueryData(jobKeys.detail(JOB_ID))).toMatchObject({ id: JOB_ID })
  })
})

describe('JobFormPage — edit', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderEdit() {
    return renderRoute(<JobFormPage />, { route: `/jobs/${JOB_ID}/edit`, path: '/jobs/:id/edit' })
  }

  it('prefills the job and locks the load', async () => {
    mockGet.mockResolvedValue(makeJob({ sequence: 2, jobType: 'DROPOFF' }))
    renderEdit()

    await waitFor(() => expect(screen.getByLabelText(/Type/)).toHaveValue('DROPOFF'))
    expect(screen.getByLabelText(/Leg/)).toHaveValue(2)
    expect(screen.getByLabelText(/Load/)).toBeDisabled()
  })

  it('PUTs the edited job', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(makeJob())
    mockPut.mockResolvedValue(makeJob({ sequence: 3 }))
    renderEdit()

    const leg = await screen.findByLabelText(/Leg/)
    await user.clear(leg)
    await user.type(leg, '3')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(mockPut).toHaveBeenCalledTimes(1))
    expect(mockPut).toHaveBeenCalledWith(
      `/jobs/${JOB_ID}`,
      expect.objectContaining({ sequence: 3, jobType: 'PICKUP' }),
    )
  })

  it('requires a leg when editing, since the server does', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(makeJob())
    renderEdit()

    await user.clear(await screen.findByLabelText(/Leg/))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(mockPut).not.toHaveBeenCalled())
  })
})
