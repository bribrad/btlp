import { formatEnum } from '@/lib/format'

interface FilterSelectProps {
  label: string
  value: string
  options: readonly string[]
  allLabel: string
  onChange: (value: string) => void
}

/** Single-choice filter; an empty value means "no filter" and is omitted from the request. */
export function FilterSelect({ label, value, options, allLabel, onChange }: FilterSelectProps) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={event => onChange(event.target.value)}
      className="h-9 rounded-md border border-input bg-transparent px-2 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
    >
      <option value="">{allLabel}</option>
      {options.map(option => (
        <option key={option} value={option}>
          {formatEnum(option)}
        </option>
      ))}
    </select>
  )
}
