import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ActivityPage } from '@/features/activity/ActivityPage'
import { ActivityTimeline } from '@/features/activity/ActivityTimeline'
import { makeActivityEvent, makeJob, makeLoad, page, renderRoute } from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)

const LOAD_ID = '11111111-1111-1111-1111-111111111111'
const JOB_ID = '22222222-2222-2222-2222-222222222222'
const ALICE_ID = '33333333-3333-3333-3333-333333333333'

/** One dispatch action's worth of events, newest first — the order the API returns them in. */
const ACCEPTED = makeActivityEvent({
  id: 'event-accept',
  sequence: 4,
  entityType: 'ASSIGNMENT',
  entityId: '44444444-4444-4444-4444-444444444444',
  action: 'ACCEPT',
  detail: 'ACCEPTED',
  actor: 'driver',
  jobId: JOB_ID,
  jobType: 'PICKUP',
  jobSequence: 1,
  assignmentId: '44444444-4444-4444-4444-444444444444',
  driverId: ALICE_ID,
  driverName: 'Alice Rivera',
})

const JOB_ASSIGNED = makeActivityEvent({
  id: 'event-status',
  sequence: 3,
  entityType: 'JOB',
  entityId: JOB_ID,
  action: 'STATUS_CHANGE',
  detail: 'ASSIGNED',
  actor: 'driver',
  jobId: JOB_ID,
  jobType: 'PICKUP',
  jobSequence: 1,
})

const DISPATCHED = makeActivityEvent({
  id: 'event-assign',
  sequence: 2,
  entityType: 'ASSIGNMENT',
  entityId: '44444444-4444-4444-4444-444444444444',
  action: 'ASSIGN',
  detail: 'PENDING',
  jobId: JOB_ID,
  jobType: 'PICKUP',
  jobSequence: 1,
  assignmentId: '44444444-4444-4444-4444-444444444444',
  driverId: ALICE_ID,
  driverName: 'Alice Rivera',
})

const LOAD_CREATED = makeActivityEvent({ id: 'event-create', sequence: 1 })

const FEED = [ACCEPTED, JOB_ASSIGNED, DISPATCHED, LOAD_CREATED]

/** Routes the page's three requests to fixtures; the feed is whatever the test passes in. */
function stubPage(events = FEED, options: { jobs?: ReturnType<typeof makeJob>[] } = {}) {
  mockGet.mockImplementation(async (path: string) => {
    if (path.startsWith('/activity')) return page(events) as never
    if (path.startsWith('/loads')) return page([makeLoad()]) as never
    if (path.startsWith('/jobs')) return page(options.jobs ?? [makeJob()]) as never
    throw new Error(`unexpected GET ${path}`)
  })
}

