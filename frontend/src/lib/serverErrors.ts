import { ApiRequestError } from '@/api/client'

/**
 * Splits a backend VALIDATION_ERROR message into per-field messages.
 *
 * `ApiExceptionHandler` joins field errors as `"<field> <message>; <field> <message>"`, so the
 * first token of each part is the field name — which matches the form field names one-for-one.
 */
export function parseFieldErrors(message: string): Record<string, string> {
  const fieldErrors: Record<string, string> = {}
  for (const part of message.split(';')) {
    const trimmed = part.trim()
    if (trimmed === '') continue
    const separator = trimmed.indexOf(' ')
    if (separator <= 0) continue
    const field = trimmed.slice(0, separator)
    // Only treat it as a field error when the name looks like one of our camelCase fields.
    if (!/^[a-z][A-Za-z0-9]*$/.test(field)) continue
    fieldErrors[field] = trimmed.slice(separator + 1)
  }
  return fieldErrors
}

/** Field-level errors carried by a 400 VALIDATION_ERROR, or an empty object for anything else. */
export function fieldErrorsFrom(error: unknown): Record<string, string> {
  if (error instanceof ApiRequestError && error.code === 'VALIDATION_ERROR') {
    return parseFieldErrors(error.message)
  }
  return {}
}
