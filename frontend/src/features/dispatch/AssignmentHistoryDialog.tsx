import { Modal } from '@/components/Modal'
import { EmptyState, ErrorState } from '@/components/QueryStates'
import { StatusBadge } from '@/components/StatusBadge'
import { formatDateTime, formatEnum, shortId } from '@/lib/format'
import type { Assignment, DispatchBoardEntry } from '@/types'
import { useAssignmentHistory, useDriverDirectory } from './api'

interface AssignmentHistoryDialogProps {
  entry: DispatchBoardEntry
  onClose: () => void
}

/**
 * Every assignment this job has had, newest first. A reassignment leaves the canceled assignment
 * in place, so the trail — who dispatched whom, who answered, and who pulled it back — is visible
 * here rather than only in the audit log.
 */
export function AssignmentHistoryDialog({ entry, onClose }: AssignmentHistoryDialogProps) {
  const history = useAssignmentHistory(entry.jobId)
  const directory = useDriverDirectory()

  const driverNames = new Map(
    (directory.data?.content ?? []).map(driver => [driver.id, driver.name]),
  )
  const assignments = history.data?.content ?? []
  const jobLabel = `${formatEnum(entry.jobType)} · Leg ${entry.sequence}`

  return (
    <Modal
      title="Assignment history"
      description={`${jobLabel} — ${entry.origin} → ${entry.destination}`}
      onClose={onClose}
    >
      {history.isError ? (
        <ErrorState error={history.error} onRetry={() => history.refetch()} />
      ) : history.isPending ? (
        <p className="text-sm text-muted-foreground">Loading history…</p>
      ) : assignments.length === 0 ? (
        <EmptyState
          title="Not dispatched yet"
          description="This job has never been sent to a driver."
        />
      ) : (
        <ol className="space-y-3">
          {assignments.map(assignment => (
            <li key={assignment.id} className="rounded-md border p-3">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">
                  {driverNames.get(assignment.driverId) ?? `Driver ${shortId(assignment.driverId)}`}
                </p>
                <StatusBadge status={assignment.state} />
              </div>
              <dl className="mt-2 space-y-1 text-xs text-muted-foreground">
                <div className="flex gap-1">
                  <dt>Dispatched</dt>
                  <dd>
                    {formatDateTime(assignment.assignedAt)}
                    {assignment.createdBy && ` by ${assignment.createdBy}`}
                  </dd>
                </div>
                {assignment.acceptedAt && (
                  <div className="flex gap-1">
                    <dt>Accepted</dt>
                    <dd>{formatDateTime(assignment.acceptedAt)}</dd>
                  </div>
                )}
                <div className="flex gap-1">
                  <dt>{outcomeLabel(assignment)}</dt>
                  <dd>
                    {formatDateTime(assignment.updatedAt)}
                    {assignment.updatedBy && ` by ${assignment.updatedBy}`}
                  </dd>
                </div>
              </dl>
            </li>
          ))}
        </ol>
      )}
    </Modal>
  )
}

/** Labels the last transition so the row reads as a sentence rather than a bare timestamp. */
function outcomeLabel(assignment: Assignment): string {
  switch (assignment.state) {
    case 'PENDING':
      return 'Awaiting answer since'
    case 'ACCEPTED':
      return 'In progress since'
    default:
      return `${formatEnum(assignment.state)}`
  }
}
