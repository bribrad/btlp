import { describe, it, expect } from 'vitest'
import {
  formatCurrency,
  formatDateTime,
  formatEnum,
  formatText,
  formatWindow,
  shortId,
} from '@/lib/format'

describe('format helpers', () => {
  it('renders an em dash for missing values', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatCurrency(null, 'USD')).toBe('—')
    expect(formatText('   ')).toBe('—')
    expect(formatWindow(null, null)).toBe('—')
  })

  it('renders an em dash for unparseable timestamps', () => {
    expect(formatDateTime('not-a-date')).toBe('—')
  })

  it('formats one-sided windows', () => {
    expect(formatWindow('2026-03-03T14:00:00Z', null)).toMatch(/^From /)
    expect(formatWindow(null, '2026-03-03T14:00:00Z')).toMatch(/^Until /)
  })

  it('joins both ends of a window with a dash', () => {
    expect(formatWindow('2026-03-03T14:00:00Z', '2026-03-05T14:00:00Z')).toContain('–')
  })

  it('formats currency and falls back on an invalid code', () => {
    expect(formatCurrency(1250.5, 'USD')).toContain('1,250.50')
    expect(formatCurrency(1250.5, 'NOTACODE')).toBe('1250.50 NOTACODE')
  })

  it('humanizes API enums', () => {
    expect(formatEnum('IN_TRANSIT')).toBe('In transit')
    expect(formatEnum('PICKUP')).toBe('Pickup')
    expect(formatEnum(null)).toBe('—')
  })

  it('shortens ids to eight characters', () => {
    expect(shortId('c8852758-30cf-40c3-a28f-db8c6f24c076')).toBe('c8852758')
  })
})
