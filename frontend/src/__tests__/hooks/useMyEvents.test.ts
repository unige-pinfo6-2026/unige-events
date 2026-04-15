// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { useMyEvents } from '@/hooks/useMyEvents'

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
  publishEvent: vi.fn(),
  deleteEvent: vi.fn(),
}))

import { getAll, publishEvent, deleteEvent } from '@/services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>
const mockPublishEvent = publishEvent as ReturnType<typeof vi.fn>
const mockDeleteEvent = deleteEvent as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number, startDate: string) => ({
  id,
  title: `Event ${id}`,
  location: 'Location',
  startDate,
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'org-1',
  createdAt: '2026-03-01T10:00:00',
  description: '',
  capacity: 100,
  attendingCount: 0,
  bannerUrl: '',
})

afterEach(() => vi.resetAllMocks())

describe('useMyEvents', () => {
  it('fetches events when organizerId is provided', async () => {
    mockGetAll.mockResolvedValue([
      makeMockEvent(1, '2026-04-10T14:00:00'),
      makeMockEvent(2, '2026-04-11T14:00:00'),
    ])

    const { result } = renderHook(() => useMyEvents('org-1', 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(2)
    expect(result.current.error).toBeNull()
    expect(mockGetAll).toHaveBeenCalledWith({
      organizerId: 'org-1',
      status: 'PUBLISHED',
      size: 100,
    })
  })

  it('sorts events by startDate descending', async () => {
    mockGetAll.mockResolvedValue([
      makeMockEvent(1, '2026-04-10T14:00:00'),
      makeMockEvent(2, '2026-04-11T14:00:00'),
      makeMockEvent(3, '2026-04-09T14:00:00'),
    ])

    const { result } = renderHook(() => useMyEvents('org-1', 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events[0].id).toBe(2) // latest
    expect(result.current.events[1].id).toBe(1)
    expect(result.current.events[2].id).toBe(3) // earliest
  })

  it('returns empty array and clears error when organizerId is null', async () => {
    const { result } = renderHook(() => useMyEvents(null, 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('sets error message when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useMyEvents('org-1', 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos événements.')
    expect(result.current.events).toEqual([])
  })

  it('publish() calls publishEvent and removes event from list', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetAll.mockResolvedValue([event])
    mockPublishEvent.mockResolvedValue({})

    const { result } = renderHook(() => useMyEvents('org-1', 'DRAFT'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let publishResult: boolean | undefined
    await act(async () => {
      publishResult = await result.current.publish(42)
    })

    expect(mockPublishEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(publishResult).toBe(true)
  })

  it('cancel() calls deleteEvent and removes event from list', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetAll.mockResolvedValue([event])
    mockDeleteEvent.mockResolvedValue({})

    const { result } = renderHook(() => useMyEvents('org-1', 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let cancelResult: boolean | undefined
    await act(async () => {
      cancelResult = await result.current.cancel(42)
    })

    expect(mockDeleteEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(cancelResult).toBe(true)
  })

  it('refresh() re-fetches events', async () => {
    mockGetAll.mockResolvedValue([makeMockEvent(1, '2026-04-10T14:00:00')])

    const { result } = renderHook(() => useMyEvents('org-1', 'PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetAll).toHaveBeenCalledTimes(1)

    act(() => {
      result.current.refresh()
    })

    await waitFor(() => expect(mockGetAll).toHaveBeenCalledTimes(2))
  })
})
