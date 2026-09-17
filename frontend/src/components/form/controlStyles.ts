/** Shared control styling; `aria-invalid` drives the error outline so it can't drift from state. */
export const controlClass =
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-destructive aria-[invalid=true]:focus-visible:ring-destructive'

export const textAreaClass = controlClass.replace('h-9', 'min-h-20 py-2')
