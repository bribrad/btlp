import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import { useJobs } from '@/features/jobs/api'
import { useLoads } from '@/features/loads/api'
import { formatEnum } from '@/lib/format'
import { ActivityTimeline } from './ActivityTimeline'

const PAGE_SIZE = 20

/** One screen's worth of loads and legs to pick from; deeper history is reached from a record. */
const OPTIONS_SIZE = 100

/**
 * The operations feed. Filters live in the URL so a dispatcher can hand a colleague the link to
 * exactly the load they are asking about, and so a refresh lands on the same view.
 */
export function ActivityPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const loadId = searchParams.get('loadId') ?? ''
  const jobId = searchParams.get('jobId') ?? ''
  const page = Math.max(Number(searchParams.get('page') ?? '0') || 0, 0)
  const hasFilters = loadId !== '' || jobId !== ''

  const loads = useLoads({ size: OPTIONS_SIZE })
  // Legs are only worth listing once a load narrows them down; until then, don't fetch at all.
  const jobs = useJobs({ loadId, size: OPTIONS_SIZE, enabled: loadId !== '' })

  const updateParams = useCallback(
    (changes: Record<string, string>, resetPage = true) => {
      setSearchParams(
        previous => {
          const next = new URLSearchParams(previous)
          for (const [key, value] of Object.entries(changes)) {
            if (value === '') next.delete(key)
            else next.set(key, value)
          }
          if (resetPage) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title="Activity"
        description="Dispatch and status milestones across every load, newest first."
      />

      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label="Filter by load"
          value={loadId}
          // A leg belongs to one load, so changing the load invalidates any leg already chosen.
          onChange={event => updateParams({ loadId: event.target.value, jobId: '' })}
          className="h-9 max-w-xs rounded-md border border-input bg-transparent px-2 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option value="">All loads</option>
          {(loads.data?.content ?? []).map(load => (
            <option key={load.id} value={load.id}>
              {load.origin} → {load.destination}
            </option>
          ))}
        </select>

        <select
          aria-label="Filter by job"
          value={jobId}
          disabled={loadId === ''}
          onChange={event => updateParams({ jobId: event.target.value })}
          className="h-9 max-w-xs rounded-md border border-input bg-transparent px-2 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          <option value="">{loadId === '' ? 'Pick a load first' : 'All jobs on this load'}</option>
          {(loadId === '' ? [] : (jobs.data?.content ?? [])).map(job => (
            <option key={job.id} value={job.id}>
              {formatEnum(job.jobType)} · Leg {job.sequence}
            </option>
          ))}
        </select>

        {hasFilters && (
          <button
            type="button"
            onClick={() => updateParams({ loadId: '', jobId: '' })}
            className="h-9 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            Clear filters
          </button>
        )}
      </div>

      <div className="rounded-lg border bg-card p-4">
        <ActivityTimeline
          loadId={loadId || undefined}
          jobId={jobId || undefined}
          page={page}
          size={PAGE_SIZE}
          onPageChange={next => updateParams({ page: String(next) }, false)}
          emptyTitle={hasFilters ? 'No activity for this filter' : 'No activity yet'}
          emptyDescription={
            hasFilters
              ? 'Nothing has happened on this load or leg yet.'
              : 'Dispatch and status events appear here as they happen.'
          }
        />
      </div>
    </div>
  )
}
