import { useCallback } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { ButtonLink } from '@/components/ButtonLink'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, TableSkeleton } from '@/components/QueryStates'
import { StatusBadge } from '@/components/StatusBadge'
import { formatDateTime, formatEnum, shortId } from '@/lib/format'
import { JOB_STATUSES, JOB_TYPES, type JobStatus, type JobType } from '@/types'
import { useJobs } from './api'

const PAGE_SIZE = 20

const COLUMNS = ['Load', 'Leg', 'Type', 'Scheduled', 'Status']

export function JobsListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  const loadId = searchParams.get('loadId') ?? ''
  const status = (searchParams.get('status') ?? '') as JobStatus | ''
  const jobType = (searchParams.get('jobType') ?? '') as JobType | ''
  const page = Math.max(Number(searchParams.get('page') ?? '0') || 0, 0)
  const hasFilters = loadId !== '' || status !== '' || jobType !== ''

  const { data, isPending, isError, error, isFetching, refetch } = useJobs({
    loadId,
    status,
    jobType,
    page,
    size: PAGE_SIZE,
  })

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

  const jobs = data?.content ?? []

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title="Jobs"
        description="Pickup and dropoff legs across all loads."
        actions={
          <ButtonLink to={loadId ? `/jobs/new?loadId=${loadId}` : '/jobs/new'}>
            <Plus className="h-4 w-4" />
            New job
          </ButtonLink>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <FilterSelect
          label="Filter by status"
          value={status}
          options={JOB_STATUSES}
          allLabel="All statuses"
          onChange={value => updateParams({ status: value })}
        />
        <FilterSelect
          label="Filter by type"
          value={jobType}
          options={JOB_TYPES}
          allLabel="All types"
          onChange={value => updateParams({ jobType: value })}
        />
        {loadId !== '' && (
          <span className="inline-flex items-center gap-2 rounded-full bg-muted px-3 py-1 text-xs">
            Load {shortId(loadId)}
            <button
              type="button"
              onClick={() => updateParams({ loadId: '' })}
              className="text-muted-foreground transition-colors hover:text-foreground"
            >
              Clear
            </button>
          </span>
        )}
        {hasFilters && (
          <button
            type="button"
            onClick={() => updateParams({ status: '', jobType: '', loadId: '' })}
            className="h-9 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            Clear filters
          </button>
        )}
      </div>

      {isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  {COLUMNS.map(column => (
                    <th key={column} scope="col" className="px-4 py-2.5 font-medium">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              {isPending ? (
                <TableSkeleton rows={6} columns={COLUMNS.length} />
              ) : (
                <tbody>
                  {jobs.map(job => (
                    <tr
                      key={job.id}
                      onClick={() => navigate(`/jobs/${job.id}`)}
                      className="cursor-pointer border-b transition-colors last:border-0 hover:bg-accent/50"
                    >
                      <td className="px-4 py-3">
                        <Link
                          to={`/loads/${job.loadId}`}
                          onClick={event => event.stopPropagation()}
                          className="font-mono text-xs hover:underline"
                        >
                          {shortId(job.loadId)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 tabular-nums">{job.sequence}</td>
                      <td className="px-4 py-3">
                        <Link
                          to={`/jobs/${job.id}`}
                          onClick={event => event.stopPropagation()}
                          className="font-medium hover:underline"
                        >
                          {formatEnum(job.jobType)}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDateTime(job.scheduledAt)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={job.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              )}
            </table>
          </div>

          {!isPending && jobs.length === 0 && (
            <EmptyState
              className="border-0"
              title={hasFilters ? 'No jobs match these filters' : 'No jobs yet'}
              description={
                hasFilters
                  ? 'Try a different status or type.'
                  : 'Jobs appear here once loads have pickup and dropoff legs.'
              }
              action={
                hasFilters ? (
                  <button
                    type="button"
                    onClick={() => updateParams({ status: '', jobType: '', loadId: '' })}
                    className="rounded-md border bg-background px-3 py-1.5 text-sm font-medium shadow-sm transition-colors hover:bg-accent"
                  >
                    Clear filters
                  </button>
                ) : undefined
              }
            />
          )}

          {data && data.totalElements > 0 && (
            <Pagination
              page={data.page}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              isFetching={isFetching}
              onPageChange={next => updateParams({ page: String(next) }, false)}
            />
          )}
        </div>
      )}
    </div>
  )
}
