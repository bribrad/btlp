import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import { buildListQuery } from '@/features/loads/api'
import type { ActivityEvent, PagedResponse } from '@/types'

export interface ActivityParams {
  /** Widens to every job and assignment on the load. */
  loadId?: string
  /** Narrows to a single leg. Combined with `loadId` the two intersect. */
  jobId?: string
  page?: number
  size?: number
}

export const activityKeys = {
  all: ['activity'] as const,
  list: (params: ActivityParams) => [...activityKeys.all, 'list', params] as const,
}

export function useActivity({ loadId, jobId, page = 0, size = 20 }: ActivityParams) {
  return useQuery({
    queryKey: activityKeys.list({ loadId, jobId, page, size }),
    queryFn: () =>
      api.get<PagedResponse<ActivityEvent>>(
        `/activity${buildListQuery({ loadId, jobId, page, size })}`,
      ),
    placeholderData: keepPreviousData,
  })
}
