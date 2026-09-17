import { z } from 'zod'
import { compareLocalInputs, isoToLocalInput, localInputToIso } from '@/lib/datetime'
import type { Load } from '@/types'

/**
 * Mirrors the server-side constraints on LoadCreateRequest/LoadUpdateRequest so the dispatcher
 * gets the same answer without a round trip. The server stays authoritative — anything it
 * rejects is mapped back onto these same field names.
 *
 * Every field is a string because that is what the inputs hold; conversion happens in
 * `toCreateRequest`/`toUpdateRequest`.
 */
export const loadFormSchema = z
  .object({
    customerId: z.string().trim().max(100, 'Must be 100 characters or fewer'),
    origin: z
      .string()
      .trim()
      .min(1, 'Origin is required')
      .max(500, 'Must be 500 characters or fewer'),
    destination: z
      .string()
      .trim()
      .min(1, 'Destination is required')
      .max(500, 'Must be 500 characters or fewer'),
    pickupWindowStart: z.string(),
    pickupWindowEnd: z.string(),
    dropoffWindowStart: z.string(),
    dropoffWindowEnd: z.string(),
    rateAmount: z
      .string()
      .refine(value => value === '' || Number.isFinite(Number(value)), 'Must be a number')
      .refine(value => value === '' || Number(value) >= 0, 'Must be zero or more'),
    rateCurrency: z
      .string()
      .trim()
      .refine(
        value => value === '' || /^[A-Z]{3}$/.test(value),
        'Must be a 3-letter uppercase currency code',
      ),
    notes: z.string().max(2000, 'Must be 2000 characters or fewer'),
  })
  // Timeline rules, matching ChronologicalWindowsValidator: each applies only when both ends
  // are filled in, and the error lands on the later field.
  .superRefine((values, ctx) => {
    const pickupOrder = compareLocalInputs(values.pickupWindowStart, values.pickupWindowEnd)
    if (pickupOrder !== null && pickupOrder > 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['pickupWindowEnd'],
        message: 'Must be at or after the pickup window start',
      })
    }

    const dropoffOrder = compareLocalInputs(values.dropoffWindowStart, values.dropoffWindowEnd)
    if (dropoffOrder !== null && dropoffOrder > 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['dropoffWindowEnd'],
        message: 'Must be at or after the dropoff window start',
      })
    }

    const legOrder = compareLocalInputs(values.pickupWindowStart, values.dropoffWindowStart)
    if (legOrder !== null && legOrder > 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['dropoffWindowStart'],
        message: 'Must be at or after the pickup window start',
      })
    }
  })

export type LoadFormValues = z.infer<typeof loadFormSchema>

export const emptyLoadForm: LoadFormValues = {
  customerId: '',
  origin: '',
  destination: '',
  pickupWindowStart: '',
  pickupWindowEnd: '',
  dropoffWindowStart: '',
  dropoffWindowEnd: '',
  rateAmount: '',
  rateCurrency: 'USD',
  notes: '',
}

export function loadToFormValues(load: Load): LoadFormValues {
  return {
    customerId: load.customerId ?? '',
    origin: load.origin,
    destination: load.destination,
    pickupWindowStart: isoToLocalInput(load.pickupWindowStart),
    pickupWindowEnd: isoToLocalInput(load.pickupWindowEnd),
    dropoffWindowStart: isoToLocalInput(load.dropoffWindowStart),
    dropoffWindowEnd: isoToLocalInput(load.dropoffWindowEnd),
    rateAmount: load.rateAmount === null ? '' : String(load.rateAmount),
    rateCurrency: load.rateCurrency ?? '',
    notes: load.notes ?? '',
  }
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

/** Shared body for both verbs; empty inputs are sent as null so the server clears them. */
function toRequestBody(values: LoadFormValues) {
  return {
    origin: values.origin.trim(),
    destination: values.destination.trim(),
    pickupWindowStart: localInputToIso(values.pickupWindowStart),
    pickupWindowEnd: localInputToIso(values.pickupWindowEnd),
    dropoffWindowStart: localInputToIso(values.dropoffWindowStart),
    dropoffWindowEnd: localInputToIso(values.dropoffWindowEnd),
    rateAmount: values.rateAmount.trim() === '' ? null : Number(values.rateAmount),
    rateCurrency: blankToNull(values.rateCurrency),
    notes: blankToNull(values.notes),
  }
}

export function toCreateRequest(values: LoadFormValues) {
  return { ...toRequestBody(values), customerId: blankToNull(values.customerId) }
}

/** Update omits customerId — the API does not accept a change of customer on an existing load. */
export function toUpdateRequest(values: LoadFormValues) {
  return toRequestBody(values)
}
