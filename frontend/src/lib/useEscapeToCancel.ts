import { useEffect } from 'react'

/** Escape anywhere in a form backs out of it — the keyboard counterpart to Cancel. */
export function useEscapeToCancel(onCancel: () => void) {
  useEffect(() => {
    function handle(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', handle)
    return () => document.removeEventListener('keydown', handle)
  }, [onCancel])
}
