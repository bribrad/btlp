// ── Shared ──────────────────────────────────────────────────────────────────

/** Mirrors the backend `PagedResponse` envelope returned by every list endpoint. */
export interface PagedResponse<T> {
  content: T[]
  page: number // 0-based page index
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiError {
  error: string
  message: string
}

// ── Load ─────────────────────────────────────────────────────────────────────

export const LOAD_STATUSES = [
  'PLANNED',
  'ASSIGNED',
  'IN_TRANSIT',
  'DELIVERED',
  'COMPLETED',
  'CANCELED',
] as const

export type LoadStatus = (typeof LOAD_STATUSES)[number]

export interface Load {
  id: string
  customerId: string | null
  origin: string
  destination: string
  pickupWindowStart: string | null
  pickupWindowEnd: string | null
  dropoffWindowStart: string | null
  dropoffWindowEnd: string | null
  rateAmount: number | null
  rateCurrency: string | null
  notes: string | null
  status: LoadStatus
  createdBy: string | null
  updatedBy: string | null
  createdAt: string
  updatedAt: string
}

// ── Job ──────────────────────────────────────────────────────────────────────

export const JOB_STATUSES = [
  'UNASSIGNED',
  'ASSIGNED',
  'EN_ROUTE',
  'ARRIVED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELED',
] as const

export type JobStatus = (typeof JOB_STATUSES)[number]

export const JOB_TYPES = ['PICKUP', 'DROPOFF'] as const

export type JobType = (typeof JOB_TYPES)[number]

export interface Job {
  id: string
  loadId: string
  jobType: JobType
  sequence: number
  status: JobStatus
  scheduledAt: string | null
  createdAt: string
  updatedAt: string
}

// ── Driver ───────────────────────────────────────────────────────────────────

export type DriverAvailability = 'AVAILABLE' | 'UNAVAILABLE' | 'ON_TRIP'

export type DriverStatus = 'ACTIVE' | 'INACTIVE'

export interface Driver {
  id: string
  name: string
  phone: string
  licenseNumber: string
  availability: DriverAvailability
  status: DriverStatus
  createdAt: string
  updatedAt: string
}

// ── Assignment ───────────────────────────────────────────────────────────────

export type AssignmentState =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELED'
  | 'COMPLETED'

export interface Assignment {
  id: string
  jobId: string
  driverId: string
  state: AssignmentState
  assignedAt: string
  acceptedAt: string | null
  expiresAt: string
  /** Dispatcher who created the assignment; null for rows predating actor stamping. */
  createdBy: string | null
  /** Whoever applied the last transition — a dispatcher, the driver, or `system` on expiry. */
  updatedBy: string | null
  createdAt: string
  updatedAt: string
}

// ── Dispatch board ───────────────────────────────────────────────────────────

/** Board column, derived by the backend from the job's active assignment. */
export const DISPATCH_BOARD_LANES = [
  'NEEDS_DISPATCH',
  'PENDING_ACCEPTANCE',
  'IN_PROGRESS',
] as const

export type DispatchBoardLane = (typeof DISPATCH_BOARD_LANES)[number]

/** A job on the dispatch board, joined to its load and its active assignment. */
export interface DispatchBoardEntry {
  jobId: string
  loadId: string
  jobType: JobType
  sequence: number
  jobStatus: JobStatus
  scheduledAt: string | null
  origin: string
  destination: string
  lane: DispatchBoardLane
  // Assignment and driver fields are null in the NEEDS_DISPATCH lane.
  assignmentId: string | null
  assignmentState: AssignmentState | null
  assignedAt: string | null
  expiresAt: string | null
  assignedBy: string | null
  driverId: string | null
  driverName: string | null
  driverPhone: string | null
}

// ── Audit ────────────────────────────────────────────────────────────────────

export type AuditEntityType = 'LOAD' | 'JOB' | 'ASSIGNMENT'

export const AUDIT_ACTIONS = [
  'CREATE',
  'UPDATE',
  'STATUS_CHANGE',
  'ASSIGN',
  'REASSIGN',
  'CANCEL',
  'ACCEPT',
  'REJECT',
  'EXPIRE',
  'COMPLETE',
] as const

export type AuditAction = (typeof AUDIT_ACTIONS)[number]

export interface AuditEvent {
  id: string
  /** Monotonic insertion order; the timeline sorts on this, not on `occurredAt`. */
  sequence: number
  entityType: AuditEntityType
  entityId: string
  action: AuditAction
  /** The entity's resulting status/state, or null for actions that have none. */
  detail: string | null
  actor: string
  occurredAt: string
}

// ── Activity timeline ────────────────────────────────────────────────────────

/**
 * An audit event resolved against the load, job, assignment, and driver it concerns. Context
 * fields narrow with the entity type — a load event carries no job, only an assignment event
 * carries a driver — and are also null once the referenced row is gone.
 */
export interface ActivityEvent {
  id: string
  sequence: number
  occurredAt: string
  actor: string
  entityType: AuditEntityType
  entityId: string
  action: AuditAction
  detail: string | null
  loadId: string | null
  origin: string | null
  destination: string | null
  jobId: string | null
  jobType: JobType | null
  jobSequence: number | null
  assignmentId: string | null
  driverId: string | null
  driverName: string | null
}
