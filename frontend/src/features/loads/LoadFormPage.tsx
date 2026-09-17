import { useCallback, useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '@/components/PageHeader'
import { DetailSkeleton, ErrorState } from '@/components/QueryStates'
import { FormActions } from '@/components/form/FormActions'
import { FormField } from '@/components/form/FormField'
import { controlClass, textAreaClass } from '@/components/form/controlStyles'
import { errorMessage } from '@/lib/errors'
import { fieldErrorsFrom } from '@/lib/serverErrors'
import { useEscapeToCancel } from '@/lib/useEscapeToCancel'
import { useCreateLoad, useLoad, useUpdateLoad } from './api'
import {
  emptyLoadForm,
  loadFormSchema,
  loadToFormValues,
  toCreateRequest,
  toUpdateRequest,
  type LoadFormValues,
} from './loadForm'

export function LoadFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)
  const navigate = useNavigate()

  const loadQuery = useLoad(id)
  const createLoad = useCreateLoad()
  const updateLoad = useUpdateLoad(id ?? '')
  const isSaving = createLoad.isPending || updateLoad.isPending

  const [formError, setFormError] = useState<string | null>(null)

  const form = useForm<LoadFormValues>({
    resolver: zodResolver(loadFormSchema),
    defaultValues: emptyLoadForm,
    // Don't nag while the dispatcher is still typing, but correct them live once they've tried.
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  })
  const { register, handleSubmit, reset, setError, formState } = form
  const { errors } = formState

  // Seed the form once. A later background refetch (React Query refetches on window focus)
  // must not overwrite edits in progress.
  const seeded = useRef(false)
  useEffect(() => {
    if (loadQuery.data && !seeded.current) {
      seeded.current = true
      reset(loadToFormValues(loadQuery.data))
    }
  }, [loadQuery.data, reset])

  const cancel = useCallback(() => {
    navigate(isEdit ? `/loads/${id}` : '/loads')
  }, [navigate, isEdit, id])
  useEscapeToCancel(cancel)

  const onSubmit = handleSubmit(async values => {
    setFormError(null)
    try {
      const saved = isEdit
        ? await updateLoad.mutateAsync(toUpdateRequest(values))
        : await createLoad.mutateAsync(toCreateRequest(values))
      navigate(`/loads/${saved.id}`, { replace: true })
    } catch (error) {
      // The server is authoritative: put whatever it rejected back on the offending field.
      const fieldErrors = fieldErrorsFrom(error)
      const known = Object.entries(fieldErrors).filter(([field]) => field in emptyLoadForm)
      known.forEach(([field, message], index) =>
        setError(field as keyof LoadFormValues, { message }, { shouldFocus: index === 0 }),
      )
      if (known.length === 0) setFormError(errorMessage(error))
    }
  })

  if (isEdit && loadQuery.isPending) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Edit load" backTo={{ to: `/loads/${id}`, label: 'Back to load' }} />
        <DetailSkeleton />
      </div>
    )
  }

  if (isEdit && loadQuery.isError) {
    return (
      <div className="flex flex-col gap-6 p-8">
        <PageHeader title="Edit load" backTo={{ to: '/loads', label: 'Back to loads' }} />
        <ErrorState error={loadQuery.error} onRetry={() => loadQuery.refetch()} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 p-8">
      <PageHeader
        title={isEdit ? 'Edit load' : 'New load'}
        description={
          isEdit
            ? 'Changes apply immediately once saved.'
            : 'A new load starts in the Planned status.'
        }
        backTo={
          isEdit
            ? { to: `/loads/${id}`, label: 'Back to load' }
            : { to: '/loads', label: 'Back to loads' }
        }
      />

      <form onSubmit={onSubmit} noValidate className="flex max-w-3xl flex-col gap-6">
        {formError && (
          <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            {formError}
          </p>
        )}

        <section className="rounded-lg border bg-card">
          <h2 className="border-b px-4 py-3 text-sm font-medium">Route</h2>
          <div className="grid gap-4 p-4 sm:grid-cols-2">
            <FormField label="Origin" required error={errors.origin?.message}>
              {props => (
                <input
                  {...props}
                  {...register('origin')}
                  autoFocus
                  autoComplete="off"
                  placeholder="Chicago, IL"
                  className={controlClass}
                />
              )}
            </FormField>
            <FormField label="Destination" required error={errors.destination?.message}>
              {props => (
                <input
                  {...props}
                  {...register('destination')}
                  autoComplete="off"
                  placeholder="Dallas, TX"
                  className={controlClass}
                />
              )}
            </FormField>
            {!isEdit && (
              <FormField
                label="Customer"
                error={errors.customerId?.message}
                hint="Cannot be changed after the load is created."
              >
                {props => (
                  <input
                    {...props}
                    {...register('customerId')}
                    autoComplete="off"
                    placeholder="ACME"
                    className={controlClass}
                  />
                )}
              </FormField>
            )}
          </div>
        </section>

        <section className="rounded-lg border bg-card">
          <h2 className="border-b px-4 py-3 text-sm font-medium">Schedule</h2>
          <div className="grid gap-4 p-4 sm:grid-cols-2">
            <FormField label="Pickup window start" error={errors.pickupWindowStart?.message}>
              {props => (
                <input
                  {...props}
                  {...register('pickupWindowStart')}
                  type="datetime-local"
                  className={controlClass}
                />
              )}
            </FormField>
            <FormField label="Pickup window end" error={errors.pickupWindowEnd?.message}>
              {props => (
                <input
                  {...props}
                  {...register('pickupWindowEnd')}
                  type="datetime-local"
                  className={controlClass}
                />
              )}
            </FormField>
            <FormField label="Dropoff window start" error={errors.dropoffWindowStart?.message}>
              {props => (
                <input
                  {...props}
                  {...register('dropoffWindowStart')}
                  type="datetime-local"
                  className={controlClass}
                />
              )}
            </FormField>
            <FormField label="Dropoff window end" error={errors.dropoffWindowEnd?.message}>
              {props => (
                <input
                  {...props}
                  {...register('dropoffWindowEnd')}
                  type="datetime-local"
                  className={controlClass}
                />
              )}
            </FormField>
          </div>
        </section>

        <section className="rounded-lg border bg-card">
          <h2 className="border-b px-4 py-3 text-sm font-medium">Commercial</h2>
          <div className="grid gap-4 p-4 sm:grid-cols-2">
            <FormField label="Rate" error={errors.rateAmount?.message}>
              {props => (
                <input
                  {...props}
                  {...register('rateAmount')}
                  type="number"
                  min="0"
                  step="0.01"
                  inputMode="decimal"
                  placeholder="1250.00"
                  className={controlClass}
                />
              )}
            </FormField>
            <FormField
              label="Currency"
              error={errors.rateCurrency?.message}
              hint="Three-letter code, e.g. USD."
            >
              {props => (
                <input
                  {...props}
                  {...register('rateCurrency')}
                  autoComplete="off"
                  maxLength={3}
                  placeholder="USD"
                  className={`${controlClass} uppercase`}
                />
              )}
            </FormField>
            <FormField label="Notes" wide error={errors.notes?.message}>
              {props => (
                <textarea
                  {...props}
                  {...register('notes')}
                  rows={3}
                  placeholder="Anything the driver needs to know"
                  className={textAreaClass}
                />
              )}
            </FormField>
          </div>
        </section>

        <FormActions
          submitLabel={isEdit ? 'Save changes' : 'Create load'}
          pendingLabel="Saving…"
          isPending={isSaving}
          onCancel={cancel}
        />
      </form>
    </div>
  )
}
