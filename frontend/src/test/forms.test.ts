import { describe, it, expect } from 'vitest'
import { isoToLocalInput, localInputToIso, compareLocalInputs } from '@/lib/datetime'
import { fieldErrorsFrom, parseFieldErrors } from '@/lib/serverErrors'
import { ApiRequestError } from '@/api/client'
import {
  loadFormSchema,
  loadToFormValues,
  toCreateRequest,
  toUpdateRequest,
  emptyLoadForm,
} from '@/features/loads/loadForm'
import {
  jobCreateSchema,
  jobEditSchema,
  toCreateRequest as jobCreate,
} from '@/features/jobs/jobForm'
import { makeLoad } from './utils'

describe('datetime conversions', () => {
  it('round-trips a timestamp through the input format', () => {
    const local = isoToLocalInput('2026-03-03T14:00:00Z')
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
    expect(localInputToIso(local)).toBe(new Date('2026-03-03T14:00:00Z').toISOString())
  })

  it('treats blank and unparseable values as empty', () => {
    expect(isoToLocalInput(null)).toBe('')
    expect(isoToLocalInput('nonsense')).toBe('')
    expect(localInputToIso('')).toBeNull()
    expect(localInputToIso('nonsense')).toBeNull()
  })

  it('only compares when both sides are present', () => {
    expect(compareLocalInputs('2026-03-03T10:00', '')).toBeNull()
    expect(compareLocalInputs('2026-03-04T10:00', '2026-03-03T10:00')).toBeGreaterThan(0)
    expect(compareLocalInputs('2026-03-03T10:00', '2026-03-04T10:00')).toBeLessThan(0)
  })
})

describe('server error mapping', () => {
  it('splits the joined field errors the API returns', () => {
    expect(parseFieldErrors('destination must not be blank; origin must not be blank')).toEqual({
      origin: 'must not be blank',
      destination: 'must not be blank',
    })
  })

  it('ignores prose messages that are not field errors', () => {
    expect(parseFieldErrors('Malformed or unreadable request body.')).toEqual({})
  })

  it('only unpacks VALIDATION_ERROR responses', () => {
    expect(fieldErrorsFrom(new ApiRequestError(400, 'VALIDATION_ERROR', 'origin must not be blank')))
      .toEqual({ origin: 'must not be blank' })
    expect(fieldErrorsFrom(new ApiRequestError(409, 'CONFLICT', 'sequence taken'))).toEqual({})
    expect(fieldErrorsFrom(new Error('boom'))).toEqual({})
  })
})

function parse(overrides: Partial<typeof emptyLoadForm> = {}) {
  return loadFormSchema.safeParse({ ...emptyLoadForm, origin: 'A', destination: 'B', ...overrides })
}

function errorFor(result: ReturnType<typeof parse>, field: string) {
  if (result.success) return undefined
  return result.error.issues.find(issue => issue.path[0] === field)?.message
}

describe('loadFormSchema', () => {
  it('requires origin and destination', () => {
    const result = parse({ origin: '', destination: '   ' })
    expect(errorFor(result, 'origin')).toBe('Origin is required')
    expect(errorFor(result, 'destination')).toBe('Destination is required')
  })

  it('accepts a load with no schedule or rate at all', () => {
    expect(parse().success).toBe(true)
  })

  it('rejects a pickup window that ends before it starts', () => {
    const result = parse({
      pickupWindowStart: '2026-03-04T10:00',
      pickupWindowEnd: '2026-03-03T10:00',
    })
    expect(errorFor(result, 'pickupWindowEnd')).toContain('at or after')
  })

  it('rejects a dropoff window that ends before it starts', () => {
    const result = parse({
      dropoffWindowStart: '2026-03-06T10:00',
      dropoffWindowEnd: '2026-03-05T10:00',
    })
    expect(errorFor(result, 'dropoffWindowEnd')).toContain('at or after')
  })

  it('rejects a dropoff that starts before the pickup', () => {
    const result = parse({
      pickupWindowStart: '2026-03-06T10:00',
      dropoffWindowStart: '2026-03-04T10:00',
    })
    expect(errorFor(result, 'dropoffWindowStart')).toContain('at or after')
  })

  it('allows a half-open window, matching the server', () => {
    expect(parse({ pickupWindowStart: '2026-03-04T10:00' }).success).toBe(true)
    expect(parse({ dropoffWindowEnd: '2026-03-04T10:00' }).success).toBe(true)
  })

  it('rejects a negative rate and a malformed currency', () => {
    expect(errorFor(parse({ rateAmount: '-1' }), 'rateAmount')).toBe('Must be zero or more')
    expect(errorFor(parse({ rateCurrency: 'usd' }), 'rateCurrency')).toContain('3-letter')
    expect(parse({ rateCurrency: '' }).success).toBe(true)
  })
})

describe('load request mapping', () => {
  it('sends blanks as null so the server clears them', () => {
    const body = toCreateRequest({ ...emptyLoadForm, origin: 'A', destination: 'B' })
    expect(body).toMatchObject({
      origin: 'A',
      destination: 'B',
      customerId: null,
      rateAmount: null,
      notes: null,
      pickupWindowStart: null,
    })
  })

  it('omits customerId on update — the API will not accept it', () => {
    const body = toUpdateRequest(loadToFormValues(makeLoad()))
    expect(body).not.toHaveProperty('customerId')
    expect(body.rateAmount).toBe(1250.5)
  })
})

describe('jobFormSchema', () => {
  it('requires a load', () => {
    const result = jobCreateSchema.safeParse({
      loadId: '',
      jobType: 'PICKUP',
      sequence: '',
      scheduledAt: '',
    })
    expect(result.success).toBe(false)
  })

  it('treats a blank sequence as "next leg"', () => {
    const body = jobCreate({
      loadId: makeLoad().id,
      jobType: 'PICKUP',
      sequence: '',
      scheduledAt: '',
    })
    expect(body.sequence).toBeNull()
  })

  it('requires a leg when editing, since the API does', () => {
    const base = { loadId: makeLoad().id, jobType: 'PICKUP' as const, scheduledAt: '' }
    expect(jobCreateSchema.safeParse({ ...base, sequence: '' }).success).toBe(true)
    expect(jobEditSchema.safeParse({ ...base, sequence: '' }).success).toBe(false)
  })

  it('rejects a zero or fractional leg', () => {
    const base = { loadId: makeLoad().id, jobType: 'PICKUP' as const, scheduledAt: '' }
    expect(jobCreateSchema.safeParse({ ...base, sequence: '0' }).success).toBe(false)
    expect(jobCreateSchema.safeParse({ ...base, sequence: '1.5' }).success).toBe(false)
    expect(jobCreateSchema.safeParse({ ...base, sequence: '2' }).success).toBe(true)
  })
})
