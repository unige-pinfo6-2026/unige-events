// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn(),
}))

import { getAll } from '@/services/eventApi'
import { useOrganizerEvents } from '@/hooks/useOrganizerEvents'
import type { Event } from '@/types/event'

const mockGetAll = getAll as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

const event: Event = {
  id: 1,
  title: 'Conf',
  description: '',
  location: '',
  startDate: '2026-05-14T10:00:00.000Z',
  endDate: '2026-05-14T12:00:00.000Z',
  category: 'CONFERENCE',
  faculty: null,
  status: 'PUBLISHED',
  creatorId: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  capacity: 50,
  attendingCount: 0,
  createdAt: '2026-05-01T10:00:00.000Z',
}

describe('useOrganizerEvents', () => {
  it('does not fetch when organizerId is undefined', async () => {
    const { result } = renderHook(() => useOrganizerEvents(undefined))

    await new Promise(r => setTimeout(r, 10))

    expect(mockGetAll).not.toHaveBeenCalled()
    expect(result.current.loading).toBe(true)
    expect(result.current.events).toEqual([])
  })

  it('GETs /events with organizerId + status=PUBLISHED and surfaces the array', async () => {
    mockGetAll.mockResolvedValueOnce([event])

    const { result } = renderHook(() => useOrganizerEvents(event.creatorId))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetAll).toHaveBeenCalledWith({ organizerId: event.creatorId, status: 'PUBLISHED' })
    expect(result.current.events).toEqual([event])
    expect(result.current.error).toBeNull()
  })

  it('returns empty array + French error on failure', async () => {
    mockGetAll.mockRejectedValueOnce(new Error('boom'))

    const { result } = renderHook(() => useOrganizerEvents(event.creatorId))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toEqual([])
    expect(result.current.error).toBe('Impossible de charger les événements organisés.')
  })

  it('refetches on organizerId change', async () => {
    mockGetAll
      .mockResolvedValueOnce([event])
      .mockResolvedValueOnce([])

    const { result, rerender } = renderHook(({ id }: { id: string }) => useOrganizerEvents(id), {
      initialProps: { id: event.creatorId },
    })

    await waitFor(() => expect(result.current.events.length).toBe(1))

    rerender({ id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1' })

    await waitFor(() => expect(result.current.events.length).toBe(0))
    expect(mockGetAll).toHaveBeenCalledTimes(2)
  })
})
