
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('@/services/attendanceApi', () => ({
  attend: vi.fn(),
  unattend: vi.fn(),
  getMyAttendance: vi.fn(),
}))

import { attend, getMyAttendance, unattend } from '@/services/attendanceApi'
import { useAttendance } from '@/hooks/useAttendance'

const mockAttend = attend as ReturnType<typeof vi.fn>
const mockUnattend = unattend as ReturnType<typeof vi.fn>
const mockGetMyAttendance = getMyAttendance as ReturnType<typeof vi.fn>

const sampleAttendance = {
  id: 1,
  userId: 'user-abc',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
}

const waitlistedAttendance = { ...sampleAttendance, status: 'WAITLISTED' as const }

afterEach(() => vi.resetAllMocks())

// Helper: render the hook and wait for initialization to complete
async function renderInitialized(
  eventId = 42,
  initialAttending = 5,
  initialStatus: 'ATTENDING' | 'WAITLISTED' | null = null,
  mountStatus: 'ATTENDING' | 'WAITLISTED' | null = null,
  initialAvailableSpots?: number | null,
) {
  mockGetMyAttendance.mockResolvedValue(mountStatus)
  const hook = renderHook(() =>
    useAttendance(eventId, initialAttending, initialStatus, initialAvailableSpots),
  )
  await waitFor(() => expect(hook.result.current.loading).toBe(false))
  return hook
}

describe('useAttendance — mount initialization', () => {
  it('starts with loading=true before the fetch resolves', () => {
    mockGetMyAttendance.mockReturnValue(new Promise(() => {})) // never resolves
    const { result } = renderHook(() => useAttendance(42, 5, null))
    expect(result.current.loading).toBe(true)
  })

  it('sets currentStatus from getMyAttendance on mount', async () => {
    mockGetMyAttendance.mockResolvedValue('ATTENDING')
    const { result } = renderHook(() => useAttendance(42, 5, null))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(mockGetMyAttendance).toHaveBeenCalledWith(42)
  })

  it('sets currentStatus to null when user has no attendance', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.currentStatus).toBeNull()
  })

  it('sets loading=false and keeps currentStatus null when fetch fails (unauthenticated)', async () => {
    mockGetMyAttendance.mockRejectedValue(new Error('401'))
    const { result } = renderHook(() => useAttendance(42, 5, null))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.currentStatus).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('does not set state after unmount', async () => {
    let resolve: (v: 'ATTENDING' | null) => void = () => {}
    mockGetMyAttendance.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result, unmount } = renderHook(() => useAttendance(42, 5, null))
    unmount()
    await act(async () => { resolve('ATTENDING') })
    expect(result.current.currentStatus).toBeNull()
  })

  it('does not allow toggle while initializing', async () => {
    mockGetMyAttendance.mockReturnValue(new Promise(() => {})) // never resolves
    const { result } = renderHook(() => useAttendance(42, 5, null))
    act(() => result.current.toggle('ATTENDING'))
    expect(mockAttend).not.toHaveBeenCalled()
  })

  it('starts with isFull=true when initialAvailableSpots is 0', () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null, 0))
    expect(result.current.isFull).toBe(true)
  })

  it('starts with isFull=false when initialAvailableSpots is > 0', () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null, 3))
    expect(result.current.isFull).toBe(false)
  })

  it('starts with isFull=false when initialAvailableSpots is not provided', () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null))
    expect(result.current.isFull).toBe(false)
  })
})

describe('useAttendance — toggle ON (set status)', () => {
  it('optimistically sets ATTENDING and increments attendingCount', async () => {
    mockAttend.mockResolvedValue(sampleAttendance)
    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(6)
    expect(result.current.loading).toBe(true)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockAttend).toHaveBeenCalledWith(42, 'ATTENDING')
  })

  it('sets WAITLISTED when server returns WAITLISTED and keeps attendingCount unchanged', async () => {
    mockAttend.mockResolvedValue(waitlistedAttendance)
    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))

    // Optimistic: ATTENDING + count incremented
    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(6)

    await waitFor(() => expect(result.current.loading).toBe(false))

    // Final: WAITLISTED, attendingCount rolled back
    expect(result.current.currentStatus).toBe('WAITLISTED')
    expect(result.current.attendingCount).toBe(5)
  })
})

