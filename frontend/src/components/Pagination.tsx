import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number // 0-based
  size: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
  isFetching?: boolean
}

export function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
  isFetching,
}: PaginationProps) {
  const first = totalElements === 0 ? 0 : page * size + 1
  const last = Math.min((page + 1) * size, totalElements)
  const hasPrevious = page > 0
  const hasNext = page + 1 < totalPages

  return (
    <div className="flex items-center justify-between gap-4 border-t px-4 py-3 text-sm">
      <p className="text-muted-foreground" aria-live="polite">
        {totalElements === 0
          ? 'No results'
          : `${first}–${last} of ${totalElements}`}
        {isFetching && <span className="ml-2">Updating…</span>}
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={!hasPrevious}
          className="inline-flex items-center gap-1 rounded-md border bg-background px-2.5 py-1.5 font-medium shadow-sm transition-colors hover:bg-accent disabled:pointer-events-none disabled:opacity-50"
        >
          <ChevronLeft className="h-4 w-4" />
          Previous
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={!hasNext}
          className="inline-flex items-center gap-1 rounded-md border bg-background px-2.5 py-1.5 font-medium shadow-sm transition-colors hover:bg-accent disabled:pointer-events-none disabled:opacity-50"
        >
          Next
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
