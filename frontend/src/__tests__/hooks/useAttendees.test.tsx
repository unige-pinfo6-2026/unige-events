// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { Attendance } from '@/types/attendance'

vi.mock('@/services/attendeesApi', () => ({
  getEventAttendees: vi.fn(),
  getPublicUser: vi.fn(),
}))

vi.mock('axios', () => ({
  default: { isAxiosError: vi.fn() },
  isAxiosError: vi.fn(),
}))

import axios from 'axios'
import { getEventAttendees, getPublicUser } from '@/services/attendeesApi'
import { useAttendees } from '@/hooks/useAttendees'

const mockGetEventAttendees = getEventAttendees as ReturnType<typeof vi.fn>
const mockGetPublicUser = getPublicUser as ReturnType<typeof vi.fn>
const mockIsAxiosError = axios.isAxiosError as unknown as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

const a1 = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Alice',
  avatarUrl: null,
}

const a2 = {
  id: 2,
  userId: 'user-2',
  eventId: 42,
  status: 'WAITLISTED' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Bob',
  avatarUrl: null,
}

const profile1 = {
  id: 'user-1',
  displayName: 'Alice',
}

const profile2 = {
  id: 'user-2',
  displayName: 'Bob',
}

describe('useAttendees', () => {
  it('does not fetch when enabled is false', async () => {
    const { result } = renderHook(() => useAttendees(42, { enabled: false }))

    await new Promise((r) => setTimeout(r, 10))

    expect(mockGetEventAttendees).not.toHaveBeenCalled()
    expect(result.current.attendees).toEqual([])
    expect(result.current.isLoading).toBe(false)
  })

  it('fetches initial page and merges profiles', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([a1, a2])
    mockGetPublicUser.mockImplementation((id: string) =>
      Promise.resolve(id === 'user-1' ? profile1 : profile2),
    )

    const { result } = renderHook(() => useAttendees(42, { pageSize: 20 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.attendees).toEqual([
      { attendance: a1, profile: profile1 },
      { attendance: a2, profile: profile2 },
    ])
    expect(result.current.isForbidden).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('handles a private profile (null) without breaking the batch', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([a1, a2])
    mockGetPublicUser.mockImplementation((id: string) =>
      id === 'user-1' ? Promise.resolve(null) : Promise.resolve(profile2),
    )

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.attendees).toEqual([
      { attendance: a1, profile: null },
      { attendance: a2, profile: profile2 },
    ])
  })

  it('falls back to null when getPublicUser rejects', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([a1])
    mockGetPublicUser.mockRejectedValueOnce(new Error('boom'))

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.attendees).toEqual([{ attendance: a1, profile: null }])
  })

  it('flags isForbidden when initial fetch is 403', async () => {
    const err = { response: { status: 403 } }
    mockGetEventAttendees.mockRejectedValueOnce(err)
    mockIsAxiosError.mockReturnValue(true)

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isForbidden).toBe(true)
    expect(result.current.attendees).toEqual([])
    expect(result.current.hasMore).toBe(false)
    expect(mockGetPublicUser).not.toHaveBeenCalled()
  })

  it('sets error when initial fetch fails with non-403', async () => {
    mockGetEventAttendees.mockRejectedValueOnce(new Error('network'))
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.error?.message).toBe('network')
    expect(result.current.isForbidden).toBe(false)
  })

  it('wraps non-Error throwables in an Error', async () => {
    mockGetEventAttendees.mockRejectedValueOnce('string-error')
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('loadMore appends pages and dedupes by attendance.id', async () => {
    mockGetEventAttendees
      .mockResolvedValueOnce([a1, a2])
      .mockResolvedValueOnce([a2, { ...a1, id: 3, userId: 'user-3' }])
    mockGetPublicUser.mockResolvedValue(null)

    const { result } = renderHook(() => useAttendees(42, { pageSize: 2 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.hasMore).toBe(true)

    act(() => { result.current.loadMore() })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.attendees.map((x) => x.attendance.id)).toEqual([1, 2, 3])
  })

  it('marks hasMore false when last page is short', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([a1])
    mockGetPublicUser.mockResolvedValue(null)

    const { result } = renderHook(() => useAttendees(42, { pageSize: 5 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.hasMore).toBe(false)
  })

  it('loadMore is a no-op when hasMore is false', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([a1])
    mockGetPublicUser.mockResolvedValue(null)

    const { result } = renderHook(() => useAttendees(42, { pageSize: 5 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    mockGetEventAttendees.mockClear()

    act(() => { result.current.loadMore() })

    expect(mockGetEventAttendees).not.toHaveBeenCalled()
  })

  it('loadMore is a no-op when isForbidden', async () => {
    mockGetEventAttendees.mockRejectedValueOnce({ response: { status: 403 } })
    mockIsAxiosError.mockReturnValue(true)

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isForbidden).toBe(true))
    mockGetEventAttendees.mockClear()

    act(() => { result.current.loadMore() })

    expect(mockGetEventAttendees).not.toHaveBeenCalled()
  })

  it('refetch resets state and re-fetches page 0 after an error', async () => {
    mockGetEventAttendees
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce([a1])
    mockGetPublicUser.mockResolvedValue(profile1)
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.error).not.toBeNull())
    expect(result.current.attendees).toEqual([])

    act(() => { result.current.refetch() })

    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.error).toBeNull()
    expect(result.current.attendees[0].attendance.id).toBe(1)
  })

  it('discards a stale response when eventId changes mid-flight', async () => {
    let resolveFirst: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees
      .mockImplementationOnce(() => new Promise<Attendance[]>((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce([{ ...a1, id: 99, eventId: 7, userId: 'user-7' }])
    mockGetPublicUser.mockResolvedValue(null)

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useAttendees(id),
      { initialProps: { id: 42 } },
    )

    rerender({ id: 7 })

    // Resolve the stale request after the rerender — its result must be ignored.
    act(() => { resolveFirst?.([a1, a2]) })

    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.attendees[0].attendance.id).toBe(99)
  })

  it('does not call setState after unmount when the request resolves later', async () => {
    let resolveFn: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees.mockImplementationOnce(
      () => new Promise<Attendance[]>((resolve) => { resolveFn = resolve }),
    )
    mockGetPublicUser.mockResolvedValue(null)

    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { unmount } = renderHook(() => useAttendees(42))

    unmount()
    act(() => { resolveFn?.([a1, a2]) })
    await new Promise((r) => setTimeout(r, 20))

    // No "state update on unmounted" warnings raised by React.
    expect(errorSpy).not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('eventId change releases the loadingRef so the new initial fetch runs', async () => {
    let resolveFirst: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees
      .mockImplementationOnce(() => new Promise<Attendance[]>((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce([{ ...a1, id: 50, eventId: 100 }])
    mockGetPublicUser.mockResolvedValue(null)

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useAttendees(id),
      { initialProps: { id: 42 } },
    )

    // Switch to a new event while the first fetch is still pending.
    rerender({ id: 100 })

    // The hook must have called the API twice: once for 42, once for 100.
    expect(mockGetEventAttendees).toHaveBeenCalledTimes(2)
    expect(mockGetEventAttendees).toHaveBeenLastCalledWith(100, { page: 0, size: 20 })

    // Now resolve the stale first request — should not surface in state.
    act(() => { resolveFirst?.([a1]) })
    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.attendees[0].attendance.id).toBe(50)
  })
})
