import { afterEach, describe, expect, it, vi } from 'vitest'
import { globalNavigate, setGlobalNavigate } from '@/utils/navigation'

afterEach(() => {
  vi.restoreAllMocks()
  setGlobalNavigate(() => {})
})

describe('navigation singleton', () => {
  it('calls the registered navigate function', () => {
    const fn = vi.fn()
    setGlobalNavigate(fn)
    globalNavigate('/403')
    expect(fn).toHaveBeenCalledWith('/403')
  })

  it('replaces the navigate function when set again', () => {
    const first = vi.fn()
    const second = vi.fn()
    setGlobalNavigate(first)
    setGlobalNavigate(second)
    globalNavigate('/test')
    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledWith('/test')
  })

  it('falls back to window.location.href when no fn is registered', async () => {
    // Fresh module = _navigate starts as null
    vi.resetModules()
    const { globalNavigate: navigateFresh } = await import('@/utils/navigation')

    let captured = ''
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      set href(v: string) { captured = v },
    } as unknown as Location)

    navigateFresh('/login')
    expect(captured).toBe('/login')
  })
})
