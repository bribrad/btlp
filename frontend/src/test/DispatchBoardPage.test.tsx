import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DispatchBoardPage } from '@/features/dispatch/DispatchBoardPage'
import {
  makeAssignment,
  makeBoardEntry,
  makeDriver,
  page,
  renderRoute,
} from './utils'

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }
})

import { api, ApiRequestError } from '@/api/client'
const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const JOB_ID = '22222222-2222-2222-2222-222222222222'
const ASSIGNMENT_ID = '44444444-4444-4444-4444-444444444444'
const ALICE_ID = '33333333-3333-3333-3333-333333333333'
const BOB_ID = '55555555-5555-5555-5555-555555555555'

const UNASSIGNED = makeBoardEntry()

const PENDING = makeBoardEntry({
  jobId: '66666666-6666-6666-6666-666666666666',
  jobType: 'DROPOFF',
  sequence: 2,
  lane: 'PENDING_ACCEPTANCE',
  assignmentId: ASSIGNMENT_ID,
  assignmentState: 'PENDING',
  assignedAt: '2026-03-03T14:00:00Z',
  // Far future, so the countdown is not "Overdue" unless a test says so.
  expiresAt: '2099-01-01T00:00:00Z',
  assignedBy: 'dispatcher',
  driverId: ALICE_ID,
  driverName: 'Alice Rivera',
  driverPhone: '555-0100',
})

/** Routes each board/directory request to its fixture; lanes not named here come back empty. */
function stubBoard({
  needsDispatch = [UNASSIGNED],
  pending = [PENDING],
  inProgress = [],
  drivers = [makeDriver(), makeDriver({ id: BOB_ID, name: 'Bob Chen', phone: '555-0200' })],
  history = [makeAssignment()],
}: {
  needsDispatch?: ReturnType<typeof makeBoardEntry>[]
  pending?: ReturnType<typeof makeBoardEntry>[]
  inProgress?: ReturnType<typeof makeBoardEntry>[]
  drivers?: ReturnType<typeof makeDriver>[]
  history?: ReturnType<typeof makeAssignment>[]
} = {}) {
  mockGet.mockImplementation(async (path: string) => {
    if (path.includes('lane=NEEDS_DISPATCH')) return page(needsDispatch) as never
    if (path.includes('lane=PENDING_ACCEPTANCE')) return page(pending) as never
    if (path.includes('lane=IN_PROGRESS')) return page(inProgress) as never
    if (path.startsWith('/drivers')) return page(drivers) as never
    if (path.startsWith('/dispatch/assignments')) return page(history) as never
    throw new Error(`unexpected GET ${path}`)
  })
}

function lane(title: string) {
  return within(screen.getByRole('region', { name: title }))
}

