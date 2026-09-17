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
  createdAt: string
  updatedAt: string
}

// ── Audit ────────────────────────────────────────────────────────────────────

export type AuditEntityType = 'LOAD' | 'JOB' | 'ASSIGNMENT'

export type AuditAction = 'CREATE' | 'UPDATE'

export interface AuditEvent {
  id: string
  entityType: AuditEntityType
  entityId: string
  action: AuditAction
  actor: string
  occurredAt: string
}
