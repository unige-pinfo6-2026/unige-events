// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useEventStats } from '@/hooks/useEventStats'

vi.mock('@/services/statsApi', () => ({
  getEventStats: vi.fn(),
}))

import { getEventStats } from '@/services/statsApi'
const mockGetEventStats = getEventStats as ReturnType<typeof vi.fn>

const mockStats = { views: 100, attendingCount: 20, checkInCount: 5 }

afterEach(() => {
  vi.resetAllMocks()
  vi.useRealTimers()
})

describe('useEventStats', () => {
  it('does not fetch when eventId is null', () => {
    const { result } = renderHook(() => useEventStats(null))
    expect(result.current.loading).toBe(false)
    expect(result.current.stats).toBeNull()
    expect(result.current.error).toBeNull()
    expect(mockGetEventStats).not.toHaveBeenCalled()
  })

  it('starts in loading state when eventId is provided', () => {
    mockGetEventStats.mockResolvedValue(mockStats)
    const { result } = renderHook(() => useEventStats(1))
    expect(result.current.loading).toBe(true)
  })

  it('resolves stats on success', async () => {
    mockGetEventStats.mockResolvedValue(mockStats)
    const { result } = renderHook(() => useEventStats(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.stats).toEqual(mockStats)
    expect(result.current.error).toBeNull()
  })

  it('sets error on fetch failure', async () => {
    mockGetEventStats.mockRejectedValue(new Error('Server error'))
    const { result } = renderHook(() => useEventStats(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger les statistiques.')
    expect(result.current.stats).toBeNull()
  })

  it('auto-refreshes after 60 seconds', async () => {
    vi.useFakeTimers()
    mockGetEventStats.mockResolvedValue(mockStats)
    renderHook(() => useEventStats(1))

    // flush initial fetch (direct async call, not a timer)
    await act(async () => {})
    expect(mockGetEventStats).toHaveBeenCalledTimes(1)

    // advance exactly 60s — fires the interval once, act awaits the resulting async fetch
    await act(async () => {
      vi.advanceTimersByTime(60_000)
    })
    expect(mockGetEventStats).toHaveBeenCalledTimes(2)
  })

  it('clears interval on unmount', async () => {
    vi.useFakeTimers()
    mockGetEventStats.mockResolvedValue(mockStats)
    const { unmount } = renderHook(() => useEventStats(1))

    await act(async () => {})
    expect(mockGetEventStats).toHaveBeenCalledTimes(1)

    unmount()

    await act(async () => { vi.advanceTimersByTime(60_000) })
    expect(mockGetEventStats).toHaveBeenCalledTimes(1)
  })

  it('resets state when eventId changes to null', async () => {
    mockGetEventStats.mockResolvedValue(mockStats)
    const { result, rerender } = renderHook(({ id }) => useEventStats(id), {
      initialProps: { id: 1 as number | null },
    })
    await waitFor(() => expect(result.current.stats).toEqual(mockStats))

    rerender({ id: null })
    expect(result.current.stats).toBeNull()
    expect(result.current.loading).toBe(false)
  })
})
