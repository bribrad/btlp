import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoadFormPage } from '@/features/loads/LoadFormPage'
import { createTestQueryClient, makeLoad, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPut = vi.mocked(api.put)

const LOAD_ID = '11111111-1111-1111-1111-111111111111'

describe('LoadFormPage — create', () => {
  beforeEach(() => vi.clearAllMocks())

  it('surfaces required-field errors inline and does not call the API', async () => {
    const user = userEvent.setup()
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.click(screen.getByRole('button', { name: 'Create load' }))

    expect(await screen.findByText('Origin is required')).toBeInTheDocument()
    expect(screen.getByText('Destination is required')).toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('wires each error to its input for assistive tech', async () => {
    const user = userEvent.setup()
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.click(screen.getByRole('button', { name: 'Create load' }))
    const origin = await screen.findByLabelText(/Origin/)

    expect(origin).toHaveAttribute('aria-invalid', 'true')
    const describedBy = origin.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    expect(document.getElementById(describedBy!)).toHaveTextContent('Origin is required')
  })

  it('surfaces a timeline error on the offending field', async () => {
    const user = userEvent.setup()
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.type(screen.getByLabelText(/Origin/), 'Chicago, IL')
    await user.type(screen.getByLabelText(/Destination/), 'Dallas, TX')
    await user.type(screen.getByLabelText('Pickup window start'), '2026-03-04T10:00')
    await user.type(screen.getByLabelText('Pickup window end'), '2026-03-03T10:00')
    await user.click(screen.getByRole('button', { name: 'Create load' }))

    expect(
      await screen.findByText('Must be at or after the pickup window start'),
    ).toBeInTheDocument()
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('posts the load and lands on its detail page', async () => {
    const user = userEvent.setup()
    mockPost.mockResolvedValue(makeLoad())
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.type(screen.getByLabelText(/Origin/), 'Chicago, IL')
    await user.type(screen.getByLabelText(/Destination/), 'Dallas, TX')
    await user.type(screen.getByLabelText(/Customer/), 'ACME')
    await user.click(screen.getByRole('button', { name: 'Create load' }))

    await waitFor(() => expect(mockPost).toHaveBeenCalledTimes(1))
    expect(mockPost).toHaveBeenCalledWith(
      '/loads',
      expect.objectContaining({
        origin: 'Chicago, IL',
        destination: 'Dallas, TX',
        customerId: 'ACME',
      }),
    )
  })

  it('submits on Enter from a text field', async () => {
    const user = userEvent.setup()
    mockPost.mockResolvedValue(makeLoad())
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.type(screen.getByLabelText(/Origin/), 'Chicago, IL')
    await user.type(screen.getByLabelText(/Destination/), 'Dallas, TX{Enter}')

    await waitFor(() => expect(mockPost).toHaveBeenCalledTimes(1))
  })

  it('maps a server field rejection back onto the field', async () => {
    const user = userEvent.setup()
    mockPost.mockRejectedValue(
      new ApiRequestError(400, 'VALIDATION_ERROR', 'origin must not be blank'),
    )
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.type(screen.getByLabelText(/Origin/), 'x')
    await user.type(screen.getByLabelText(/Destination/), 'Dallas, TX')
    await user.click(screen.getByRole('button', { name: 'Create load' }))

    expect(await screen.findByText('must not be blank')).toBeInTheDocument()
  })

  it('shows a form-level alert when the failure is not field-specific', async () => {
    const user = userEvent.setup()
    mockPost.mockRejectedValue(new ApiRequestError(500, 'ERROR', 'boom'))
    renderRoute(<LoadFormPage />, { route: '/loads/new', path: '/loads/new' })

    await user.type(screen.getByLabelText(/Origin/), 'Chicago, IL')
    await user.type(screen.getByLabelText(/Destination/), 'Dallas, TX')
    await user.click(screen.getByRole('button', { name: 'Create load' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The server had a problem. Try again in a moment.')
  })
})

describe('LoadFormPage — edit', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderEdit() {
    return renderRoute(<LoadFormPage />, {
      route: `/loads/${LOAD_ID}/edit`,
      path: '/loads/:id/edit',
    })
  }

  it('prefills the existing values', async () => {
    mockGet.mockResolvedValue(makeLoad())
    renderEdit()

    expect(await screen.findByDisplayValue('Chicago, IL')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Dallas, TX')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1250.5')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Fragile')).toBeInTheDocument()
  })

  it('hides the customer field, which the API will not update', async () => {
    mockGet.mockResolvedValue(makeLoad())
    renderEdit()

    await screen.findByDisplayValue('Chicago, IL')
    expect(screen.queryByLabelText(/Customer/)).not.toBeInTheDocument()
  })

  it('PUTs only the changed record', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(makeLoad())
    mockPut.mockResolvedValue(makeLoad({ origin: 'Denver, CO' }))
    renderEdit()

    const origin = await screen.findByDisplayValue('Chicago, IL')
    await user.clear(origin)
    await user.type(origin, 'Denver, CO')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(mockPut).toHaveBeenCalledTimes(1))
    expect(mockPut).toHaveBeenCalledWith(
      `/loads/${LOAD_ID}`,
      expect.objectContaining({ origin: 'Denver, CO' }),
    )
  })

  it('reports a load that no longer exists instead of an empty form', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(404, 'NOT_FOUND', 'Load not found'))
    renderEdit()

    expect(await screen.findByText('That record no longer exists.')).toBeInTheDocument()
  })
})

describe('LoadFormPage — refetch safety', () => {
  beforeEach(() => vi.clearAllMocks())

  it('does not discard in-progress edits when the record refetches', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient({ gcTime: Infinity })
    mockGet.mockResolvedValue(makeLoad())

    renderRoute(<LoadFormPage />, {
      route: `/loads/${LOAD_ID}/edit`,
      path: '/loads/:id/edit',
      queryClient,
    })

    const origin = await screen.findByDisplayValue('Chicago, IL')
    await user.clear(origin)
    await user.type(origin, 'Denver, CO')

    // A background refetch lands with the unedited server copy.
    mockGet.mockResolvedValue(makeLoad({ updatedAt: '2026-04-01T10:00:00Z' }))
    await queryClient.refetchQueries()

    await waitFor(() => expect(screen.getByLabelText(/Origin/)).toHaveValue('Denver, CO'))
  })
})
