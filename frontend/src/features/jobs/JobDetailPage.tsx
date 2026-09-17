import { Pencil } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ButtonLink } from '@/components/ButtonLink'
import { DetailCard, DetailField, DetailList } from '@/components/DetailCard'
import { PageHeader } from '@/components/PageHeader'
import { DetailSkeleton, ErrorState } from '@/components/QueryStates'
import { errorMessage } from '@/lib/errors'
import { StatusBadge } from '@/components/StatusBadge'
import { useLoad } from '@/features/loads/api'
import { formatDateTime, formatEnum, formatText, formatWindow } from '@/lib/format'
import { useJob } from './api'

export function JobDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: job, isPending, isError, error, refetch } = useJob(id)

  if (isPending) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Job" backTo={{ to: '/jobs', label: 'Back to jobs' }} />
        <DetailSkeleton />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Job" backTo={{ to: '/jobs', label: 'Back to jobs' }} />
        <ErrorState error={error} onRetry={() => refetch()} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title={`${formatEnum(job.jobType)} · Leg ${job.sequence}`}
        description={`Job ${job.id}`}
        backTo={{ to: '/jobs', label: 'Back to jobs' }}
        actions={
          <div className="flex items-center gap-3">
            <StatusBadge status={job.status} />
            <ButtonLink to={`/jobs/${job.id}/edit`} variant="secondary">
              <Pencil className="h-3.5 w-3.5" />
              Edit
            </ButtonLink>
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <DetailCard title="Schedule">
          <DetailList>
            <DetailField label="Scheduled for">{formatDateTime(job.scheduledAt)}</DetailField>
            <DetailField label="Sequence">{job.sequence}</DetailField>
          </DetailList>
        </DetailCard>

        <DetailCard title="Record">
          <DetailList>
            <DetailField label="Job ID">
              <span className="font-mono text-xs">{job.id}</span>
            </DetailField>
            <DetailField label="Type">
              <StatusBadge status={job.jobType} />
            </DetailField>
            <DetailField label="Created">{formatDateTime(job.createdAt)}</DetailField>
            <DetailField label="Last updated">{formatDateTime(job.updatedAt)}</DetailField>
          </DetailList>
        </DetailCard>

        <ParentLoad loadId={job.loadId} className="lg:col-span-2" />
      </div>
    </div>
  )
}

/** The load this job belongs to. Its failure is inlined, so it can't blank out the job itself. */
function ParentLoad({ loadId, className }: { loadId: string; className?: string }) {
  const { data: load, isPending, isError, error } = useLoad(loadId)

  return (
    <DetailCard title="Load" className={className}>
      {isPending ? (
        <DetailSkeleton />
      ) : isError ? (
        <p className="text-sm text-muted-foreground">
          Couldn&apos;t load the parent record — {errorMessage(error)}{' '}
          <Link to={`/loads/${loadId}`} className="underline">
            Open it directly
          </Link>
          .
        </p>
      ) : (
        <DetailList>
          <DetailField label="Route">
            <Link to={`/loads/${load.id}`} className="font-medium hover:underline">
              {load.origin} → {load.destination}
            </Link>
          </DetailField>
          <DetailField label="Load status">
            <StatusBadge status={load.status} />
          </DetailField>
          <DetailField label="Customer">{formatText(load.customerId)}</DetailField>
          <DetailField label="Load ID">
            <span className="font-mono text-xs">{load.id}</span>
          </DetailField>
          <DetailField label="Pickup window">
            {formatWindow(load.pickupWindowStart, load.pickupWindowEnd)}
          </DetailField>
          <DetailField label="Dropoff window">
            {formatWindow(load.dropoffWindowStart, load.dropoffWindowEnd)}
          </DetailField>
        </DetailList>
      )}
    </DetailCard>
  )
}
