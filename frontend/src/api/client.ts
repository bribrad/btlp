import type { ApiError } from '@/types'

const API_BASE = 'http://localhost:8080/api/v1'
const SESSION_KEY = 'btlp_credentials'

// ── Credential store (session storage) ───────────────────────────────────────

export interface Credentials {
  username: string
  password: string
  role: string
}

export function saveCredentials(creds: Credentials) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(creds))
  alert(JSON.stringify(creds))
}

export function loadCredentials(): Credentials | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Credentials
  } catch {
    return null
  }
}

export function clearCredentials() {
  sessionStorage.removeItem(SESSION_KEY)
}

function buildAuthHeader(creds: Credentials): string {
  return `Basic ${btoa(`${creds.username}:${creds.password}`)}`
}

// ── API error class ───────────────────────────────────────────────────────────

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

// ── Core fetch wrapper ────────────────────────────────────────────────────────

async function request<T>(
  path: string,
  options: RequestInit = {},
  creds?: Credentials | null,
): Promise<T> {
  const credentials = creds ?? loadCredentials()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (credentials) {
    headers['Authorization'] = buildAuthHeader(credentials)
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (!res.ok) {
    let body: ApiError = { error: 'UNKNOWN', message: res.statusText }
    try {
      body = (await res.json()) as ApiError
    } catch {
      // ignore parse failure
    }
    throw new ApiRequestError(res.status, body.error, body.message)
  }

  // 204 No Content
  if (res.status === 204) return undefined as unknown as T

  return res.json() as Promise<T>
}

// ── HTTP helpers ──────────────────────────────────────────────────────────────

export const api = {
  get: <T>(path: string, creds?: Credentials | null) =>
    request<T>(path, { method: 'GET' }, creds),

  post: <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) =>
    request<T>(path, {
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
      headers: extraHeaders,
    }),

  put: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'PUT',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
}