describe('useAttendance — toggle OFF (unset status)', () => {
  it('optimistically clears ATTENDING and decrements attendingCount', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 5, null, 'ATTENDING')

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(4)
    expect(result.current.loading).toBe(true)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockUnattend).toHaveBeenCalledWith(42)
  })

  it('does not decrement below zero', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 0, null, 'ATTENDING')

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.attendingCount).toBe(0)

    await waitFor(() => expect(result.current.loading).toBe(false))
  })

  it('unregisters from waitlist without decrementing attendingCount', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 5, null, 'WAITLISTED')

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(5) // unchanged — WAITLISTED not in attendingCount

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockUnattend).toHaveBeenCalledWith(42)
  })
})

describe('useAttendance — optimistic rollback on error', () => {
  it('rolls back state when attend API fails', async () => {
    mockAttend.mockRejectedValue(new Error('Network error'))
    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(5)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })

  it('rolls back state when unattend API fails', async () => {
    mockUnattend.mockRejectedValue(new Error('Network error'))
    const { result } = await renderInitialized(42, 5, null, 'ATTENDING')

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(5)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })

  it('rolls back state when unattend fails for WAITLISTED user', async () => {
    mockUnattend.mockRejectedValue(new Error('Network error'))
    const { result } = await renderInitialized(42, 5, null, 'WAITLISTED')

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBe('WAITLISTED')
    expect(result.current.attendingCount).toBe(5)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })
})

describe('useAttendance — error mapping', () => {
  // The 409 → isFull mapping was removed: capacity-reached is now a 200
  // with status=WAITLISTED. 409 is reserved for registration_closed and
  // other server errors, none of which should mutate isFull.
  it('rolls back state and surfaces a generic message on bare 409 (no body)', async () => {
    const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
    mockAttend.mockRejectedValue(axiosError)

    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.isFull).toBe(false)
    expect(result.current.error).toBe('Une erreur est survenue.')
    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(5)
  })

  it('does not set isFull on non-409 errors', async () => {
    const axiosError = new axios.AxiosError('Server Error', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 500 }, writable: false })
    mockAttend.mockRejectedValue(axiosError)

    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.isFull).toBe(false)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })
})

describe('useAttendance — onAfterSuccess callback', () => {
  it('calls onAfterSuccess once after a successful attend (ATTENDING)', async () => {
    mockAttend.mockResolvedValue(sampleAttendance)
    const onAfterSuccess = vi.fn()
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() =>
      useAttendance(42, 5, null, undefined, { onAfterSuccess }),
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(onAfterSuccess).toHaveBeenCalledTimes(1)
  })

  it('calls onAfterSuccess once after a successful attend (WAITLISTED)', async () => {
    mockAttend.mockResolvedValue(waitlistedAttendance)
    const onAfterSuccess = vi.fn()
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() =>
      useAttendance(42, 5, null, undefined, { onAfterSuccess }),
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(onAfterSuccess).toHaveBeenCalledTimes(1)
  })

  it('calls onAfterSuccess once after a successful unattend', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const onAfterSuccess = vi.fn()
    mockGetMyAttendance.mockResolvedValue('ATTENDING')
    const { result } = renderHook(() =>
      useAttendance(42, 5, null, undefined, { onAfterSuccess }),
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(onAfterSuccess).toHaveBeenCalledTimes(1)
  })

  it('does NOT call onAfterSuccess when attend fails', async () => {
    mockAttend.mockRejectedValue(new Error('boom'))
    const onAfterSuccess = vi.fn()
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() =>
      useAttendance(42, 5, null, undefined, { onAfterSuccess }),
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(onAfterSuccess).not.toHaveBeenCalled()
  })

  it('does NOT call onAfterSuccess when unattend fails', async () => {
    mockUnattend.mockRejectedValue(new Error('boom'))
    const onAfterSuccess = vi.fn()
    mockGetMyAttendance.mockResolvedValue('ATTENDING')
    const { result } = renderHook(() =>
      useAttendance(42, 5, null, undefined, { onAfterSuccess }),
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(onAfterSuccess).not.toHaveBeenCalled()
  })
})

describe('useAttendance — leave waitlist', () => {
  it('WAITLISTED user toggles → calls unattend, not attend', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 5, null, 'WAITLISTED', 0)

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.currentStatus).toBeNull()
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockUnattend).toHaveBeenCalledWith(42)
    expect(mockAttend).not.toHaveBeenCalled()
  })
})

