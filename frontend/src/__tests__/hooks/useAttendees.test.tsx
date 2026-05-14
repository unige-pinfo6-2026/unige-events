// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { Attendance } from '@/types/attendance'

vi.mock('@/services/attendeesApi', () => ({
  getEventAttendees: vi.fn(),
}))

import { getEventAttendees } from '@/services/attendeesApi'
import { useAttendees } from '@/hooks/useAttendees'

const mockGetEventAttendees = getEventAttendees as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

// SCRUM-S7 — public-profile row keeps real identity for every authenticated caller.
const publicRow: Attendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Alice',
  avatarUrl: null,
}

// SCRUM-S7 — private-profile row is anonymized server-side for non-organizers
// (userId + displayName + avatarUrl all null). The hook must surface it as-is.
const anonymizedRow: Attendance = {
  id: 2,
  userId: null,
  eventId: 42,
  status: 'WAITLISTED',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
}

describe('useAttendees', () => {
  it('does not fetch when enabled is false (unauthenticated viewers)', async () => {
    const { result } = renderHook(() => useAttendees(42, { enabled: false }))

    await new Promise((r) => setTimeout(r, 10))

    expect(mockGetEventAttendees).not.toHaveBeenCalled()
    expect(result.current.attendees).toEqual([])
    expect(result.current.isLoading).toBe(false)
  })

  it('fetches initial page and surfaces the DTO rows directly (no N+1)', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([publicRow, anonymizedRow])

    const { result } = renderHook(() => useAttendees(42, { pageSize: 20 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    // The hook now surfaces the AttendanceDTO rows directly — no wrapping in
    // { attendance, profile } and no secondary /users/{id} lookup.
    expect(result.current.attendees).toEqual([publicRow, anonymizedRow])
    expect(result.current.error).toBeNull()
    // Exactly one HTTP call per page — proves N+1 is gone.
    expect(mockGetEventAttendees).toHaveBeenCalledTimes(1)
  })

  it('surfaces anonymized rows as-is (private profiles seen by non-organizers)', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([anonymizedRow])

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.attendees[0].displayName).toBeNull()
    expect(result.current.attendees[0].userId).toBeNull()
  })

  it('sets error when initial fetch fails', async () => {
    mockGetEventAttendees.mockRejectedValueOnce(new Error('network'))

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.error?.message).toBe('network')
  })

  it('wraps non-Error throwables in an Error', async () => {
    mockGetEventAttendees.mockRejectedValueOnce('string-error')

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('loadMore appends pages and dedupes by attendance.id', async () => {
    mockGetEventAttendees
      .mockResolvedValueOnce([publicRow, anonymizedRow])
      .mockResolvedValueOnce([anonymizedRow, { ...publicRow, id: 3, userId: 'user-3' }])

    const { result } = renderHook(() => useAttendees(42, { pageSize: 2 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.hasMore).toBe(true)

    act(() => { result.current.loadMore() })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.attendees.map((x) => x.id)).toEqual([1, 2, 3])
  })

  it('marks hasMore false when last page is short', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([publicRow])

    const { result } = renderHook(() => useAttendees(42, { pageSize: 5 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.hasMore).toBe(false)
  })

  it('loadMore is a no-op when hasMore is false', async () => {
    mockGetEventAttendees.mockResolvedValueOnce([publicRow])

    const { result } = renderHook(() => useAttendees(42, { pageSize: 5 }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    mockGetEventAttendees.mockClear()

    act(() => { result.current.loadMore() })

    expect(mockGetEventAttendees).not.toHaveBeenCalled()
  })

  it('refetch resets state and re-fetches page 0 after an error', async () => {
    mockGetEventAttendees
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce([publicRow])

    const { result } = renderHook(() => useAttendees(42))

    await waitFor(() => expect(result.current.error).not.toBeNull())
    expect(result.current.attendees).toEqual([])

    act(() => { result.current.refetch() })

    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.error).toBeNull()
    expect(result.current.attendees[0].id).toBe(1)
  })

  it('discards a stale response when eventId changes mid-flight', async () => {
    let resolveFirst: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees
      .mockImplementationOnce(() => new Promise<Attendance[]>((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce([{ ...publicRow, id: 99, eventId: 7, userId: 'user-7' }])

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useAttendees(id),
      { initialProps: { id: 42 } },
    )

    rerender({ id: 7 })

    // Resolve the stale request after the rerender — its result must be ignored.
    act(() => { resolveFirst?.([publicRow, anonymizedRow]) })

    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.attendees[0].id).toBe(99)
  })

  it('does not call setState after unmount when the request resolves later', async () => {
    let resolveFn: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees.mockImplementationOnce(
      () => new Promise<Attendance[]>((resolve) => { resolveFn = resolve }),
    )

    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { unmount } = renderHook(() => useAttendees(42))

    unmount()
    act(() => { resolveFn?.([publicRow, anonymizedRow]) })
    await new Promise((r) => setTimeout(r, 20))

    expect(errorSpy).not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('eventId change releases the loadingRef so the new initial fetch runs', async () => {
    let resolveFirst: ((rows: Attendance[]) => void) | undefined
    mockGetEventAttendees
      .mockImplementationOnce(() => new Promise<Attendance[]>((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce([{ ...publicRow, id: 50, eventId: 100 }])

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useAttendees(id),
      { initialProps: { id: 42 } },
    )

    rerender({ id: 100 })

    expect(mockGetEventAttendees).toHaveBeenCalledTimes(2)
    expect(mockGetEventAttendees).toHaveBeenLastCalledWith(100, { page: 0, size: 20 })

    act(() => { resolveFirst?.([publicRow]) })
    await waitFor(() => expect(result.current.attendees.length).toBe(1))
    expect(result.current.attendees[0].id).toBe(50)
  })
})
