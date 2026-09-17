import { z } from 'zod'
import { isoToLocalInput, localInputToIso } from '@/lib/datetime'
import { JOB_TYPES, type Job } from '@/types'

/**
 * Mirrors JobCreateRequest/JobUpdateRequest. The two differ in one place: create lets the server
 * assign the next leg when sequence is blank, while update requires it (@NotNull @Positive).
 */
function makeJobFormSchema({ requireSequence }: { requireSequence: boolean }) {
  const sequence = z
    .string()
    .refine(value => value === '' || /^\d+$/.test(value), 'Must be a whole number')
    .refine(value => value === '' || Number(value) > 0, 'Must be greater than zero')
    .refine(value => !requireSequence || value !== '', 'Leg is required')

  return z.object({
    loadId: z.string().uuid('Select a load'),
    jobType: z.enum(JOB_TYPES, { errorMap: () => ({ message: 'Select a job type' }) }),
    sequence,
    scheduledAt: z.string(),
  })
}

export const jobCreateSchema = makeJobFormSchema({ requireSequence: false })
export const jobEditSchema = makeJobFormSchema({ requireSequence: true })

export type JobFormValues = z.infer<typeof jobCreateSchema>

export function emptyJobForm(loadId = ''): JobFormValues {
  return { loadId, jobType: 'PICKUP', sequence: '', scheduledAt: '' }
}

export function jobToFormValues(job: Job): JobFormValues {
  return {
    loadId: job.loadId,
    jobType: job.jobType,
    sequence: String(job.sequence),
    scheduledAt: isoToLocalInput(job.scheduledAt),
  }
}

/** Sequence left blank means "next leg on this load" — the server assigns it. */
export function toCreateRequest(values: JobFormValues) {
  return {
    loadId: values.loadId,
    jobType: values.jobType,
    sequence: values.sequence === '' ? null : Number(values.sequence),
    scheduledAt: localInputToIso(values.scheduledAt),
  }
}

export function toUpdateRequest(values: JobFormValues) {
  return {
    jobType: values.jobType,
    sequence: Number(values.sequence),
    scheduledAt: localInputToIso(values.scheduledAt),
  }
}