describe('useAttendance — batched optimistic state (no flicker)', () => {
  it('ATTENDING user unattending a full event optimistically clears isFull and currentStatus together', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 5, null, 'ATTENDING', 0)
    expect(result.current.isFull).toBe(true)

    act(() => result.current.toggle('ATTENDING'))

    // Both fields update in one render — no orange "waitlist" intermediate state.
    expect(result.current.currentStatus).toBeNull()
    expect(result.current.isFull).toBe(false)

    await waitFor(() => expect(result.current.loading).toBe(false))
  })

  it('WAITLISTED user leaving waitlist keeps isFull (no slot is freed)', async () => {
    mockUnattend.mockResolvedValue(undefined)
    const { result } = await renderInitialized(42, 5, null, 'WAITLISTED', 0)
    expect(result.current.isFull).toBe(true)

    act(() => result.current.toggle('ATTENDING'))

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.isFull).toBe(true)

    await waitFor(() => expect(result.current.loading).toBe(false))
  })
})

describe('useAttendance — debounce', () => {
  it('keeps loading=true for ~500ms after a fast-resolving API success', async () => {
    vi.useFakeTimers()
    try {
      mockGetMyAttendance.mockResolvedValue(null)
      mockAttend.mockResolvedValue(sampleAttendance)
      const { result } = renderHook(() => useAttendance(42, 5, null))

      // Drain the initial mount fetch.
      await vi.waitFor(() => expect(result.current.loading).toBe(false))

      act(() => result.current.toggle('ATTENDING'))
      expect(result.current.loading).toBe(true)

      // Allow the API microtask to settle.
      await act(async () => { await Promise.resolve() })

      // API has resolved but debounce window keeps loading true.
      expect(result.current.loading).toBe(true)

      // Advance past the 500ms debounce window.
      await act(async () => { vi.advanceTimersByTime(500) })

      expect(result.current.loading).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('clears the debounce timer on unmount (no setState after unmount)', async () => {
    vi.useFakeTimers()
    try {
      mockGetMyAttendance.mockResolvedValue(null)
      mockAttend.mockResolvedValue(sampleAttendance)
      const { result, unmount } = renderHook(() => useAttendance(42, 5, null))
      await vi.waitFor(() => expect(result.current.loading).toBe(false))

      act(() => result.current.toggle('ATTENDING'))
      unmount()

      // Should not throw / log warnings. Timers are cleared.
      await act(async () => { vi.advanceTimersByTime(1000) })
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('useAttendance — guards', () => {
  it('ignores toggle call while loading is true (post-init toggle in flight)', async () => {
    let resolve: (v: unknown) => void = () => {}
    mockAttend.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    expect(result.current.loading).toBe(true)

    act(() => result.current.toggle('ATTENDING'))
    expect(result.current.currentStatus).toBe('ATTENDING')

    await act(async () => { resolve(sampleAttendance) })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockAttend).toHaveBeenCalledTimes(1)
  })

  it('preserves isFull across a 409 then a successful ATTENDING retry', async () => {
    // 409 must NOT touch isFull; the success retry derives it from the response status.
    const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
    mockAttend.mockRejectedValueOnce(axiosError).mockResolvedValue(sampleAttendance)

    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(false)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(false)
  })
})
