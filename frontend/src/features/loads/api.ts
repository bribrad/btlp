import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { Load, LoadStatus, PagedResponse } from '@/types'

export interface LoadListParams {
  q?: string
  status?: LoadStatus | ''
  page?: number
  size?: number
}

export const loadKeys = {
  all: ['loads'] as const,
  list: (params: LoadListParams) => [...loadKeys.all, 'list', params] as const,
  detail: (id: string) => [...loadKeys.all, 'detail', id] as const,
}

/** Serializes list params, dropping the empty ones so they don't reach the API as `?q=`. */
export function buildListQuery(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function useLoads({ q, status, page = 0, size = 20 }: LoadListParams) {
  return useQuery({
    queryKey: loadKeys.list({ q, status, page, size }),
    queryFn: () =>
      api.get<PagedResponse<Load>>(`/loads${buildListQuery({ q, status, page, size })}`),
    // Keep the previous page on screen while the next one loads, so paging doesn't flash empty.
    placeholderData: keepPreviousData,
  })
}

export function useLoad(id: string | undefined) {
  return useQuery({
    queryKey: loadKeys.detail(id ?? ''),
    queryFn: () => api.get<Load>(`/loads/${id}`),
    enabled: Boolean(id),
  })
}
