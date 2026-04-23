// @vitest-environment jsdom

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
    // Resolving after unmount should not throw or update state
    await act(async () => { resolve('ATTENDING') })
    expect(result.current.currentStatus).toBeNull()
  })

  it('does not allow toggle while initializing', async () => {
    mockGetMyAttendance.mockReturnValue(new Promise(() => {})) // never resolves
    const { result } = renderHook(() => useAttendance(42, 5, null))
    act(() => result.current.toggle('ATTENDING'))
    expect(mockAttend).not.toHaveBeenCalled()
  })

  it('starts with isFull=true when initialAvailableSpots is 0', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null, 0))
    expect(result.current.isFull).toBe(true)
  })

  it('starts with isFull=false when initialAvailableSpots is > 0', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(42, 5, null, 3))
    expect(result.current.isFull).toBe(false)
  })

  it('starts with isFull=false when initialAvailableSpots is not provided', async () => {
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

    // Optimistic state: ATTENDING + count incremented
    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(6)

    await waitFor(() => expect(result.current.loading).toBe(false))

    // Final state: WAITLISTED, attendingCount rolled back
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

describe('useAttendance — 409 → isFull flag', () => {
  it('sets isFull on 409 and rolls back state', async () => {
    const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
    mockAttend.mockRejectedValue(axiosError)

    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.isFull).toBe(true)
    expect(result.current.error).toBeNull()
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

describe('useAttendance — guards', () => {
  it('ignores toggle call while loading is true (post-init toggle in flight)', async () => {
    let resolve: (v: unknown) => void = () => {}
    mockAttend.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    expect(result.current.loading).toBe(true)

    // A second toggle while loading should be a no-op
    act(() => result.current.toggle('ATTENDING'))
    expect(result.current.currentStatus).toBe('ATTENDING')

    await act(async () => { resolve(sampleAttendance) })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockAttend).toHaveBeenCalledTimes(1)
  })

  it('clears isFull when toggling again after a 409', async () => {
    const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
    mockAttend.mockRejectedValueOnce(axiosError).mockResolvedValue(sampleAttendance)

    const { result } = await renderInitialized(42, 5, null, null)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(true)

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(false)
  })
})
