
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useEvent } from '@/hooks/useEvent'

vi.mock('@/services/eventApi', () => ({
  getById: vi.fn(),
  getAll: vi.fn(),
  deleteEvent: vi.fn(),
}))

import { getById } from '@/services/eventApi'

const mockGetById = getById as ReturnType<typeof vi.fn>

const mockEvent = {
  id: 1,
  title: 'Test Event',
  location: 'Test Location',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
}

afterEach(() => vi.resetAllMocks())

describe('useEvent', () => {
  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useEvent(null))
    expect(result.current.event).toBeNull()
    expect(mockGetById).not.toHaveBeenCalled()
  })

  it('fetches event by id', async () => {
    mockGetById.mockResolvedValue(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.event).toEqual(mockEvent)
    expect(result.current.error).toBeNull()
  })

  it('sets error when fetch fails', async () => {
    mockGetById.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger cet événement.')
    expect(result.current.event).toBeNull()
  })

  it('starts in loading state', () => {
    mockGetById.mockResolvedValue(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    expect(result.current.loading).toBe(true)
  })

  it('exposes a refetch() that triggers a new fetch and updates event', async () => {
    const updated = { ...mockEvent, title: 'Updated' }
    mockGetById.mockResolvedValueOnce(mockEvent).mockResolvedValueOnce(updated)
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.event?.title).toBe('Test Event'))

    await act(async () => { await result.current.refetch() })

    expect(result.current.event?.title).toBe('Updated')
    expect(mockGetById).toHaveBeenCalledTimes(2)
  })

  it('refetch() is a no-op when id is null', async () => {
    const { result } = renderHook(() => useEvent(null))
    await act(async () => { await result.current.refetch() })
    expect(mockGetById).not.toHaveBeenCalled()
  })

  it('refetch() flips isRefetching but not isInitialLoad after the first load', async () => {
    let resolveSecond: (v: typeof mockEvent) => void = () => {}
    mockGetById
      .mockResolvedValueOnce(mockEvent)
      .mockReturnValueOnce(new Promise<typeof mockEvent>((r) => { resolveSecond = r }))

    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.isInitialLoad).toBe(false))
    expect(result.current.event).toEqual(mockEvent)

    act(() => { void result.current.refetch() })

    expect(result.current.isRefetching).toBe(true)
    expect(result.current.isInitialLoad).toBe(false)
    expect(result.current.event).toEqual(mockEvent)

    await act(async () => { resolveSecond({ ...mockEvent, title: 'Refreshed' }) })
    await waitFor(() => expect(result.current.isRefetching).toBe(false))
    expect(result.current.event?.title).toBe('Refreshed')
  })

  it('isInitialLoad is true on first fetch and flips to false after first response', async () => {
    let resolve: (v: typeof mockEvent) => void = () => {}
    mockGetById.mockReturnValueOnce(new Promise<typeof mockEvent>((r) => { resolve = r }))
    const { result } = renderHook(() => useEvent(1))

    expect(result.current.isInitialLoad).toBe(true)
    expect(result.current.isRefetching).toBe(false)

    await act(async () => { resolve(mockEvent) })
    await waitFor(() => expect(result.current.isInitialLoad).toBe(false))
    expect(result.current.event).toEqual(mockEvent)
  })

  it('loading alias = isInitialLoad || isRefetching', async () => {
    mockGetById.mockResolvedValueOnce(mockEvent).mockResolvedValueOnce(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => { await result.current.refetch() })
    expect(result.current.loading).toBe(false)
  })

  it('discards stale responses when id changes mid-flight', async () => {
    let resolveFirst: (v: typeof mockEvent) => void = () => {}
    const firstPromise = new Promise<typeof mockEvent>((r) => { resolveFirst = r })
    const secondEvent = { ...mockEvent, id: 2, title: 'Second' }
    mockGetById
      .mockReturnValueOnce(firstPromise)
      .mockResolvedValueOnce(secondEvent)

    const { result, rerender } = renderHook(({ id }) => useEvent(id), {
      initialProps: { id: 1 },
    })

    rerender({ id: 2 })
    await waitFor(() => expect(result.current.event?.title).toBe('Second'))

    // Stale response for id=1 arrives after id=2 is loaded — must be discarded.
    await act(async () => { resolveFirst(mockEvent) })
    expect(result.current.event?.title).toBe('Second')
  })

  it('discards a stale rejection when id changes mid-flight (catch isCurrent guard)', async () => {
    let rejectFirst: (e: Error) => void = () => {}
    const firstPromise = new Promise<typeof mockEvent>((_, r) => { rejectFirst = r })
    const secondEvent = { ...mockEvent, id: 2, title: 'Second' }
    mockGetById
      .mockReturnValueOnce(firstPromise)
      .mockResolvedValueOnce(secondEvent)

    const { result, rerender } = renderHook(({ id }) => useEvent(id), {
      initialProps: { id: 1 },
    })

    rerender({ id: 2 })
    await waitFor(() => expect(result.current.event?.title).toBe('Second'))

    // Stale rejection for id=1 lands after id=2 loaded — the catch `!isCurrent()`
    // guard (line 48) must discard it: no error surfaced for the live id=2.
    await act(async () => { rejectFirst(new Error('Network error')) })
    expect(result.current.event?.title).toBe('Second')
    expect(result.current.error).toBeNull()
  })

  it('does not setState after unmount', async () => {
    let resolve: (v: typeof mockEvent) => void = () => {}
    mockGetById.mockReturnValue(new Promise<typeof mockEvent>((r) => { resolve = r }))
    const { result, unmount } = renderHook(() => useEvent(1))
    unmount()
    await act(async () => { resolve(mockEvent) })
    expect(result.current.event).toBeNull()
  })

  it('refetch() resets error from a previous failed load', async () => {
    mockGetById
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.error).toBe('Impossible de charger cet événement.'))

    await act(async () => { await result.current.refetch() })

    expect(result.current.event).toEqual(mockEvent)
    expect(result.current.error).toBeNull()
  })
})
