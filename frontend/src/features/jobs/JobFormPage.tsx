import { useCallback, useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import { DetailSkeleton, ErrorState } from '@/components/QueryStates'
import { FormActions } from '@/components/form/FormActions'
import { FormField } from '@/components/form/FormField'
import { controlClass } from '@/components/form/controlStyles'
import { useLoads } from '@/features/loads/api'
import { errorMessage } from '@/lib/errors'
import { formatEnum } from '@/lib/format'
import { fieldErrorsFrom } from '@/lib/serverErrors'
import { useEscapeToCancel } from '@/lib/useEscapeToCancel'
import { JOB_TYPES } from '@/types'
import { useCreateJob, useJob, useUpdateJob } from './api'
import {
  emptyJobForm,
  jobCreateSchema,
  jobEditSchema,
  jobToFormValues,
  toCreateRequest,
  toUpdateRequest,
  type JobFormValues,
} from './jobForm'

const FORM_FIELDS = ['loadId', 'jobType', 'sequence', 'scheduledAt']

export function JobFormPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const presetLoadId = searchParams.get('loadId') ?? ''
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const jobQuery = useJob(id)
  const createJob = useCreateJob()
  const updateJob = useUpdateJob(id ?? '')
  const isSaving = createJob.isPending || updateJob.isPending

  // Only needed for the picker when creating a job that didn't come from a load.
  const loadsQuery = useLoads({ size: 100 })

  const [formError, setFormError] = useState<string | null>(null)

  const form = useForm<JobFormValues>({
    resolver: zodResolver(isEdit ? jobEditSchema : jobCreateSchema),
    defaultValues: emptyJobForm(presetLoadId),
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  })
  const { register, handleSubmit, reset, setError, formState, watch } = form
  const { errors } = formState
  // Watched so the load <select> can be controlled: the preset id is set before the
  // options finish loading, and an uncontrolled select would drop it.
  const selectedLoadId = watch('loadId')

  // Seed once; a background refetch must not overwrite edits in progress.
  const seeded = useRef(false)
  useEffect(() => {
    if (jobQuery.data && !seeded.current) {
      seeded.current = true
      reset(jobToFormValues(jobQuery.data))
    }
  }, [jobQuery.data, reset])

  const cancel = useCallback(() => {
    if (isEdit) navigate(`/jobs/${id}`)
    else if (presetLoadId) navigate(`/loads/${presetLoadId}`)
    else navigate('/jobs')
  }, [navigate, isEdit, id, presetLoadId])
  useEscapeToCancel(cancel)

  const onSubmit = handleSubmit(async values => {
    setFormError(null)
    try {
      const saved = isEdit
        ? await updateJob.mutateAsync(toUpdateRequest(values))
        : await createJob.mutateAsync(toCreateRequest(values))
      navigate(`/jobs/${saved.id}`, { replace: true })
    } catch (error) {
      const fieldErrors = fieldErrorsFrom(error)
      const known = Object.entries(fieldErrors).filter(([field]) => FORM_FIELDS.includes(field))
      known.forEach(([field, message], index) =>
        setError(field as keyof JobFormValues, { message }, { shouldFocus: index === 0 }),
      )
      if (known.length === 0) setFormError(errorMessage(error))
    }
  })

  if (isEdit && jobQuery.isPending) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Edit job" backTo={{ to: `/jobs/${id}`, label: 'Back to job' }} />
        <DetailSkeleton />
      </div>
    )
  }

  if (isEdit && jobQuery.isError) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Edit job" backTo={{ to: '/jobs', label: 'Back to jobs' }} />
        <ErrorState error={jobQuery.error} onRetry={() => jobQuery.refetch()} />
      </div>
    )
  }

  const loads = loadsQuery.data?.content ?? []
  const backTo = isEdit
    ? { to: `/jobs/${id}`, label: 'Back to job' }
    : presetLoadId
      ? { to: `/loads/${presetLoadId}`, label: 'Back to load' }
      : { to: '/jobs', label: 'Back to jobs' }

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title={isEdit ? 'Edit job' : 'New job'}
        description={
          isEdit
            ? 'Status is managed by dispatch and cannot be edited here.'
            : 'A new job starts unassigned.'
        }
        backTo={backTo}
      />

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-6">
        {formError && (
          <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            {formError}
          </p>
        )}

        <section className="rounded-lg border bg-card">
          <h2 className="border-b px-4 py-3 text-sm font-medium">Job</h2>
          <div className="grid gap-4 p-4 sm:grid-cols-2">
            <FormField
              label="Load"
              required
              wide
              error={errors.loadId?.message}
              hint={isEdit ? 'A job cannot be moved to another load.' : undefined}
            >
              {props =>
                isEdit ? (
                  <input
                    {...props}
                    value={selectedLoadId}
                    readOnly
                    disabled
                    className={`${controlClass} font-mono text-xs`}
                  />
                ) : (
                  <select
                    {...props}
                    {...register('loadId')}
                    value={selectedLoadId}
                    autoFocus={!presetLoadId}
                    className={controlClass}
                  >
                    <option value="">Select a load…</option>
                    {loads.map(load => (
                      <option key={load.id} value={load.id}>
                        {load.origin} → {load.destination}
                        {load.customerId ? ` (${load.customerId})` : ''}
                      </option>
                    ))}
                  </select>
                )
              }
            </FormField>

            <FormField label="Type" required error={errors.jobType?.message}>
              {props => (
                <select
                  {...props}
                  {...register('jobType')}
                  autoFocus={isEdit || Boolean(presetLoadId)}
                  className={controlClass}
                >
                  {JOB_TYPES.map(type => (
                    <option key={type} value={type}>
                      {formatEnum(type)}
                    </option>
                  ))}
                </select>
              )}
            </FormField>

            <FormField
              label="Leg"
              required={isEdit}
              error={errors.sequence?.message}
              hint={isEdit ? undefined : 'Leave blank to add this as the next leg.'}
            >
              {props => (
                <input
                  {...props}
                  {...register('sequence')}
                  type="number"
                  min="1"
                  step="1"
                  inputMode="numeric"
                  className={controlClass}
                />
              )}
            </FormField>

            <FormField label="Scheduled for" wide error={errors.scheduledAt?.message}>
              {props => (
                <input
                  {...props}
                  {...register('scheduledAt')}
                  type="datetime-local"
                  className={controlClass}
                />
              )}
            </FormField>
          </div>
        </section>

        <FormActions
          submitLabel={isEdit ? 'Save changes' : 'Create job'}
          pendingLabel="Saving…"
          isPending={isSaving}
          onCancel={cancel}
        />
      </form>
    </div>
  )
}
