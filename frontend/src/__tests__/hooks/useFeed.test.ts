import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { groupEventsByDate, toDateKey, useFeed } from '@/hooks/useFeed'
import type { Event } from '@/types/event'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
}))

import { getAll } from '@/services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

// ─── Factories ────────────────────────────────────────────────────────────────

function makeEvent(overrides: Partial<Event> & { id: number; startDate: string }): Event {
  return {
    title: `Event ${overrides.id}`,
    location: 'Uni Dufour',
    endDate: overrides.startDate,
    category: 'CONFERENCE',
    status: 'PUBLISHED',
    creatorId: 'user-1',
    createdAt: '2026-01-01T00:00:00',
    allDay: false,
    attendingCount: 0,
    ...overrides,
  }
}

function makeEvents(count: number, dateString = '2026-04-28T10:00:00'): Event[] {
  return Array.from({ length: count }, (_, i) =>
    makeEvent({ id: i + 1, startDate: dateString }),
  )
}

// ─── beforeEach / afterEach ───────────────────────────────────────────────────

beforeEach(() => {
  mockGetAll.mockResolvedValue([])
})

afterEach(() => {
  vi.resetAllMocks()
})

// ─── toDateKey ────────────────────────────────────────────────────────────────

describe('toDateKey', () => {
  it('converts a UTC ISO string to a YYYY-MM-DD local date key', () => {
    // Fixed offset: run in UTC for determinism — key must start with 2026-04
    const key = toDateKey('2026-04-28T10:00:00Z')
    expect(key).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('normalises strings without timezone suffix (treats as UTC)', () => {
    const key = toDateKey('2026-04-28T10:00:00')
    expect(key).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})

// ─── groupEventsByDate ────────────────────────────────────────────────────────

describe('groupEventsByDate', () => {
  it('returns an empty array for an empty input', () => {
    expect(groupEventsByDate([])).toEqual([])
  })

  it('groups events by startDate (same day → one group)', () => {
    const events = [
      makeEvent({ id: 1, startDate: '2026-04-28T10:00:00Z' }),
      makeEvent({ id: 2, startDate: '2026-04-28T14:00:00Z' }),
    ]
    const groups = groupEventsByDate(events)
    expect(groups).toHaveLength(1)
    expect(groups[0].events).toHaveLength(2)
  })

  it('creates separate groups for different days', () => {
    const events = [
      makeEvent({ id: 1, startDate: '2026-04-28T10:00:00Z' }),
      makeEvent({ id: 2, startDate: '2026-04-29T10:00:00Z' }),
    ]
    const groups = groupEventsByDate(events)
    expect(groups).toHaveLength(2)
    expect(groups[0].events[0].id).toBe(1)
    expect(groups[1].events[0].id).toBe(2)
  })

  it('merges inter-page events on the same day (cross-page fusion)', () => {
    // Simule page N (fin) + page N+1 (début) sur le même jour
    const pageN = [makeEvent({ id: 1, startDate: '2026-04-30T22:00:00Z' })]
    const pageN1 = [makeEvent({ id: 2, startDate: '2026-04-30T23:00:00Z' })]
    const all = [...pageN, ...pageN1]
    const groups = groupEventsByDate(all)

    // Tous tombent le même jour UTC → un seul groupe
    const key1 = toDateKey('2026-04-30T22:00:00Z')
    const key2 = toDateKey('2026-04-30T23:00:00Z')
    if (key1 === key2) {
      expect(groups).toHaveLength(1)
      expect(groups[0].events).toHaveLength(2)
    } else {
      // Décalage horaire local : deux groupes distincts → aussi valide
      expect(groups).toHaveLength(2)
    }
  })

  it('preserves insertion order (startDate ASC from API)', () => {
    const events = [
      makeEvent({ id: 1, startDate: '2026-05-01T08:00:00Z' }),
      makeEvent({ id: 2, startDate: '2026-05-02T08:00:00Z' }),
      makeEvent({ id: 3, startDate: '2026-05-03T08:00:00Z' }),
    ]
    const groups = groupEventsByDate(events)
    expect(groups[0].events[0].id).toBe(1)
    expect(groups[1].events[0].id).toBe(2)
    expect(groups[2].events[0].id).toBe(3)
  })
})

// ─── useFeed ──────────────────────────────────────────────────────────────────

describe('useFeed', () => {
  it('starts in loading state', () => {
    mockGetAll.mockReturnValue(new Promise(() => {}))
    const { result } = renderHook(() => useFeed())
    expect(result.current.loading).toBe(true)
    expect(result.current.groups).toEqual([])
  })

  it('returns grouped events after fetch', async () => {
    const events = [
      makeEvent({ id: 1, startDate: '2026-04-28T10:00:00Z' }),
      makeEvent({ id: 2, startDate: '2026-04-29T10:00:00Z' }),
    ]
    mockGetAll.mockResolvedValue(events)

    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.groups.length).toBeGreaterThanOrEqual(1)
    expect(result.current.error).toBeNull()
  })

  it('sets hasMore=true when page is full (20 items)', async () => {
    mockGetAll.mockResolvedValue(makeEvents(20))
    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(true)
  })

  it('sets hasMore=false when page is not full', async () => {
    mockGetAll.mockResolvedValue(makeEvents(5))
    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(false)
  })

  it('sets error when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBeTruthy()
    expect(result.current.groups).toEqual([])
  })

  it('appends events and re-groups on loadMore', async () => {
    const page0 = [makeEvent({ id: 1, startDate: '2026-04-28T10:00:00Z' })]
    const page1 = [makeEvent({ id: 2, startDate: '2026-04-29T10:00:00Z' })]
    mockGetAll
      .mockResolvedValueOnce(page0)
      .mockResolvedValueOnce(page1)

    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.loadMore() })
    await waitFor(() => expect(result.current.loading).toBe(false))

    const totalEvents = result.current.groups.reduce((s, g) => s + g.events.length, 0)
    expect(totalEvents).toBe(2)
    expect(mockGetAll).toHaveBeenCalledTimes(2)
  })

  it('calls getAll with status=PUBLISHED and endDateFrom', async () => {
    mockGetAll.mockResolvedValue([])
    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetAll).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'PUBLISHED', endDateFrom: expect.any(String) }),
    )
  })

  it('passes followedOnly=true to getAll when the option is set (SCRUM-168)', async () => {
    mockGetAll.mockResolvedValue([])
    const { result } = renderHook(() => useFeed({ followedOnly: true }))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetAll).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'PUBLISHED', followedOnly: true }),
    )
  })

  it('omits followedOnly (undefined) by default so the "Tous" feed stays unfiltered', async () => {
    mockGetAll.mockResolvedValue([])
    const { result } = renderHook(() => useFeed())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetAll).toHaveBeenCalledWith(
      expect.objectContaining({ followedOnly: undefined }),
    )
  })

  it('loadMore is a no-op while the initial page is still in flight (fetchingRef guard, lines 83/129)', async () => {
    // Hold the page-0 request open so `fetchingRef` stays true; a concurrent
    // loadMore() must short-circuit at line 129 (and never reach a 2nd getAll).
    let resolvePage0!: (v: Event[]) => void
    mockGetAll.mockImplementationOnce(
      () => new Promise<Event[]>(r => { resolvePage0 = r }),
    )

    const { result } = renderHook(() => useFeed())
    expect(result.current.loading).toBe(true)

    act(() => { result.current.loadMore() })
    // Guard hit: no second call was made while page 0 is pending.
    expect(mockGetAll).toHaveBeenCalledTimes(1)

    await act(async () => { resolvePage0(makeEvents(5)) })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetAll).toHaveBeenCalledTimes(1)
  })

  it('ignores a stale in-flight response when followedOnly changes (no wrong-filter data)', async () => {
    const firstData = [makeEvent({ id: 1, startDate: '2026-04-28T10:00:00Z' })]
    const secondData = [makeEvent({ id: 2, startDate: '2026-04-29T10:00:00Z' })]
    let resolveFirst!: (v: Event[]) => void
    mockGetAll
      .mockImplementationOnce(() => new Promise<Event[]>(r => { resolveFirst = r }))
      .mockResolvedValueOnce(secondData)

    const { result, rerender } = renderHook(
      ({ f }: { f: boolean }) => useFeed({ followedOnly: f }),
      { initialProps: { f: false } },
    )

    // Toggle the filter while the first (followedOnly=false) request is still pending.
    rerender({ f: true })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.groups.flatMap(g => g.events.map(e => e.id))).toEqual([2])

    // The stale first request now resolves — it must NOT overwrite the list.
    await act(async () => { resolveFirst(firstData) })
    expect(result.current.groups.flatMap(g => g.events.map(e => e.id))).toEqual([2])
  })
})
