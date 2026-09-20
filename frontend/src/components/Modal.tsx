import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import { useEscapeToCancel } from '@/lib/useEscapeToCancel'

interface ModalProps {
  title: string
  description?: string
  onClose: () => void
  children: React.ReactNode
}

/**
 * Minimal modal dialog: labelled for screen readers, dismissed with Escape, the close button, or
 * a click on the backdrop. Focus moves into the dialog on open so the keyboard lands inside it.
 */
export function Modal({ title, description, onClose, children }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEscapeToCancel(onClose)

  useEffect(() => {
    panelRef.current?.focus()
  }, [])

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-4 pt-[10vh]"
      onClick={event => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className="w-full max-w-md rounded-lg border bg-card shadow-lg focus:outline-none"
      >
        <div className="flex items-start justify-between gap-3 border-b px-4 py-3">
          <div className="space-y-1">
            <h2 className="text-sm font-medium">{title}</h2>
            {description && <p className="text-xs text-muted-foreground">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded p-1 text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="p-4">{children}</div>
      </div>
    </div>
  )
}
