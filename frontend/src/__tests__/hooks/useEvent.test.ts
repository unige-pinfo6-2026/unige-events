// @vitest-environment jsdom

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

    act(() => result.current.refetch())

    await waitFor(() => expect(result.current.event?.title).toBe('Updated'))
    expect(mockGetById).toHaveBeenCalledTimes(2)
  })

  it('refetch() is a no-op when id is null', () => {
    const { result } = renderHook(() => useEvent(null))
    act(() => result.current.refetch())
    expect(mockGetById).not.toHaveBeenCalled()
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

    act(() => result.current.refetch())

    await waitFor(() => expect(result.current.event).toEqual(mockEvent))
    expect(result.current.error).toBeNull()
  })
})
