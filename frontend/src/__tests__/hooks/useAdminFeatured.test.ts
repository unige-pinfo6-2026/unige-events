import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/adminApi', () => ({
  getFeaturedEvents: vi.fn(),
  featureEvent: vi.fn(),
  unfeatureEvent: vi.fn(),
}))

vi.mock('@/services/searchApi', () => ({
  searchEvents: vi.fn(),
}))

import { getFeaturedEvents, featureEvent, unfeatureEvent } from '@/services/adminApi'
import { searchEvents } from '@/services/searchApi'
import { useAdminFeatured } from '@/hooks/useAdminFeatured'
import type { Event } from '@/types/event'

const mockGetFeaturedEvents = getFeaturedEvents as ReturnType<typeof vi.fn>
const mockFeatureEvent = featureEvent as ReturnType<typeof vi.fn>
const mockUnfeatureEvent = unfeatureEvent as ReturnType<typeof vi.fn>
const mockSearchEvents = searchEvents as ReturnType<typeof vi.fn>

const makeEvent = (id: number): Event => ({
  id,
  title: `Event ${id}`,
  location: 'Geneva',
  startDate: '2026-06-01T10:00:00',
  endDate: '2026-06-01T12:00:00',
  allDay: false,
  category: 'CONFERENCE',
  faculty: null,
  status: 'PUBLISHED',
  creatorId: 'user-1',
  createdAt: '2026-05-01T10:00:00',
  description: '',
  capacity: undefined,
  attendingCount: 0,
  bannerUrl: undefined,
})

afterEach(() => {
  vi.resetAllMocks()
  vi.useRealTimers()
})

describe('useAdminFeatured — initial load', () => {
  it('fetches featured events on mount', async () => {
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1), makeEvent(2)])
    const { result } = renderHook(() => useAdminFeatured())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.featuredEvents).toHaveLength(2)
    expect(result.current.error).toBeNull()
  })

  it('sets error message when fetch fails', async () => {
    mockGetFeaturedEvents.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les événements mis en avant.')
    expect(result.current.featuredEvents).toEqual([])
  })

  it('starts with empty search query and no search results', async () => {
    mockGetFeaturedEvents.mockResolvedValue([])
    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.searchQuery).toBe('')
    expect(result.current.searchResults).toEqual([])
  })
})

describe('useAdminFeatured — search', () => {
  it('clears searchResults when query is empty whitespace', async () => {
    mockGetFeaturedEvents.mockResolvedValue([])

    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setSearchQuery('   '))
    await waitFor(() => expect(result.current.searchResults).toEqual([]))
    expect(mockSearchEvents).not.toHaveBeenCalled()
  })

  it('calls searchEvents after debounce when query is non-empty', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([])
    mockSearchEvents.mockResolvedValue([makeEvent(5)])

    const { result } = renderHook(() => useAdminFeatured())

    // Flush the initial fetch promise (microtasks)
    await act(async () => { await Promise.resolve() })

    act(() => result.current.setSearchQuery('concert'))
    expect(mockSearchEvents).not.toHaveBeenCalled()

    await act(async () => { vi.advanceTimersByTime(350) })

    expect(mockSearchEvents).toHaveBeenCalledWith({ q: 'concert', size: 8 })
  })

  it('cancels the pending debounce when query changes before it fires (cleanup line 75)', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([])
    mockSearchEvents.mockResolvedValue([makeEvent(5)])

    const { result } = renderHook(() => useAdminFeatured())
    await act(async () => { await Promise.resolve() })

    // First query — starts a 350 ms debounce timer
    act(() => result.current.setSearchQuery('ab'))
    // Second query before timer fires — cleanup of previous effect clears the timer (line 75)
    act(() => result.current.setSearchQuery('abc'))

    // Only the 'abc' query should fire when the debounce settles
    await act(async () => { vi.advanceTimersByTime(350) })
    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledTimes(1)
    expect(mockSearchEvents).toHaveBeenCalledWith({ q: 'abc', size: 8 })
    vi.useRealTimers()
  })

  it('sets searchResults to empty when search fails', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([])
    mockSearchEvents.mockRejectedValue(new Error('API error'))

    const { result } = renderHook(() => useAdminFeatured())
    await act(async () => { await Promise.resolve() })

    act(() => result.current.setSearchQuery('fail'))
    await act(async () => { vi.advanceTimersByTime(350) })

    // Let the rejected promise settle
    await act(async () => { await Promise.resolve() })

    expect(result.current.searchResults).toEqual([])
  })

  it('discards stale search results when a newer query is already current (line 64)', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([])

    // First call is a manually-controlled promise; second resolves immediately.
    let resolveFirst!: (v: Event[]) => void
    const firstSearch = new Promise<Event[]>((r) => { resolveFirst = r })
    mockSearchEvents
      .mockReturnValueOnce(firstSearch)
      .mockResolvedValueOnce([makeEvent(5)])

    const { result } = renderHook(() => useAdminFeatured())
    await act(async () => { await Promise.resolve() })

    // Fire 'ab' — debounce fires, searchEvents called (pending on firstSearch).
    act(() => result.current.setSearchQuery('ab'))
    await act(async () => { vi.advanceTimersByTime(350) })

    // Fire 'abc' — seq increments to 2; second call resolves immediately.
    act(() => result.current.setSearchQuery('abc'))
    await act(async () => { vi.advanceTimersByTime(350) })
    await act(async () => { await Promise.resolve() })

    // 'abc' results are set; now resolve the stale 'ab' call (seq=1, current=2).
    await act(async () => { resolveFirst([makeEvent(99)]) })
    await act(async () => { await Promise.resolve() })

    // The stale result (id=99) must not overwrite the current result (id=5).
    expect(result.current.searchResults).toHaveLength(1)
    expect(result.current.searchResults[0].id).toBe(5)

    vi.useRealTimers()
  })

  it('discards stale search error when a newer query is already current (line 67)', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([])

    let rejectFirst!: (e: Error) => void
    const firstSearch = new Promise<Event[]>((_, r) => { rejectFirst = r })
    mockSearchEvents
      .mockReturnValueOnce(firstSearch)
      .mockResolvedValueOnce([makeEvent(5)])

    const { result } = renderHook(() => useAdminFeatured())
    await act(async () => { await Promise.resolve() })

    act(() => result.current.setSearchQuery('ab'))
    await act(async () => { vi.advanceTimersByTime(350) })

    act(() => result.current.setSearchQuery('abc'))
    await act(async () => { vi.advanceTimersByTime(350) })
    await act(async () => { await Promise.resolve() })

    // Reject the stale 'ab' call — must not clear the 'abc' results.
    await act(async () => { rejectFirst(new Error('stale network error')) })
    await act(async () => { await Promise.resolve() })

    expect(result.current.searchResults).toHaveLength(1)
    expect(result.current.searchResults[0].id).toBe(5)

    vi.useRealTimers()
  })
})

