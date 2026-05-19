
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCalendarEvents } from '../../hooks/useCalendarEvents'

vi.mock('../../services/eventApi', () => ({
  getAll: vi.fn(),
}))

import { getAll } from '../../services/eventApi'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

const makeEvent = (id: number, startDate: string, endDate: string) => ({
  id,
  title: `Event ${id}`,
  description: 'Description',
  location: 'Location',
  startDate,
  endDate,
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'u1',
  createdAt: '2026-03-01T00:00:00',
})

afterEach(() => vi.resetAllMocks())

describe('useCalendarEvents', () => {
  it('starts in loading state', () => {
    mockGetAll.mockResolvedValue([])
    const date = new Date(2026, 3, 1) // April 2026
    const { result } = renderHook(() => useCalendarEvents(date))
    expect(result.current.loading).toBe(true)
  })

  it('fetches and maps events for the given month', async () => {
    const events = [
      makeEvent(1, '2026-04-10T09:00:00', '2026-04-10T11:00:00'),
      makeEvent(2, '2026-04-20T14:00:00', '2026-04-20T16:00:00'),
    ]
    mockGetAll.mockResolvedValue(events)

    const { result } = renderHook(() => useCalendarEvents(new Date(2026, 3, 1)))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(2)
    expect(result.current.error).toBeNull()

    const first = result.current.events[0]
    expect(first.title).toBe('Event 1')
    expect(first.start).toEqual(new Date('2026-04-10T09:00:00'))
    expect(first.end).toEqual(new Date('2026-04-10T11:00:00'))
    expect(first.resource.id).toBe(1)
  })

  it('filters out events starting after the last day of the month', async () => {
    const events = [
      makeEvent(1, '2026-04-30T09:00:00', '2026-04-30T11:00:00'),
      makeEvent(2, '2026-05-01T09:00:00', '2026-05-01T11:00:00'), // should be excluded
    ]
    mockGetAll.mockResolvedValue(events)

    const { result } = renderHook(() => useCalendarEvents(new Date(2026, 3, 1)))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(1)
    expect(result.current.events[0].resource.id).toBe(1)
  })

  it('calls getAll with endDateFrom set to the first day of the month', async () => {
    mockGetAll.mockResolvedValue([])

    renderHook(() => useCalendarEvents(new Date(2026, 3, 15)))
    await waitFor(() => expect(mockGetAll).toHaveBeenCalled())

    const [params] = mockGetAll.mock.calls[0]
    const d = new Date(2026, 3, 1)
    const pad = (n: number) => String(n).padStart(2, '0')
    const expected = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T00:00:00`
    expect(params.endDateFrom).toBe(expected)
    expect(params.status).toBe('PUBLISHED')
  })

  it('sets error when fetch fails', async () => {
    mockGetAll.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useCalendarEvents(new Date(2026, 3, 1)))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les événements.')
    expect(result.current.events).toHaveLength(0)
  })

  it('returns empty events list when API returns nothing', async () => {
    mockGetAll.mockResolvedValue([])

    const { result } = renderHook(() => useCalendarEvents(new Date(2026, 3, 1)))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(0)
    expect(result.current.error).toBeNull()
  })
})
