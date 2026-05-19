import { describe, expect, it } from 'vitest'
import { formatFileSize } from '@/utils/formatFileSize'

describe('formatFileSize', () => {
  it('returns "0 B" for zero, negative or invalid inputs', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(-1)).toBe('0 B')
    expect(formatFileSize(Number.NaN)).toBe('0 B')
    expect(formatFileSize(Number.POSITIVE_INFINITY)).toBe('0 B')
  })

  it('formats byte ranges (< 1 KiB) as integer bytes', () => {
    expect(formatFileSize(1)).toBe('1 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1023)).toBe('1023 B')
  })

  it('flips to KB at exactly 1024 bytes', () => {
    expect(formatFileSize(1024)).toBe('1.0 KB')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(1024 * 1023)).toBe('1023.0 KB')
  })

  it('flips to MB at exactly 1 MiB', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB')
    expect(formatFileSize(1024 * 1024 * 2.5)).toBe('2.5 MB')
  })

  it('handles the backend max (10 MiB) and beyond without overflow', () => {
    expect(formatFileSize(10 * 1024 * 1024)).toBe('10.0 MB')
    expect(formatFileSize(1024 * 1024 * 1024)).toBe('1024.0 MB')
  })
})
