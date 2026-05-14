import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDebounce } from '@/hooks/useDebounce'

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns the initial value immediately', () => {
    const { result } = renderHook(({ v }) => useDebounce(v, 300), {
      initialProps: { v: 'a' },
    })
    expect(result.current).toBe('a')
  })

  it('debounces subsequent value changes by `delay` ms', () => {
    const { result, rerender } = renderHook(({ v }) => useDebounce(v, 300), {
      initialProps: { v: 'a' },
    })

    rerender({ v: 'b' })
    expect(result.current).toBe('a') // still old value before delay elapses

    act(() => {
      vi.advanceTimersByTime(150)
    })
    expect(result.current).toBe('a')

    act(() => {
      vi.advanceTimersByTime(200)
    })
    expect(result.current).toBe('b')
  })

  it('coalesces rapid changes into a single update', () => {
    const { result, rerender } = renderHook(({ v }) => useDebounce(v, 300), {
      initialProps: { v: 'a' },
    })

    rerender({ v: 'b' })
    act(() => {
      vi.advanceTimersByTime(100)
    })
    rerender({ v: 'c' })
    act(() => {
      vi.advanceTimersByTime(100)
    })
    rerender({ v: 'd' })

    expect(result.current).toBe('a') // none of the intermediates landed

    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(result.current).toBe('d')
  })

  it('cleans up the timer on unmount (no stale setState warning)', () => {
    const { unmount, rerender } = renderHook(({ v }) => useDebounce(v, 300), {
      initialProps: { v: 'a' },
    })

    rerender({ v: 'b' })
    unmount()

    // Advancing timers after unmount must not throw or warn.
    act(() => {
      vi.advanceTimersByTime(1000)
    })
  })
})
