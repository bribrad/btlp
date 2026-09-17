import { Link, useParams } from 'react-router-dom'
import { DetailCard, DetailField, DetailList } from '@/components/DetailCard'
import { PageHeader } from '@/components/PageHeader'
import { DetailSkeleton, EmptyState, ErrorState } from '@/components/QueryStates'
import { StatusBadge } from '@/components/StatusBadge'
import { useJobs } from '@/features/jobs/api'
import {
  formatCurrency,
  formatDateTime,
  formatEnum,
  formatText,
  formatWindow,
} from '@/lib/format'
import { useLoad } from './api'

export function LoadDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: load, isPending, isError, error, refetch } = useLoad(id)

  if (isPending) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Load" backTo={{ to: '/loads', label: 'Back to loads' }} />
        <DetailSkeleton />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Load" backTo={{ to: '/loads', label: 'Back to loads' }} />
        <ErrorState error={error} onRetry={() => refetch()} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title={`${load.origin} → ${load.destination}`}
        description={`Load ${load.id}`}
        backTo={{ to: '/loads', label: 'Back to loads' }}
        actions={<StatusBadge status={load.status} className="mt-1" />}
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <DetailCard title="Schedule">
          <DetailList>
            <DetailField label="Pickup window">
              {formatWindow(load.pickupWindowStart, load.pickupWindowEnd)}
            </DetailField>
            <DetailField label="Dropoff window">
              {formatWindow(load.dropoffWindowStart, load.dropoffWindowEnd)}
            </DetailField>
          </DetailList>
        </DetailCard>

        <DetailCard title="Commercial">
          <DetailList>
            <DetailField label="Customer">{formatText(load.customerId)}</DetailField>
            <DetailField label="Rate">
              {formatCurrency(load.rateAmount, load.rateCurrency)}
            </DetailField>
            <DetailField label="Notes" wide>
              <span className="whitespace-pre-wrap">{formatText(load.notes)}</span>
            </DetailField>
          </DetailList>
        </DetailCard>

        <DetailCard title="Record" className="lg:col-span-2">
          <DetailList>
            <DetailField label="Load ID">
              <span className="font-mono text-xs">{load.id}</span>
            </DetailField>
            <DetailField label="Status">
              <StatusBadge status={load.status} />
            </DetailField>
            <DetailField label="Created">
              {formatDateTime(load.createdAt)} by {formatText(load.createdBy)}
            </DetailField>
            <DetailField label="Last updated">
              {formatDateTime(load.updatedAt)} by {formatText(load.updatedBy)}
            </DetailField>
          </DetailList>
        </DetailCard>
      </div>

      <LoadJobs loadId={load.id} />
    </div>
  )
}

/** Jobs belonging to this load, in sequence order — the legs that make up the trip. */
function LoadJobs({ loadId }: { loadId: string }) {
  const { data, isPending, isError, error, refetch } = useJobs({ loadId, size: 100 })
  const jobs = data?.content ?? []

  return (
    <DetailCard title="Jobs">
      {isPending ? (
        <DetailSkeleton />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : jobs.length === 0 ? (
        <EmptyState
          title="No jobs on this load"
          description="Pickup and dropoff jobs appear here once they are created."
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th scope="col" className="px-2 py-2 font-medium">
                  #
                </th>
                <th scope="col" className="px-2 py-2 font-medium">
                  Type
                </th>
                <th scope="col" className="px-2 py-2 font-medium">
                  Scheduled
                </th>
                <th scope="col" className="px-2 py-2 font-medium">
                  Status
                </th>
              </tr>
            </thead>
            <tbody>
              {jobs.map(job => (
                <tr key={job.id} className="border-b last:border-0">
                  <td className="px-2 py-2.5 tabular-nums">{job.sequence}</td>
                  <td className="px-2 py-2.5">
                    <Link to={`/jobs/${job.id}`} className="font-medium hover:underline">
                      {formatEnum(job.jobType)}
                    </Link>
                  </td>
                  <td className="px-2 py-2.5 text-muted-foreground">
                    {formatDateTime(job.scheduledAt)}
                  </td>
                  <td className="px-2 py-2.5">
                    <StatusBadge status={job.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </DetailCard>
  )
}
