import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { downloadAttachment } from '@/utils/downloadAttachment'

const originalFetch = globalThis.fetch
const originalCreateObjectURL = globalThis.URL.createObjectURL
const originalRevokeObjectURL = globalThis.URL.revokeObjectURL
const originalOpen = globalThis.open

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  globalThis.fetch = originalFetch
  globalThis.URL.createObjectURL = originalCreateObjectURL
  globalThis.URL.revokeObjectURL = originalRevokeObjectURL
  globalThis.open = originalOpen
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('downloadAttachment', () => {
  it('fetches the URL, synthesizes an <a download> click, and revokes the object URL', async () => {
    const blob = new Blob(['payload'], { type: 'application/pdf' })
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, blob: () => Promise.resolve(blob) }) as typeof fetch
    globalThis.URL.createObjectURL = vi.fn().mockReturnValue('blob:fake-url')
    const revokeSpy = vi.fn()
    globalThis.URL.revokeObjectURL = revokeSpy
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click')

    await downloadAttachment('https://s3.example.com/file.pdf', 'rapport.pdf')

    expect(globalThis.fetch).toHaveBeenCalledWith('https://s3.example.com/file.pdf', { mode: 'cors', credentials: 'omit' })
    expect(clickSpy).toHaveBeenCalledTimes(1)
    // The anchor is removed from the DOM right after clicking, so we
    // verify URL.createObjectURL was called instead.
    expect(globalThis.URL.createObjectURL).toHaveBeenCalledWith(blob)
    vi.runAllTimers()
    expect(revokeSpy).toHaveBeenCalledWith('blob:fake-url')
  })

  it('falls back to window.open when fetch throws (CORS / network)', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('CORS blocked')) as typeof fetch
    const openSpy = vi.fn()
    globalThis.open = openSpy as unknown as typeof globalThis.open

    await downloadAttachment('https://s3.example.com/file.pdf', 'rapport.pdf')

    expect(openSpy).toHaveBeenCalledWith('https://s3.example.com/file.pdf', '_blank', 'noopener,noreferrer')
  })

  it('falls back to window.open when the response is non-2xx', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: false, status: 404 }) as typeof fetch
    const openSpy = vi.fn()
    globalThis.open = openSpy as unknown as typeof globalThis.open

    await downloadAttachment('https://s3.example.com/missing.pdf', 'rapport.pdf')

    expect(openSpy).toHaveBeenCalledWith('https://s3.example.com/missing.pdf', '_blank', 'noopener,noreferrer')
  })

  it('falls back to window.open when blob() rejects', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      blob: () => Promise.reject(new Error('boom')),
    }) as typeof fetch
    const openSpy = vi.fn()
    globalThis.open = openSpy as unknown as typeof globalThis.open

    await downloadAttachment('https://s3.example.com/file.pdf', 'rapport.pdf')

    expect(openSpy).toHaveBeenCalledTimes(1)
  })
})
