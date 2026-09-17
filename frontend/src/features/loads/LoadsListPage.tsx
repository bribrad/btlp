import { useCallback } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { ButtonLink } from '@/components/ButtonLink'
import { FilterSelect } from '@/components/FilterSelect'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { EmptyState, ErrorState, TableSkeleton } from '@/components/QueryStates'
import { SearchInput } from '@/components/SearchInput'
import { StatusBadge } from '@/components/StatusBadge'
import { formatCurrency, formatText, formatWindow } from '@/lib/format'
import { LOAD_STATUSES, type LoadStatus } from '@/types'
import { useLoads } from './api'

const PAGE_SIZE = 20

const COLUMNS = ['Route', 'Customer', 'Pickup window', 'Dropoff window', 'Rate', 'Status']

export function LoadsListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') ?? '') as LoadStatus | ''
  const page = Math.max(Number(searchParams.get('page') ?? '0') || 0, 0)
  const hasFilters = q !== '' || status !== ''

  const { data, isPending, isError, error, isFetching, refetch } = useLoads({
    q,
    status,
    page,
    size: PAGE_SIZE,
  })

  // Filter changes reset to the first page; otherwise a narrow result set lands on a blank page.
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

  const loads = data?.content ?? []

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title="Loads"
        description="Every load in the system, newest first."
        actions={
          <ButtonLink to="/loads/new">
            <Plus className="h-4 w-4" />
            New load
          </ButtonLink>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <SearchInput
          label="Search loads"
          placeholder="Origin, destination, or customer"
          value={q}
          onChange={value => updateParams({ q: value })}
        />
        <FilterSelect
          label="Filter by status"
          value={status}
          options={LOAD_STATUSES}
          allLabel="All statuses"
          onChange={value => updateParams({ status: value })}
        />
        {hasFilters && (
          <button
            type="button"
            onClick={() => updateParams({ q: '', status: '' })}
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
                  {loads.map(load => (
                    <tr
                      key={load.id}
                      onClick={() => navigate(`/loads/${load.id}`)}
                      className="cursor-pointer border-b transition-colors last:border-0 hover:bg-accent/50"
                    >
                      <td className="px-4 py-3">
                        <Link
                          to={`/loads/${load.id}`}
                          onClick={event => event.stopPropagation()}
                          className="font-medium hover:underline"
                        >
                          {load.origin} → {load.destination}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatText(load.customerId)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatWindow(load.pickupWindowStart, load.pickupWindowEnd)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatWindow(load.dropoffWindowStart, load.dropoffWindowEnd)}
                      </td>
                      <td className="px-4 py-3 tabular-nums">
                        {formatCurrency(load.rateAmount, load.rateCurrency)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={load.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              )}
            </table>
          </div>

          {!isPending && loads.length === 0 && (
            <EmptyState
              className="border-0"
              title={hasFilters ? 'No loads match these filters' : 'No loads yet'}
              description={
                hasFilters
                  ? 'Try a different search term or clear the status filter.'
                  : 'Loads appear here once they are created.'
              }
              action={
                hasFilters ? (
                  <button
                    type="button"
                    onClick={() => updateParams({ q: '', status: '' })}
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
