import { describe, it, expect } from 'vitest'
import { ApiRequestError } from '@/api/client'
import { errorMessage } from '@/lib/errors'

describe('errorMessage', () => {
  it('explains the common API failures in plain language', () => {
    expect(errorMessage(new ApiRequestError(404, 'NOT_FOUND', 'Load not found'))).toBe(
      'That record no longer exists.',
    )
    expect(errorMessage(new ApiRequestError(401, 'UNAUTHORIZED', 'nope'))).toContain('session')
    expect(errorMessage(new ApiRequestError(403, 'FORBIDDEN', 'nope'))).toContain('permission')
    expect(errorMessage(new ApiRequestError(500, 'ERROR', 'boom'))).toContain('server')
  })

  it('passes through other client-error messages', () => {
    expect(errorMessage(new ApiRequestError(400, 'VALIDATION_ERROR', 'origin must not be blank')))
      .toBe('origin must not be blank')
  })

  it('treats a failed fetch as a connectivity problem', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toContain('Could not reach the server')
  })

  it('handles values that are not errors', () => {
    expect(errorMessage('weird')).toBe('Something went wrong.')
  })
})
