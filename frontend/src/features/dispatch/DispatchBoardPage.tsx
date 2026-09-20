import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { ErrorState } from '@/components/QueryStates'
import { StatusBadge } from '@/components/StatusBadge'
import { formatDateTime, formatEnum, formatTimeUntil } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { DispatchBoardEntry, DispatchBoardLane } from '@/types'
import { AssignDriverDialog } from './AssignDriverDialog'
import { AssignmentHistoryDialog } from './AssignmentHistoryDialog'
import { CancelAssignmentDialog } from './CancelAssignmentDialog'
import { dispatchKeys, useBoardLane } from './api'

interface LaneConfig {
  lane: DispatchBoardLane
  title: string
  description: string
}

const LANES: LaneConfig[] = [
  {
    lane: 'NEEDS_DISPATCH',
    title: 'Needs dispatch',
    description: 'No driver holds these yet.',
  },
  {
    lane: 'PENDING_ACCEPTANCE',
    title: 'Awaiting acceptance',
    description: 'Sent to a driver, waiting on their answer.',
  },
  {
    lane: 'IN_PROGRESS',
    title: 'In progress',
    description: 'Accepted and under way.',
  },
]

type DialogState = { kind: 'assign' | 'cancel' | 'history'; entry: DispatchBoardEntry } | null

export function DispatchBoardPage() {
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<DialogState>(null)

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title="Dispatch Board"
        description="Assign jobs to drivers, move them when plans change, and watch acceptances land."
        actions={
          <button
            type="button"
            onClick={() => queryClient.invalidateQueries({ queryKey: dispatchKeys.all })}
            className="inline-flex items-center gap-2 rounded-md border bg-background px-3 py-1.5 text-sm font-medium shadow-sm transition-colors hover:bg-accent"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Refresh
          </button>
        }
      />

      <div className="grid items-start gap-4 lg:grid-cols-3">
        {LANES.map(config => (
          <LaneColumn key={config.lane} config={config} onAction={setDialog} />
        ))}
      </div>

      {dialog?.kind === 'assign' && (
        <AssignDriverDialog entry={dialog.entry} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'cancel' && (
        <CancelAssignmentDialog entry={dialog.entry} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'history' && (
        <AssignmentHistoryDialog entry={dialog.entry} onClose={() => setDialog(null)} />
      )}
    </div>
  )
}

function LaneColumn({
  config,
  onAction,
}: {
  config: LaneConfig
  onAction: (dialog: DialogState) => void
}) {
  const { data, isPending, isError, error, isFetching, refetch } = useBoardLane(config.lane)
  const entries = data?.content ?? []

  return (
    <section aria-label={config.title} className="rounded-lg border bg-card">
      <div className="space-y-1 border-b px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-medium">{config.title}</h2>
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums text-muted-foreground">
            {isPending ? '–' : data?.totalElements ?? 0}
          </span>
        </div>
        <p className="text-xs text-muted-foreground">
          {config.description}
          {isFetching && !isPending && <span className="ml-2">Updating…</span>}
        </p>
      </div>

      <div className="space-y-3 p-3">
        {isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : isPending ? (
          <CardSkeleton />
        ) : entries.length === 0 ? (
          <p className="px-1 py-6 text-center text-sm text-muted-foreground">Nothing here.</p>
        ) : (
          entries.map(entry => <BoardCard key={entry.jobId} entry={entry} onAction={onAction} />)
        )}
      </div>
    </section>
  )
}

function BoardCard({
  entry,
  onAction,
}: {
  entry: DispatchBoardEntry
  onAction: (dialog: DialogState) => void
}) {
  const now = useMinuteTick()
  const isPendingAcceptance = entry.assignmentState === 'PENDING'
  const isOverdue = isPendingAcceptance && formatTimeUntil(entry.expiresAt, now) === 'Overdue'

  return (
    <article className="space-y-3 rounded-md border bg-background p-3">
      <div className="flex items-start justify-between gap-2">
        <Link to={`/jobs/${entry.jobId}`} className="text-sm font-medium hover:underline">
          {formatEnum(entry.jobType)} · Leg {entry.sequence}
        </Link>
        <StatusBadge status={entry.assignmentState ?? entry.jobStatus} />
      </div>

      <div className="space-y-1 text-xs text-muted-foreground">
        <Link to={`/loads/${entry.loadId}`} className="block hover:underline">
          {entry.origin} → {entry.destination}
        </Link>
        <p>Scheduled {formatDateTime(entry.scheduledAt)}</p>
        {entry.driverName && (
          <p className="text-foreground">
            {entry.driverName}
            {entry.assignedBy && (
              <span className="text-muted-foreground"> · dispatched by {entry.assignedBy}</span>
            )}
          </p>
        )}
        {isPendingAcceptance && (
          <p className={cn(isOverdue && 'font-medium text-destructive')}>
            {isOverdue ? 'Acceptance window elapsed' : formatTimeUntil(entry.expiresAt, now)}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {entry.assignmentId === null ? (
          <button
            type="button"
            onClick={() => onAction({ kind: 'assign', entry })}
            className="rounded-md bg-primary px-2.5 py-1.5 text-xs font-medium text-primary-foreground shadow transition-colors hover:bg-primary/90"
          >
            Assign driver
          </button>
        ) : (
          <>
            <button
              type="button"
              onClick={() => onAction({ kind: 'assign', entry })}
              className="rounded-md border bg-background px-2.5 py-1.5 text-xs font-medium shadow-sm transition-colors hover:bg-accent"
            >
              Reassign
            </button>
            <button
              type="button"
              onClick={() => onAction({ kind: 'cancel', entry })}
              className="rounded-md border bg-background px-2.5 py-1.5 text-xs font-medium text-destructive shadow-sm transition-colors hover:bg-destructive/10"
            >
              Cancel
            </button>
          </>
        )}
        <button
          type="button"
          onClick={() => onAction({ kind: 'history', entry })}
          className="ml-auto text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          History
        </button>
      </div>
    </article>
  )
}

function CardSkeleton() {
  return (
    <div className="space-y-3" aria-hidden="true">
      {Array.from({ length: 3 }).map((_, index) => (
        <div key={index} className="h-24 animate-pulse rounded-md bg-muted" />
      ))}
    </div>
  )
}

/**
 * Re-renders once a minute so acceptance countdowns stay honest without polling the API — the
 * deadline is already known client-side, only the clock moves.
 */
function useMinuteTick(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000)
    return () => clearInterval(timer)
  }, [])
  return now
}
