// ── Shared ──────────────────────────────────────────────────────────────────

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number // 0-based page index
  size: number
}

export interface ApiError {
  error: string
  message: string
}

// ── Load ─────────────────────────────────────────────────────────────────────

export type LoadStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface Load {
  id: string
  origin: string
  destination: string
  pickupWindowStart: string
  pickupWindowEnd: string
  dropoffWindowStart: string
  dropoffWindowEnd: string
  rate: number
  status: LoadStatus
  customerId?: string
  createdAt: string
  updatedAt: string
}

// ── Job ──────────────────────────────────────────────────────────────────────

export type JobStatus =
  | 'UNASSIGNED'
  | 'ASSIGNED'
  | 'EN_ROUTE'
  | 'ARRIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'

export type JobType = 'PICKUP' | 'DROPOFF'

export interface Job {
  id: string
  loadId: string
  jobType: JobType
  sequence: number
  status: JobStatus
  scheduledAt?: string
  createdAt: string
  updatedAt: string
}

// ── Driver ───────────────────────────────────────────────────────────────────

export type DriverAvailability = 'AVAILABLE' | 'ON_TRIP' | 'OFF_DUTY'

export interface Driver {
  id: string
  name: string
  phone: string
  licenseNumber: string
  availability: DriverAvailability
  createdAt: string
  updatedAt: string
}

// ── Assignment ───────────────────────────────────────────────────────────────

export type AssignmentState =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'COMPLETED'
  | 'CANCELLED'

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

export type AuditEntityType = 'LOAD' | 'JOB' | 'ASSIGNMENT' | 'DRIVER'

export interface AuditEvent {
  id: string
  action: string
  entityType: AuditEntityType
  entityId: string
  actorUsername: string
  timestamp: string
  details?: Record<string, unknown>
}
