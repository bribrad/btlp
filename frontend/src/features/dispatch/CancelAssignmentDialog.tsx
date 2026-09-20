import { Modal } from '@/components/Modal'
import { errorMessage } from '@/lib/errors'
import { formatEnum } from '@/lib/format'
import type { DispatchBoardEntry } from '@/types'
import { useCancelAssignment } from './api'

interface CancelAssignmentDialogProps {
  entry: DispatchBoardEntry
  onClose: () => void
}

/** Confirms pulling a job back from its driver, returning it to the needs-dispatch lane. */
export function CancelAssignmentDialog({ entry, onClose }: CancelAssignmentDialogProps) {
  const cancelAssignment = useCancelAssignment()
  const jobLabel = `${formatEnum(entry.jobType)} · Leg ${entry.sequence}`

  return (
    <Modal
      title="Cancel assignment"
      description={`${jobLabel} — ${entry.origin} → ${entry.destination}`}
      onClose={onClose}
    >
      <div className="space-y-4">
        <p className="text-sm">
          {entry.driverName} will be released and the job returns to the needs-dispatch lane.
        </p>

        {cancelAssignment.isError && (
          <p role="alert" className="text-sm text-destructive">
            {errorMessage(cancelAssignment.error)}
          </p>
        )}

        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={cancelAssignment.isPending || entry.assignmentId === null}
            onClick={() => {
              if (entry.assignmentId) {
                cancelAssignment.mutate(entry.assignmentId, { onSuccess: onClose })
              }
            }}
            className="inline-flex items-center justify-center rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground shadow transition-colors hover:bg-destructive/90 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
          >
            {cancelAssignment.isPending ? 'Canceling…' : 'Cancel assignment'}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex items-center justify-center rounded-md border bg-background px-4 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            Keep assignment
          </button>
        </div>
      </div>
    </Modal>
  )
}
