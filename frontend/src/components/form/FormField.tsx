import { useId } from 'react'
import { cn } from '@/lib/utils'

interface FormFieldProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
  wide?: boolean
  /** Receives the wiring every control needs to be announced correctly by a screen reader. */
  children: (props: {
    id: string
    'aria-invalid': boolean
    'aria-describedby': string | undefined
  }) => React.ReactNode
}

export function FormField({
  label,
  error,
  hint,
  required,
  wide,
  children,
}: FormFieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ')

  return (
    <div className={cn('space-y-1.5', wide && 'sm:col-span-2')}>
      <label htmlFor={id} className="text-sm font-medium leading-none">
        {label}
        {required && (
          <span className="ml-0.5 text-destructive" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children({
        id,
        'aria-invalid': Boolean(error),
        'aria-describedby': describedBy === '' ? undefined : describedBy,
      })}
      {hint && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