describe('useAdminFeatured — featureEvent', () => {
  it('calls featureEvent API and moves event from searchResults to featuredEvents', async () => {
    vi.useFakeTimers()
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1)])
    mockSearchEvents.mockResolvedValue([makeEvent(2), makeEvent(3)])
    mockFeatureEvent.mockResolvedValue(undefined)

    const { result } = renderHook(() => useAdminFeatured())
    await act(async () => { await Promise.resolve() })

    act(() => result.current.setSearchQuery('event'))
    await act(async () => { vi.advanceTimersByTime(350) })
    await act(async () => { await Promise.resolve() })

    vi.useRealTimers()

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.featureEvent(2) })

    expect(mockFeatureEvent).toHaveBeenCalledWith(2)
    expect(result.current.featuredEvents[0].id).toBe(2)
    expect(result.current.searchResults.find(e => e.id === 2)).toBeUndefined()
    expect(ok).toBe(true)
  })

  it('returns true but does not mutate lists when the id is absent from searchResults (line 83 false branch)', async () => {
    // featureEvent succeeds server-side, but the id was never in searchResults
    // (e.g. results were cleared by a newer query) → `featured` is undefined,
    // so neither featuredEvents nor searchResults change.
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1)])
    mockFeatureEvent.mockResolvedValue(undefined)

    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.featureEvent(404) })

    expect(mockFeatureEvent).toHaveBeenCalledWith(404)
    expect(ok).toBe(true)
    // No event with id=404 existed in searchResults → lists untouched.
    expect(result.current.featuredEvents).toEqual([makeEvent(1)])
    expect(result.current.searchResults).toEqual([])
  })

  it('returns false when featureEvent API fails', async () => {
    mockGetFeaturedEvents.mockResolvedValue([])
    mockFeatureEvent.mockRejectedValue(new Error('Forbidden'))

    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.featureEvent(99) })

    expect(ok).toBe(false)
  })
})

describe('useAdminFeatured — unfeatureEvent', () => {
  it('calls unfeatureEvent API and removes event from featuredEvents', async () => {
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1), makeEvent(2)])
    mockUnfeatureEvent.mockResolvedValue(undefined)

    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.featuredEvents).toHaveLength(2)

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.unfeatureEvent(1) })

    expect(mockUnfeatureEvent).toHaveBeenCalledWith(1)
    expect(result.current.featuredEvents).toHaveLength(1)
    expect(result.current.featuredEvents[0].id).toBe(2)
    expect(ok).toBe(true)
  })

  it('returns false when unfeatureEvent API fails', async () => {
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1)])
    mockUnfeatureEvent.mockRejectedValue(new Error('Server error'))

    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.unfeatureEvent(1) })

    expect(ok).toBe(false)
    expect(result.current.featuredEvents).toHaveLength(1)
  })
})

describe('useAdminFeatured — refresh', () => {
  it('re-fetches featured events when refresh() is called', async () => {
    mockGetFeaturedEvents.mockResolvedValue([makeEvent(1)])
    const { result } = renderHook(() => useAdminFeatured())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetFeaturedEvents).toHaveBeenCalledTimes(1)

    act(() => result.current.refresh())
    await waitFor(() => expect(mockGetFeaturedEvents).toHaveBeenCalledTimes(2))
  })
})
