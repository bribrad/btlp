/**
 * Conversions between the API's ISO-8601 offset timestamps and the `YYYY-MM-DDTHH:mm`
 * strings that `<input type="datetime-local">` reads and writes (always local time).
 */

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** ISO timestamp → datetime-local input value. Returns '' for null/unparseable input. */
export function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** datetime-local input value → ISO timestamp with offset. Returns null for ''. */
export function localInputToIso(value: string): string | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

/** Compares two datetime-local values; null when either is blank. */
export function compareLocalInputs(a: string, b: string): number | null {
  if (!a || !b) return null
  const left = new Date(a).getTime()
  const right = new Date(b).getTime()
  if (Number.isNaN(left) || Number.isNaN(right)) return null
  return left - right
}
