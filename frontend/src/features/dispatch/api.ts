import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import { buildListQuery } from '@/features/loads/api'
import { jobKeys } from '@/features/jobs/api'
import type {
  Assignment,
  DispatchBoardEntry,
  DispatchBoardLane,
  Driver,
  PagedResponse,
} from '@/types'

/** A lane holds one screen's worth of work; deeper queues are handled from the jobs list. */
const LANE_SIZE = 50
const DIRECTORY_SIZE = 100

export const dispatchKeys = {
  all: ['dispatch'] as const,
  board: (lane: DispatchBoardLane) => [...dispatchKeys.all, 'board', lane] as const,
  history: (jobId: string) => [...dispatchKeys.all, 'history', jobId] as const,
  eligibleDrivers: () => [...dispatchKeys.all, 'drivers', 'eligible'] as const,
  driverDirectory: () => [...dispatchKeys.all, 'drivers', 'directory'] as const,
}

/** One board column. Each lane is its own query so a column refreshes without the others. */
export function useBoardLane(lane: DispatchBoardLane) {
  return useQuery({
    queryKey: dispatchKeys.board(lane),
    queryFn: () =>
      api.get<PagedResponse<DispatchBoardEntry>>(
        `/dispatch/board${buildListQuery({ lane, size: LANE_SIZE })}`,
      ),
    placeholderData: keepPreviousData,
  })
}

/** Every assignment a job has had, newest first — the visible trail of a reassignment. */
export function useAssignmentHistory(jobId: string | undefined) {
  return useQuery({
    queryKey: dispatchKeys.history(jobId ?? ''),
    queryFn: () =>
      api.get<PagedResponse<Assignment>>(
        `/dispatch/assignments${buildListQuery({ jobId, size: LANE_SIZE })}`,
      ),
    enabled: Boolean(jobId),
  })
}

/** Drivers who can take a new job: active and not already on a trip. */
export function useEligibleDrivers(enabled = true) {
  return useQuery({
    queryKey: dispatchKeys.eligibleDrivers(),
    queryFn: () =>
      api.get<PagedResponse<Driver>>(
        `/drivers/eligible${buildListQuery({ size: DIRECTORY_SIZE })}`,
      ),
    enabled,
  })
}

/** The full directory, used to name the drivers on past assignments. */
export function useDriverDirectory(enabled = true) {
  return useQuery({
    queryKey: dispatchKeys.driverDirectory(),
    queryFn: () =>
      api.get<PagedResponse<Driver>>(`/drivers${buildListQuery({ size: DIRECTORY_SIZE })}`),
    enabled,
  })
}

export interface DispatchVariables {
  jobId: string
  driverId: string
}

export function useDispatchDriver() {
  const invalidateBoard = useBoardInvalidation()
  return useMutation({
    mutationFn: ({ jobId, driverId }: DispatchVariables) =>
      api.post<Assignment>('/dispatch/assignments', { jobId, driverId }, idempotencyHeader()),
    onSuccess: invalidateBoard,
  })
}

export interface ReassignVariables {
  assignmentId: string
  driverId: string
}

export function useReassignAssignment() {
  const invalidateBoard = useBoardInvalidation()
  return useMutation({
    mutationFn: ({ assignmentId, driverId }: ReassignVariables) =>
      api.post<Assignment>(
        `/dispatch/assignments/${assignmentId}/reassign`,
        { driverId },
        idempotencyHeader(),
      ),
    onSuccess: invalidateBoard,
  })
}

export function useCancelAssignment() {
  const invalidateBoard = useBoardInvalidation()
  return useMutation({
    mutationFn: (assignmentId: string) =>
      api.post<Assignment>(
        `/dispatch/assignments/${assignmentId}/cancel`,
        undefined,
        idempotencyHeader(),
      ),
    onSuccess: invalidateBoard,
  })
}

/**
 * Every dispatch action moves a job between lanes and can change its status, so both the board
 * and the cached job lists are stale afterwards.
 */
function useBoardInvalidation() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: dispatchKeys.all })
    queryClient.invalidateQueries({ queryKey: jobKeys.all })
  }
}

/**
 * A unique key per action. The backend stores the outcome against it, so a request replayed at
 * the transport level returns the original response instead of dispatching a second time.
 */
function idempotencyHeader(): Record<string, string> {
  return { 'Idempotency-Key': crypto.randomUUID() }
}
