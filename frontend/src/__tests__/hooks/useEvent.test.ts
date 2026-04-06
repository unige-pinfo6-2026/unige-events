// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useEvent } from '@/hooks/useEvent'

vi.mock('@/services/eventApi', () => ({
  getById: vi.fn(),
  getAll: vi.fn(),
  deleteEvent: vi.fn(),
}))

import { getById } from '@/services/eventApi'

const mockGetById = getById as ReturnType<typeof vi.fn>

const mockEvent = {
  id: 1,
  title: 'Test Event',
  location: 'Test Location',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
}

afterEach(() => vi.resetAllMocks())

describe('useEvent', () => {
  it('does not fetch when id is null', () => {
    const { result } = renderHook(() => useEvent(null))
    expect(result.current.event).toBeNull()
    expect(mockGetById).not.toHaveBeenCalled()
  })

  it('fetches event by id', async () => {
    mockGetById.mockResolvedValue(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.event).toEqual(mockEvent)
    expect(result.current.error).toBeNull()
  })

  it('sets error when fetch fails', async () => {
    mockGetById.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useEvent(1))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger cet événement.')
    expect(result.current.event).toBeNull()
  })

  it('starts in loading state', () => {
    mockGetById.mockResolvedValue(mockEvent)
    const { result } = renderHook(() => useEvent(1))
    expect(result.current.loading).toBe(true)
  })
})
