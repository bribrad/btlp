import { Link } from 'react-router-dom'
import { Pagination } from '@/components/Pagination'
import { DetailSkeleton, EmptyState, ErrorState } from '@/components/QueryStates'
import { formatDate, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ActivityEvent } from '@/types'
import { useActivity } from './api'
import { actionStyle, contextLabels, summarize } from './eventCopy'

interface ActivityTimelineProps {
  /** Scopes the feed to one load, including its jobs and their assignments. */
  loadId?: string
  /** Scopes the feed to a single leg. Combined with `loadId` the two intersect. */
  jobId?: string
  page?: number
  size?: number
  onPageChange?: (page: number) => void
  emptyTitle?: string
  emptyDescription?: string
  /** Rows link out to the load and job they concern; off inside that record's own page. */
  linkToContext?: boolean
}

/**
 * Dispatch and status milestones, newest first, grouped under the day they happened.
 *
 * <p>Ordering comes from the backend's insertion sequence rather than the timestamp: the several
 * events one dispatch action writes share a transaction timestamp, so sorting on time alone would
 * let them swap places between refreshes.
 */
export function ActivityTimeline({
  loadId,
  jobId,
  page = 0,
  size = 20,
  onPageChange,
  emptyTitle = 'No activity yet',
  emptyDescription = 'Dispatch and status events appear here as they happen.',
  linkToContext = true,
}: ActivityTimelineProps) {
  const { data, isPending, isError, error, isFetching, refetch } = useActivity({
    loadId,
    jobId,
    page,
    size,
  })

  if (isPending) return <DetailSkeleton />
  if (isError) return <ErrorState error={error} onRetry={() => refetch()} />

  const events = data.content
  if (events.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />
  }

  return (
    <div className="space-y-6">
      {groupByDay(events).map(([day, dayEvents]) => (
        <section key={day} className="space-y-3">
          <h3 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {formatDate(day)}
          </h3>
          <ol className="space-y-1">
            {dayEvents.map(event => (
              <TimelineRow
                key={event.id}
                event={event}
                scope={{ loadId, jobId }}
                linkToContext={linkToContext}
              />
            ))}
          </ol>
        </section>
      ))}

      {onPageChange && data.totalPages > 1 && (
        <Pagination
          page={data.page}
          size={data.size}
          totalElements={data.totalElements}
          totalPages={data.totalPages}
          isFetching={isFetching}
          onPageChange={onPageChange}
        />
      )}
    </div>
  )
}

interface TimelineRowProps {
  event: ActivityEvent
  scope: { loadId?: string; jobId?: string }
  linkToContext: boolean
}

function TimelineRow({ event, scope, linkToContext }: TimelineRowProps) {
  const { icon: Icon, tone } = actionStyle(event.action)
  const labels = contextLabels(event, scope)
  const target = linkToContext ? contextTarget(event) : null

  return (
    <li className="group flex gap-3">
      {/* Icon column doubles as the rail: the connector runs down to the next row in the day. */}
      <div className="flex flex-col items-center">
        <span className={cn('flex h-7 w-7 items-center justify-center rounded-full', tone)}>
          <Icon className="h-3.5 w-3.5" aria-hidden="true" />
        </span>
        <span className="w-px flex-1 bg-border group-last:hidden" />
      </div>

      <div className="flex-1 pb-4">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3">
          <p className="text-sm font-medium">{summarize(event)}</p>
          <time dateTime={event.occurredAt} className="text-xs text-muted-foreground">
            {formatDateTime(event.occurredAt)}
          </time>
        </div>
        <p className="text-xs text-muted-foreground">
          by {event.actor}
          {labels.length > 0 && ' · '}
          {target ? (
            <Link to={target} className="hover:underline">
              {labels.join(' · ')}
            </Link>
          ) : (
            labels.join(' · ')
          )}
        </p>
      </div>
    </li>
  )
}

/** Deepest record the event concerns, so the row links where a dispatcher would want to go. */
function contextTarget(event: ActivityEvent): string | null {
  if (event.jobId) return `/jobs/${event.jobId}`
  if (event.loadId) return `/loads/${event.loadId}`
  return null
}

/**
 * Splits the page into day buckets, preserving the order the API returned. Buckets follow the
 * viewer's local day, not UTC, so a heading never disagrees with the times listed under it. Each
 * is keyed by the timestamp of its first event, which is what the heading renders.
 */
function groupByDay(events: ActivityEvent[]): [string, ActivityEvent[]][] {
  const groups: [string, ActivityEvent[]][] = []
  let currentDay: string | null = null
  for (const event of events) {
    const day = new Date(event.occurredAt).toDateString()
    if (day !== currentDay) {
      groups.push([event.occurredAt, []])
      currentDay = day
    }
    groups[groups.length - 1][1].push(event)
  }
  return groups
}
