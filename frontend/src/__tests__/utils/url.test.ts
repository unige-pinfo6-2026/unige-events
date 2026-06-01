import { describe, expect, it } from 'vitest'
import { safeHttpUrl } from '@/utils/url'

describe('safeHttpUrl', () => {
  it('accepts an http URL', () => {
    expect(safeHttpUrl('http://example.com/a')).toBe('http://example.com/a')
  })

  it('accepts an https URL', () => {
    expect(safeHttpUrl('https://example.com/path?q=1')).toBe('https://example.com/path?q=1')
  })

  it.each([
    'javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'ftp://example.com/file',
    'mailto:foo@example.com',
    'file:///etc/passwd',
  ])('rejects the non-http(s) scheme %s', (value) => {
    expect(safeHttpUrl(value)).toBeNull()
  })

  it.each([
    'example.com',
    'not a url',
    '',
    'http://',
  ])('rejects the unparseable value %s', (value) => {
    expect(safeHttpUrl(value)).toBeNull()
  })
})
