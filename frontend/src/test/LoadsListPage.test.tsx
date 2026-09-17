import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoadsListPage } from '@/features/loads/LoadsListPage'
import { makeLoad, page, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)

describe('LoadsListPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders each load with its schedule, rate, and status', async () => {
    mockGet.mockResolvedValue(
      page([
        makeLoad(),
        makeLoad({
          id: '33333333-3333-3333-3333-333333333333',
          origin: 'Denver, CO',
          destination: 'Phoenix, AZ',
          status: 'IN_TRANSIT',
        }),
      ]),
    )

    renderRoute(<LoadsListPage />, { route: '/loads' })

    expect(await screen.findByText('Chicago, IL → Dallas, TX')).toBeInTheDocument()

    // Scoped to the table: the status filter lists the same status names as options.
    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(within(rows[1]).getByText('Planned')).toBeInTheDocument()
    expect(within(rows[1]).getByText(/1,250\.50/)).toBeInTheDocument()
    expect(within(rows[2]).getByText('Denver, CO → Phoenix, AZ')).toBeInTheDocument()
    expect(within(rows[2]).getByText('In transit')).toBeInTheDocument()
    expect(screen.getByText('1–2 of 2')).toBeInTheDocument()
  })

  it('shows a first-run empty state when nothing exists', async () => {
    mockGet.mockResolvedValue(page([]))
    renderRoute(<LoadsListPage />, { route: '/loads' })
    expect(await screen.findByText('No loads yet')).toBeInTheDocument()
  })

  it('distinguishes an empty search result from an empty system', async () => {
    mockGet.mockResolvedValue(page([]))
    renderRoute(<LoadsListPage />, { route: '/loads?q=nowhere' })
    expect(await screen.findByText('No loads match these filters')).toBeInTheDocument()
  })

  it('surfaces a server failure with a retry', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(500, 'ERROR', 'boom'))
    renderRoute(<LoadsListPage />, { route: '/loads' })

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/server had a problem/i)).toBeInTheDocument()
    expect(within(alert).getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })

  it('sends the typed search term to the API', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(page([makeLoad()]))
    renderRoute(<LoadsListPage />, { route: '/loads' })

    await user.type(await screen.findByLabelText('Search loads'), 'chicago')

    await waitFor(
      () => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('q=chicago')),
      { timeout: 2000 },
    )
  })

  it('sends the selected status to the API', async () => {
    const user = userEvent.setup()
    mockGet.mockResolvedValue(page([makeLoad()]))
    renderRoute(<LoadsListPage />, { route: '/loads' })

    await user.selectOptions(await screen.findByLabelText('Filter by status'), 'IN_TRANSIT')

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('status=IN_TRANSIT')),
    )
  })

  it('reads the page out of the URL and requests it', async () => {
    mockGet.mockResolvedValue(page([makeLoad()], { page: 1, totalElements: 25, totalPages: 2 }))
    renderRoute(<LoadsListPage />, { route: '/loads?page=1' })

    await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('page=1')))
    expect(await screen.findByText('21–25 of 25')).toBeInTheDocument()
  })

  it('omits empty filters from the request', async () => {
    mockGet.mockResolvedValue(page([makeLoad()]))
    renderRoute(<LoadsListPage />, { route: '/loads' })

    await waitFor(() => expect(mockGet).toHaveBeenCalled())
    expect(mockGet).toHaveBeenCalledWith('/loads?page=0&size=20')
  })
})
