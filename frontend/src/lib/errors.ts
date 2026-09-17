import { ApiRequestError } from '@/api/client'

/** Turns an unknown thrown value into a message a dispatcher can act on. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 404) return 'That record no longer exists.'
    if (error.status === 401) return 'Your session expired. Sign in again to continue.'
    if (error.status === 403) return 'You do not have permission to view this.'
    if (error.status >= 500) return 'The server had a problem. Try again in a moment.'
    return error.message
  }
  if (error instanceof TypeError) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}
