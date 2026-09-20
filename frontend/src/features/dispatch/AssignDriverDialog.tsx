import { useState } from 'react'
import { Modal } from '@/components/Modal'
import { controlClass } from '@/components/form/controlStyles'
import { errorMessage } from '@/lib/errors'
import { formatEnum } from '@/lib/format'
import type { DispatchBoardEntry } from '@/types'
import { useDispatchDriver, useEligibleDrivers, useReassignAssignment } from './api'

interface AssignDriverDialogProps {
  entry: DispatchBoardEntry
  onClose: () => void
}

/**
 * Picks a driver for a job. With an active assignment the action is a reassignment — the backend
 * cancels the current one and dispatches the replacement in a single audited step.
 */
export function AssignDriverDialog({ entry, onClose }: AssignDriverDialogProps) {
  const isReassign = entry.assignmentId !== null
  const [driverId, setDriverId] = useState('')

  const drivers = useEligibleDrivers()
  const dispatchDriver = useDispatchDriver()
  const reassign = useReassignAssignment()
  const mutation = isReassign ? reassign : dispatchDriver

  const available = drivers.data?.content ?? []
  const jobLabel = `${formatEnum(entry.jobType)} · Leg ${entry.sequence}`

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (driverId === '') return
    if (isReassign && entry.assignmentId) {
      reassign.mutate({ assignmentId: entry.assignmentId, driverId }, { onSuccess: onClose })
    } else {
      dispatchDriver.mutate({ jobId: entry.jobId, driverId }, { onSuccess: onClose })
    }
  }

  return (
    <Modal
      title={isReassign ? 'Reassign job' : 'Assign driver'}
      description={`${jobLabel} — ${entry.origin} → ${entry.destination}`}
      onClose={onClose}
    >
      <form onSubmit={submit} className="space-y-4">
        {isReassign && (
          <p className="rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
            Currently with {entry.driverName}. Reassigning cancels that assignment.
          </p>
        )}

        <div className="space-y-1.5">
          <label htmlFor="driverId" className="text-sm font-medium">
            Driver
          </label>
          <select
            id="driverId"
            name="driverId"
            value={driverId}
            onChange={event => setDriverId(event.target.value)}
            disabled={drivers.isPending || available.length === 0}
            className={controlClass}
          >
            <option value="">Select an available driver</option>
            {available.map(driver => (
              <option key={driver.id} value={driver.id}>
                {driver.name} · {driver.phone}
              </option>
            ))}
          </select>
          {drivers.isError && (
            <p className="text-xs text-destructive">{errorMessage(drivers.error)}</p>
          )}
          {!drivers.isPending && !drivers.isError && available.length === 0 && (
            <p className="text-xs text-muted-foreground">
              No drivers are available right now. Free one up or mark a driver available first.
            </p>
          )}
        </div>

        {mutation.isError && (
          <p role="alert" className="text-sm text-destructive">
            {errorMessage(mutation.error)}
          </p>
        )}

        <div className="flex items-center gap-2">
          <button
            type="submit"
            disabled={driverId === '' || mutation.isPending}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
          >
            {mutation.isPending
              ? isReassign
                ? 'Reassigning…'
                : 'Dispatching…'
              : isReassign
                ? 'Reassign'
                : 'Dispatch'}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="inline-flex items-center justify-center rounded-md border bg-background px-4 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  )
}
