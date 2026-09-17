interface FormActionsProps {
  submitLabel: string
  pendingLabel: string
  isPending: boolean
  onCancel: () => void
}

export function FormActions({
  submitLabel,
  pendingLabel,
  isPending,
  onCancel,
}: FormActionsProps) {
  return (
    <div className="flex items-center gap-2">
      <button
        type="submit"
        disabled={isPending}
        className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
      >
        {isPending ? pendingLabel : submitLabel}
      </button>
      <button
        type="button"
        onClick={onCancel}
        className="inline-flex items-center justify-center rounded-md border bg-background px-4 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      >
        Cancel
      </button>
      <p className="ml-auto text-xs text-muted-foreground">
        Press <kbd className="rounded border px-1">Esc</kbd> to cancel
      </p>
    </div>
  )
}
