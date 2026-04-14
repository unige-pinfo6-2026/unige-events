// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useEvents } from '@/hooks/useEvents'

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
}))

import { getAll } from '@/services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

const makeEvent = (id: number) => ({
  id,
  title: `Event ${id}`,
  location: 'Lieu',
  startDate: '2026-04-10T09:00:00',
  endDate: '2026-04-10T11:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'u1',
  attendingCount: 0,
  createdAt: '2026-03-01',
})

afterEach(() => {
  vi.resetAllMocks()
})

describe('useEvents', () => {
  it('starts in loading state', () => {
    mockGetAll.mockResolvedValue([])
    const { result } = renderHook(() => useEvents())
    expect(result.current.loading).toBe(true)
  })

  it('returns events after successful fetch', async () => {
    const events = [makeEvent(1), makeEvent(2)]
    mockGetAll.mockResolvedValue(events)

    const { result } = renderHook(() => useEvents())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(2)
    expect(result.current.error).toBeNull()
  })

  it('sets hasMore to false when fewer than page size results are returned', async () => {
    mockGetAll.mockResolvedValue([makeEvent(1)])

    const { result } = renderHook(() => useEvents())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.hasMore).toBe(false)
  })

  it('sets hasMore to true when exactly page size (12) results are returned', async () => {
    const events = Array.from({ length: 12 }, (_, i) => makeEvent(i + 1))
    mockGetAll.mockResolvedValue(events)

    const { result } = renderHook(() => useEvents())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.hasMore).toBe(true)
  })

  it('sets error message when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('network error'))

    const { result } = renderHook(() => useEvents())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les événements.')
    expect(result.current.events).toHaveLength(0)
  })

  it('appends events when loadMore is called', async () => {
    const firstPage = Array.from({ length: 12 }, (_, i) => makeEvent(i + 1))
    const secondPage = [makeEvent(13), makeEvent(14)]
    mockGetAll.mockResolvedValueOnce(firstPage).mockResolvedValueOnce(secondPage)

    const { result } = renderHook(() => useEvents())

    await waitFor(() => expect(result.current.events).toHaveLength(12))

    act(() => { result.current.loadMore() })

    await waitFor(() => expect(result.current.events).toHaveLength(14))
    expect(result.current.hasMore).toBe(false)
  })
})
