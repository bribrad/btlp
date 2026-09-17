import { cn } from '@/lib/utils'
import { formatEnum } from '@/lib/format'
import type { JobStatus, JobType, LoadStatus } from '@/types'

/**
 * Colour per lifecycle status: grey = not started, blue = in flight, green = done,
 * red = terminated. Statuses share a palette across loads and jobs so the two lists read alike.
 */
const STATUS_STYLES: Record<string, string> = {
  // Load
  PLANNED: 'bg-muted text-muted-foreground',
  ASSIGNED: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  IN_TRANSIT: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  DELIVERED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  CANCELED: 'bg-red-50 text-red-700 ring-red-600/20',
  // Job
  UNASSIGNED: 'bg-muted text-muted-foreground',
  EN_ROUTE: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  ARRIVED: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  IN_PROGRESS: 'bg-amber-50 text-amber-700 ring-amber-600/20',
}

const FALLBACK_STYLE = 'bg-muted text-muted-foreground'

interface StatusBadgeProps {
  status: LoadStatus | JobStatus | JobType | string
  className?: string
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ring-transparent whitespace-nowrap',
        STATUS_STYLES[status] ?? FALLBACK_STYLE,
        className,
      )}
    >
      {formatEnum(status)}
    </span>
  )
}
