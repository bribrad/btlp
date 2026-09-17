/** Formatting helpers for rendering API values. All inputs may be null — the API allows it. */

const EM_DASH = '—'

const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

const timeFormat = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' })

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parse(iso)
  return date ? dateTimeFormat.format(date) : EM_DASH
}

export function formatDate(iso: string | null | undefined): string {
  const date = parse(iso)
  return date ? dateFormat.format(date) : EM_DASH
}

/**
 * Renders a scheduling window as a single cell. Same-day windows collapse to
 * "Mar 3, 2026, 8:00 AM – 2:00 PM"; an open-ended window shows just the side it has.
 */
export function formatWindow(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  const from = parse(start)
  const to = parse(end)
  if (!from && !to) return EM_DASH
  if (from && !to) return `From ${dateTimeFormat.format(from)}`
  if (!from && to) return `Until ${dateTimeFormat.format(to)}`
  if (from && to) {
    return from.toDateString() === to.toDateString()
      ? `${dateTimeFormat.format(from)} – ${timeFormat.format(to)}`
      : `${dateTimeFormat.format(from)} – ${dateTimeFormat.format(to)}`
  }
  return EM_DASH
}

export function formatCurrency(
  amount: number | null | undefined,
  currency: string | null | undefined,
): string {
  if (amount === null || amount === undefined) return EM_DASH
  const code = currency ?? 'USD'
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).format(amount)
  } catch {
    // Unknown/invalid currency code — show the raw amount rather than throwing.
    return `${amount.toFixed(2)} ${code}`
  }
}

/** Turns an API enum such as IN_TRANSIT into "In transit" for display. */
export function formatEnum(value: string | null | undefined): string {
  if (!value) return EM_DASH
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function formatText(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : EM_DASH
}

/** Shortens a UUID for table cells, where the full value is noise. */
export function shortId(id: string): string {
  return id.slice(0, 8)
}
