import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { api } from '@/api/client'
import { buildListQuery } from '@/features/loads/api'
import type { Job, JobStatus, JobType, PagedResponse } from '@/types'

export interface JobListParams {
  loadId?: string
  status?: JobStatus | ''
  jobType?: JobType | ''
  page?: number
  size?: number
}

export const jobKeys = {
  all: ['jobs'] as const,
  list: (params: JobListParams) => [...jobKeys.all, 'list', params] as const,
  detail: (id: string) => [...jobKeys.all, 'detail', id] as const,
}

export function useJobs({ loadId, status, jobType, page = 0, size = 20 }: JobListParams) {
  return useQuery({
    queryKey: jobKeys.list({ loadId, status, jobType, page, size }),
    queryFn: () =>
      api.get<PagedResponse<Job>>(
        `/jobs${buildListQuery({ loadId, status, jobType, page, size })}`,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useJob(id: string | undefined) {
  return useQuery({
    queryKey: jobKeys.detail(id ?? ''),
    queryFn: () => api.get<Job>(`/jobs/${id}`),
    enabled: Boolean(id),
  })
}

export function useCreateJob() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: unknown) => api.post<Job>('/jobs', body),
    onSuccess: created => {
      queryClient.setQueryData(jobKeys.detail(created.id), created)
      queryClient.invalidateQueries({ queryKey: jobKeys.all })
    },
  })
}

export function useUpdateJob(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: unknown) => api.put<Job>(`/jobs/${id}`, body),
    onSuccess: updated => {
      queryClient.setQueryData(jobKeys.detail(id), updated)
      queryClient.invalidateQueries({ queryKey: jobKeys.all })
    },
  })
}
