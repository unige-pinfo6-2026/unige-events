// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCalendarEvents } from '@/hooks/useCalendarEvents'

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
}))

import { getAll } from '@/services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

const mockEvent = {
  id: 1,
  title: 'Conférence IA',
  description: '',
  location: 'Uni Dufour',
  startDate: '2026-04-10T09:00:00',
  endDate: '2026-04-10T11:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'u1',
  attendingCount: 0,
  createdAt: '2026-03-01',
}

afterEach(() => {
  vi.resetAllMocks()
})

describe('useCalendarEvents', () => {
  it('starts in loading state', () => {
    mockGetAll.mockResolvedValue([])
    const { result } = renderHook(() => useCalendarEvents(new Date('2026-04-01')))
    expect(result.current.loading).toBe(true)
  })

  it('returns mapped events after successful fetch', async () => {
    mockGetAll.mockResolvedValue([mockEvent])

    const { result } = renderHook(() => useCalendarEvents(new Date('2026-04-01')))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(1)
    expect(result.current.events[0].title).toBe('Conférence IA')
    expect(result.current.events[0].resource).toEqual(mockEvent)
    expect(result.current.error).toBeNull()
  })

  it('sets error message when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('network error'))

    const { result } = renderHook(() => useCalendarEvents(new Date('2026-04-01')))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les événements.')
    expect(result.current.events).toHaveLength(0)
  })

  it('filters events that start after the last day of the month', async () => {
    const futureEvent = { ...mockEvent, startDate: '2026-05-01T09:00:00', endDate: '2026-05-01T11:00:00' }
    mockGetAll.mockResolvedValue([futureEvent])

    const { result } = renderHook(() => useCalendarEvents(new Date('2026-04-01')))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(0)
  })

  it('calls getAll with a date range for the given month', async () => {
    mockGetAll.mockResolvedValue([mockEvent])

    const { result } = renderHook(() => useCalendarEvents(new Date('2026-04-15')))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetAll).toHaveBeenCalledWith(
      expect.objectContaining({ endDateFrom: expect.stringContaining('2026-04-01') }),
    )
  })
})
