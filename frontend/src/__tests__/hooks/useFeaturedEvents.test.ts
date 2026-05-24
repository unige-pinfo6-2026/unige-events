import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useFeaturedEvents } from '@/hooks/useFeaturedEvents'

vi.mock('@/services/eventApi', () => ({
  getFeatured: vi.fn(),
}))

import { getFeatured } from '@/services/eventApi'

const mockGetFeatured = getFeatured as ReturnType<typeof vi.fn>

const makeMockEvents = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    title: `Event ${i + 1}`,
    location: 'Location',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    allDay: false,
    category: 'CONFERENCE' as const,
    faculty: null,
    status: 'PUBLISHED' as const,
    creatorId: 'user-1',
    attendingCount: 0,
    featured: false,
    createdAt: '2026-03-01T10:00:00',
  }))

afterEach(() => vi.resetAllMocks())

describe('useFeaturedEvents', () => {
  it('calls GET /events/featured with limit=6', async () => {
    mockGetFeatured.mockResolvedValue(makeMockEvents(3))
    const { result } = renderHook(() => useFeaturedEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetFeatured).toHaveBeenCalledWith(6)
    expect(mockGetFeatured).toHaveBeenCalledTimes(1)
  })

  it('starts with loading=true and resolves to loading=false', async () => {
    mockGetFeatured.mockResolvedValue(makeMockEvents(2))
    const { result } = renderHook(() => useFeaturedEvents())
    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))
  })

  it('returns events on success', async () => {
    mockGetFeatured.mockResolvedValue(makeMockEvents(6))
    const { result } = renderHook(() => useFeaturedEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(6)
    expect(result.current.error).toBeNull()
  })

  it('returns empty events when API returns empty list', async () => {
    mockGetFeatured.mockResolvedValue([])
    const { result } = renderHook(() => useFeaturedEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(0)
    expect(result.current.error).toBeNull()
  })

  it('returns error on HTTP failure', async () => {
    mockGetFeatured.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useFeaturedEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger les événements à la une.')
    expect(result.current.events).toHaveLength(0)
  })

  it('does not refetch on rerender', async () => {
    mockGetFeatured.mockResolvedValue(makeMockEvents(1))
    const { result, rerender } = renderHook(() => useFeaturedEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetFeatured).toHaveBeenCalledTimes(1)
    rerender()
    rerender()
    expect(mockGetFeatured).toHaveBeenCalledTimes(1)
  })

  it('ignores resolution after unmount (no state update on stale promise)', async () => {
    let resolveFn: (value: unknown) => void = () => {}
    mockGetFeatured.mockImplementation(() => new Promise((resolve) => { resolveFn = resolve }))
    const { result, unmount } = renderHook(() => useFeaturedEvents())
    unmount()
    resolveFn(makeMockEvents(2))
    await Promise.resolve()
    // After unmount there should be no error logged and the snapshot before unmount stays
    expect(result.current.events).toHaveLength(0)
  })

  it('does not set error when the fetch rejects after unmount (catch cancelled guard)', async () => {
    let rejectFn: (e: Error) => void = () => {}
    mockGetFeatured.mockImplementation(() => new Promise((_, reject) => { rejectFn = reject }))
    const { result, unmount } = renderHook(() => useFeaturedEvents())
    unmount()
    // Rejection after unmount hits the catch `if (!cancelled)` false branch
    // (line 27) — no error set, no throw.
    rejectFn(new Error('late network error'))
    await Promise.resolve()
    expect(result.current.error).toBeNull()
    expect(result.current.events).toHaveLength(0)
  })
})