describe('ActivityTimeline', () => {
  beforeEach(() => vi.clearAllMocks())

  it('says who did what and when for each event', async () => {
    mockGet.mockResolvedValue(page(FEED))
    renderRoute(<ActivityTimeline />)

    expect(await screen.findByText('Alice Rivera accepted')).toBeInTheDocument()
    expect(screen.getByText('Job status → Assigned')).toBeInTheDocument()
    expect(screen.getByText('Dispatched to Alice Rivera')).toBeInTheDocument()
    expect(screen.getByText('Load created')).toBeInTheDocument()

    // Who, and when — the timestamp is machine-readable as well as rendered.
    expect(screen.getAllByText(/by dispatcher/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/by driver/).length).toBe(2)
    expect(screen.getAllByRole('time')[0]).toHaveAttribute('datetime', ACCEPTED.occurredAt)
  })

  it('renders events in the order the API returned rather than re-sorting them', async () => {
    // Every event of a dispatch action shares one transaction timestamp, so a client-side sort
    // on time would be free to shuffle them; the backend sequence is the only stable order.
    const sameInstant = FEED.map(event => ({ ...event, occurredAt: '2026-03-03T14:00:00Z' }))
    mockGet.mockResolvedValue(page(sameInstant))
    renderRoute(<ActivityTimeline />)

    await screen.findByText('Alice Rivera accepted')
    const rendered = screen.getAllByRole('listitem').map(item => item.textContent)
    expect(rendered[0]).toContain('Alice Rivera accepted')
    expect(rendered[1]).toContain('Job status → Assigned')
    expect(rendered[2]).toContain('Dispatched to Alice Rivera')
    expect(rendered[3]).toContain('Load created')
  })

  it('scopes the request to a job and drops the context that is now implied', async () => {
    mockGet.mockResolvedValue(page([ACCEPTED]))
    renderRoute(<ActivityTimeline jobId={JOB_ID} />)

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining(`jobId=${JOB_ID}`)),
    )
    expect(await screen.findByText('Alice Rivera accepted')).toBeInTheDocument()
    // Already on the job's own page — no point repeating its route or leg on every row.
    expect(screen.queryByText(/Chicago, IL/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Leg 1/)).not.toBeInTheDocument()
  })

  it('keeps the leg but drops the route when scoped to a load', async () => {
    mockGet.mockResolvedValue(page([ACCEPTED]))
    renderRoute(<ActivityTimeline loadId={LOAD_ID} />)

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining(`loadId=${LOAD_ID}`)),
    )
    expect(await screen.findByText(/Pickup · Leg 1/)).toBeInTheDocument()
    expect(screen.queryByText(/Chicago, IL/)).not.toBeInTheDocument()
  })

  it('links an unscoped row to the deepest record it concerns', async () => {
    mockGet.mockResolvedValue(page([ACCEPTED, LOAD_CREATED]))
    renderRoute(<ActivityTimeline />)

    // The assignment event knows its leg, so it links to the job; the load event only knows
    // the load.
    expect(
      await screen.findByRole('link', { name: 'Chicago, IL → Dallas, TX · Pickup · Leg 1' }),
    ).toHaveAttribute('href', `/jobs/${JOB_ID}`)
    expect(screen.getByRole('link', { name: 'Chicago, IL → Dallas, TX' })).toHaveAttribute(
      'href',
      `/loads/${LOAD_ID}`,
    )
  })

  it('names the driver by id when the driver record is gone', async () => {
    mockGet.mockResolvedValue(page([{ ...DISPATCHED, driverName: null }]))
    renderRoute(<ActivityTimeline />)

    expect(await screen.findByText('Dispatched to Driver 33333333')).toBeInTheDocument()
  })

  it('shows an empty state instead of a blank panel', async () => {
    mockGet.mockResolvedValue(page([]))
    renderRoute(<ActivityTimeline />)

    expect(await screen.findByText('No activity yet')).toBeInTheDocument()
  })

  it('surfaces a failed request', async () => {
    mockGet.mockRejectedValue(new ApiRequestError(500, 'ERROR', 'boom'))
    renderRoute(<ActivityTimeline />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})

/** The select renders before its loads arrive; selecting an option has to wait for them. */
async function loadFilterWithOptions() {
  const select = await screen.findByLabelText('Filter by load')
  await within(select).findByRole('option', { name: 'Chicago, IL → Dallas, TX' })
  return select
}

describe('ActivityPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists the whole feed when nothing is filtered', async () => {
    stubPage()
    renderRoute(<ActivityPage />, { route: '/timeline' })

    expect(await screen.findByText('Alice Rivera accepted')).toBeInTheDocument()
    expect(mockGet).toHaveBeenCalledWith(expect.stringMatching(/^\/activity\?page=0&size=20$/))
  })

  it('takes its filters from the URL, so a refresh lands on the same view', async () => {
    stubPage()
    renderRoute(<ActivityPage />, { route: `/timeline?loadId=${LOAD_ID}&jobId=${JOB_ID}` })

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(
        expect.stringContaining(`/activity?loadId=${LOAD_ID}&jobId=${JOB_ID}`),
      ),
    )
    expect(await screen.findByLabelText('Filter by load')).toHaveValue(LOAD_ID)
    expect(screen.getByLabelText('Filter by job')).toHaveValue(JOB_ID)
  })

  it('scopes the feed to the load the dispatcher picks', async () => {
    const user = userEvent.setup()
    stubPage()
    renderRoute(<ActivityPage />, { route: '/timeline' })

    await user.selectOptions(await loadFilterWithOptions(), LOAD_ID)

    await waitFor(() =>
      expect(mockGet).toHaveBeenCalledWith(expect.stringContaining(`/activity?loadId=${LOAD_ID}`)),
    )
  })

  it('offers legs only once a load narrows them down', async () => {
    const user = userEvent.setup()
    stubPage()
    renderRoute(<ActivityPage />, { route: '/timeline' })

    const jobFilter = await screen.findByLabelText('Filter by job')
    expect(jobFilter).toBeDisabled()
    expect(mockGet).not.toHaveBeenCalledWith(expect.stringContaining('/jobs'))

    await user.selectOptions(await loadFilterWithOptions(), LOAD_ID)

    await waitFor(() => expect(screen.getByLabelText('Filter by job')).toBeEnabled())
    expect(
      within(screen.getByLabelText('Filter by job')).getByRole('option', {
        name: 'Pickup · Leg 1',
      }),
    ).toBeInTheDocument()
  })

  it('clears a stale leg when the load changes under it', async () => {
    const user = userEvent.setup()
    stubPage()
    renderRoute(<ActivityPage />, { route: `/timeline?loadId=${LOAD_ID}&jobId=${JOB_ID}` })

    await waitFor(() => expect(screen.getByLabelText('Filter by job')).toHaveValue(JOB_ID))
    await user.selectOptions(screen.getByLabelText('Filter by load'), '')

    await waitFor(() => expect(screen.getByLabelText('Filter by job')).toHaveValue(''))
    expect(mockGet).toHaveBeenLastCalledWith(
      expect.stringMatching(/^\/activity\?page=0&size=20$/),
    )
  })

  it('says so when a filter matches nothing', async () => {
    stubPage([])
    renderRoute(<ActivityPage />, { route: `/timeline?loadId=${LOAD_ID}` })

    expect(await screen.findByText('No activity for this filter')).toBeInTheDocument()
  })
})
