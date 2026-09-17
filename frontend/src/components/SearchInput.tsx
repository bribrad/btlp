import { useEffect, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'

interface SearchInputProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  label: string
  delay?: number
}

/**
 * Text filter that keeps typing local and only reports upward after a pause, so each
 * keystroke doesn't fire a request. Re-syncs when `value` changes elsewhere (e.g. "Clear").
 */
export function SearchInput({
  value,
  onChange,
  placeholder,
  label,
  delay = 300,
}: SearchInputProps) {
  const [text, setText] = useState(value)
  const onChangeRef = useRef(onChange)

  useEffect(() => {
    onChangeRef.current = onChange
  })

  useEffect(() => {
    setText(value)
  }, [value])

  useEffect(() => {
    if (text === value) return
    const timer = setTimeout(() => onChangeRef.current(text), delay)
    return () => clearTimeout(timer)
  }, [text, value, delay])

  return (
    <div className="relative flex-1 sm:max-w-xs">
      <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <input
        type="search"
        aria-label={label}
        value={text}
        placeholder={placeholder}
        onChange={event => setText(event.target.value)}
        className="flex h-9 w-full rounded-md border border-input bg-transparent pl-8 pr-8 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      />
      {text !== '' && (
        <button
          type="button"
          aria-label="Clear search"
          onClick={() => setText('')}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-muted-foreground transition-colors hover:text-foreground"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  )
}