describe('DispatchBoardPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lays the queue out in lanes with a count per lane', async () => {
    stubBoard({ inProgress: [] })
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    expect(await lane('Needs dispatch').findByText('Pickup · Leg 1')).toBeInTheDocument()
    expect(lane('Needs dispatch').getByText('Chicago, IL → Dallas, TX')).toBeInTheDocument()
    expect(lane('Needs dispatch').getByText('1')).toBeInTheDocument()

    const awaiting = lane('Awaiting acceptance')
    expect(awaiting.getByText('Dropoff · Leg 2')).toBeInTheDocument()
    expect(awaiting.getByText(/Alice Rivera/)).toBeInTheDocument()
    expect(awaiting.getByText(/dispatched by dispatcher/)).toBeInTheDocument()

    expect(lane('In progress').getByText('Nothing here.')).toBeInTheDocument()
  })

  it('flags an acceptance window that has elapsed', async () => {
    stubBoard({ pending: [{ ...PENDING, expiresAt: '2020-01-01T00:00:00Z' }] })
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    expect(
      await lane('Awaiting acceptance').findByText('Acceptance window elapsed'),
    ).toBeInTheDocument()
  })

  it('dispatches an unassigned job to the chosen driver', async () => {
    const user = userEvent.setup()
    stubBoard()
    mockPost.mockResolvedValue(makeAssignment())
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Needs dispatch').findByRole('button', { name: 'Assign driver' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Assign driver' }))
    await user.selectOptions(await dialog.findByLabelText('Driver'), BOB_ID)
    await user.click(dialog.getByRole('button', { name: 'Dispatch' }))

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        '/dispatch/assignments',
        { jobId: JOB_ID, driverId: BOB_ID },
        expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
      ),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('reassigns a pending job to another driver', async () => {
    const user = userEvent.setup()
    stubBoard()
    mockPost.mockResolvedValue(makeAssignment({ driverId: BOB_ID }))
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Awaiting acceptance').findByRole('button', { name: 'Reassign' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Reassign job' }))
    expect(dialog.getByText(/Currently with Alice Rivera/)).toBeInTheDocument()
    await user.selectOptions(await dialog.findByLabelText('Driver'), BOB_ID)
    await user.click(dialog.getByRole('button', { name: 'Reassign' }))

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        `/dispatch/assignments/${ASSIGNMENT_ID}/reassign`,
        { driverId: BOB_ID },
        expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
      ),
    )
  })

  it('keeps the dialog open and explains a rejected reassignment', async () => {
    const user = userEvent.setup()
    stubBoard()
    mockPost.mockRejectedValue(
      new ApiRequestError(409, 'INVALID_STATE_TRANSITION', 'Assignment cannot be canceled.'),
    )
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Awaiting acceptance').findByRole('button', { name: 'Reassign' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Reassign job' }))
    await user.selectOptions(await dialog.findByLabelText('Driver'), BOB_ID)
    await user.click(dialog.getByRole('button', { name: 'Reassign' }))

    expect(await dialog.findByRole('alert')).toHaveTextContent('Assignment cannot be canceled.')
    expect(screen.getByRole('dialog', { name: 'Reassign job' })).toBeInTheDocument()
  })

  it('cancels an assignment after confirmation', async () => {
    const user = userEvent.setup()
    stubBoard()
    mockPost.mockResolvedValue(makeAssignment({ state: 'CANCELED' }))
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Awaiting acceptance').findByRole('button', { name: 'Cancel' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Cancel assignment' }))
    await user.click(dialog.getByRole('button', { name: 'Cancel assignment' }))

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        `/dispatch/assignments/${ASSIGNMENT_ID}/cancel`,
        undefined,
        expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
      ),
    )
  })

  it('shows who dispatched, answered, and pulled back each assignment', async () => {
    const user = userEvent.setup()
    stubBoard({
      history: [
        makeAssignment({ id: 'new-assignment', driverId: BOB_ID, state: 'PENDING' }),
        makeAssignment({
          state: 'CANCELED',
          driverId: ALICE_ID,
          updatedBy: 'dispatcher',
          updatedAt: '2026-03-03T14:05:00Z',
        }),
      ],
    })
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Awaiting acceptance').findByRole('button', { name: 'History' }))
    const dialog = within(await screen.findByRole('dialog', { name: 'Assignment history' }))

    expect(await dialog.findByText('Bob Chen')).toBeInTheDocument()
    expect(dialog.getByText('Alice Rivera')).toBeInTheDocument()
    // "Canceled" reads twice on that entry: once as the state badge, once as the outcome label.
    expect(dialog.getAllByText('Canceled')).toHaveLength(2)
    expect(dialog.getAllByText(/by dispatcher/).length).toBeGreaterThan(0)
    expect(mockGet).toHaveBeenCalledWith(
      expect.stringContaining(`/dispatch/assignments?jobId=${PENDING.jobId}`),
    )
  })

  it('surfaces a lane that failed to load without hiding the others', async () => {
    mockGet.mockImplementation(async (path: string) => {
      if (path.includes('lane=NEEDS_DISPATCH')) {
        throw new ApiRequestError(500, 'ERROR', 'boom')
      }
      return page([]) as never
    })
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    expect(await lane('Needs dispatch').findByRole('alert')).toBeInTheDocument()
    expect(lane('In progress').getByText('Nothing here.')).toBeInTheDocument()
  })

  it('offers no driver choice when nobody is available', async () => {
    const user = userEvent.setup()
    stubBoard({ drivers: [] })
    renderRoute(<DispatchBoardPage />, { route: '/dispatch' })

    await user.click(await lane('Needs dispatch').findByRole('button', { name: 'Assign driver' }))
    const dialog = within(screen.getByRole('dialog', { name: 'Assign driver' }))

    expect(await dialog.findByText(/No drivers are available right now/)).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Dispatch' })).toBeDisabled()
  })
})
