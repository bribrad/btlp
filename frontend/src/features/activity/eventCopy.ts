import {
  ArrowRight,
  Check,
  Flag,
  Pencil,
  Plus,
  Repeat2,
  Send,
  TimerOff,
  Undo2,
  X,
  type LucideIcon,
} from 'lucide-react'
import { formatEnum, shortId } from '@/lib/format'
import type { ActivityEvent, AuditAction } from '@/types'

/**
 * Icon and dot colour per action: green = it moved forward, red = it fell through, blue = a
 * dispatcher acted, grey = a record was edited. Mirrors the palette `StatusBadge` uses.
 */
const ACTION_STYLES: Record<AuditAction, { icon: LucideIcon; tone: string }> = {
  CREATE: { icon: Plus, tone: 'bg-muted text-muted-foreground' },
  UPDATE: { icon: Pencil, tone: 'bg-muted text-muted-foreground' },
  STATUS_CHANGE: { icon: ArrowRight, tone: 'bg-blue-50 text-blue-700' },
  ASSIGN: { icon: Send, tone: 'bg-blue-50 text-blue-700' },
  REASSIGN: { icon: Repeat2, tone: 'bg-blue-50 text-blue-700' },
  CANCEL: { icon: Undo2, tone: 'bg-red-50 text-red-700' },
  ACCEPT: { icon: Check, tone: 'bg-emerald-50 text-emerald-700' },
  REJECT: { icon: X, tone: 'bg-red-50 text-red-700' },
  EXPIRE: { icon: TimerOff, tone: 'bg-red-50 text-red-700' },
  COMPLETE: { icon: Flag, tone: 'bg-emerald-50 text-emerald-700' },
}

const FALLBACK_STYLE = { icon: Pencil, tone: 'bg-muted text-muted-foreground' }

export function actionStyle(action: AuditAction) {
  return ACTION_STYLES[action] ?? FALLBACK_STYLE
}

/** The "what" of an event, phrased the way a dispatcher would read it off a feed. */
export function summarize(event: ActivityEvent): string {
  const driver = driverLabel(event)
  switch (event.action) {
    case 'ASSIGN':
      return `Dispatched to ${driver}`
    case 'REASSIGN':
      return `Reassigned to ${driver}`
    case 'CANCEL':
      return `Assignment to ${driver} pulled back`
    case 'ACCEPT':
      return `${driver} accepted`
    case 'REJECT':
      return `${driver} declined`
    case 'EXPIRE':
      return `${driver} let the acceptance window lapse`
    case 'COMPLETE':
      return `${driver} completed the job`
    case 'STATUS_CHANGE':
      return `Job status → ${formatEnum(event.detail)}`
    case 'CREATE':
      return `${entityNoun(event)} created`
    case 'UPDATE':
      return `${entityNoun(event)} updated`
    default:
      return `${entityNoun(event)} ${formatEnum(event.action).toLowerCase()}`
  }
}

/**
 * Which load and leg the event concerns. Anything the caller has already scoped the timeline to
 * is dropped, so a load's own card doesn't repeat its route on every row.
 */
export function contextLabels(
  event: ActivityEvent,
  { loadId, jobId }: { loadId?: string; jobId?: string } = {},
): string[] {
  const labels: string[] = []
  if (!loadId && !jobId && event.origin && event.destination) {
    labels.push(`${event.origin} → ${event.destination}`)
  }
  if (!jobId && event.jobType && event.jobSequence !== null) {
    labels.push(`${formatEnum(event.jobType)} · Leg ${event.jobSequence}`)
  }
  return labels
}

function entityNoun(event: ActivityEvent): string {
  return formatEnum(event.entityType)
}

function driverLabel(event: ActivityEvent): string {
  if (event.driverName) return event.driverName
  return event.driverId ? `Driver ${shortId(event.driverId)}` : 'The driver'
}
