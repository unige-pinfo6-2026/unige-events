
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useEvent } from '@/hooks/useEvent'

vi.mock('@/services/eventApi', () => ({
  getById: vi.fn(),
}))

import { getById } from '@/services/eventApi'

const mockGetById = getById as ReturnType<typeof vi.fn>

const mockEvent = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T09:00:00',
  endDate: '2026-04-10T11:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'u1',
  createdAt: '2026-03-01',
}

afterEach(() => {
  vi.resetAllMocks()
})

describe('useEvent', () => {
  it('starts in loading state', () => {
    mockGetById.mockResolvedValue(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    expect(result.current.loading).toBe(true)
  })

  it('returns event data on successful fetch', async () => {
    mockGetById.mockResolvedValue(mockEvent)

    const { result } = renderHook(() => useEvent(1))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.event).toEqual(mockEvent)
    expect(result.current.error).toBeNull()
  })

  it('sets error message when fetch fails', async () => {
    mockGetById.mockRejectedValue(new Error('network error'))

    const { result } = renderHook(() => useEvent(1))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger cet événement.')
    expect(result.current.event).toBeNull()
  })

  it('does not fetch when id is null', () => {
    renderHook(() => useEvent(null))
    // loading starts true but never flips because effect returns early
    expect(mockGetById).not.toHaveBeenCalled()
  })

  it('refetches when id changes', async () => {
    const event2 = { ...mockEvent, id: 2, title: 'Workshop' }
    mockGetById.mockResolvedValueOnce(mockEvent).mockResolvedValueOnce(event2)

    const { result, rerender } = renderHook(({ id }) => useEvent(id), {
      initialProps: { id: 1 },
    })

    await waitFor(() => expect(result.current.event?.title).toBe('Conférence IA'))

    rerender({ id: 2 })

    await waitFor(() => expect(result.current.event?.title).toBe('Workshop'))
  })
})
