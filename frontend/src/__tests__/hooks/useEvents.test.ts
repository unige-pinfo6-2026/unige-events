
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { useEvents } from '@/hooks/useEvents'

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
  getById: vi.fn(),
  deleteEvent: vi.fn(),
}))

import { getAll } from '@/services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

const makeMockEvents = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    title: `Event ${i + 1}`,
    location: 'Location',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    category: 'CONFERENCE' as const,
    faculty: null,
    status: 'PUBLISHED' as const,
    creatorId: 'user-1',
    createdAt: '2026-03-01T10:00:00',
  }))

afterEach(() => vi.resetAllMocks())

describe('useEvents', () => {
  it('fetches events on mount', async () => {
    mockGetAll.mockResolvedValue(makeMockEvents(3))
    const { result } = renderHook(() => useEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(3)
    expect(result.current.error).toBeNull()
  })

  it('sets hasMore true when page is full (12 items)', async () => {
    mockGetAll.mockResolvedValue(makeMockEvents(12))
    const { result } = renderHook(() => useEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(true)
  })

  it('sets hasMore false when page is not full', async () => {
    mockGetAll.mockResolvedValue(makeMockEvents(5))
    const { result } = renderHook(() => useEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(false)
  })

  it('sets error when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger les événements.')
    expect(result.current.events).toHaveLength(0)
  })

  it('appends events on loadMore', async () => {
    mockGetAll
      .mockResolvedValueOnce(makeMockEvents(12))
      .mockResolvedValueOnce(makeMockEvents(3))
    const { result } = renderHook(() => useEvents())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(12)
    act(() => result.current.loadMore())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(15)
  })
})
