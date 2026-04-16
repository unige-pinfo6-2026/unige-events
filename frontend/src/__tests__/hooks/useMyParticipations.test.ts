// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useMyParticipations } from '@/hooks/useMyParticipations'

vi.mock('@/services/attendanceApi', () => ({
  getMyParticipations: vi.fn(),
}))

import { getMyParticipations } from '@/services/attendanceApi'

const mockGetMyParticipations = getMyParticipations as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number) => ({
  id,
  title: `Event ${id}`,
  location: 'Location',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
  description: '',
  capacity: 100,
  attendingCount: 0,
  bannerUrl: '',
})

afterEach(() => vi.resetAllMocks())

describe('useMyParticipations', () => {
  it('calls getMyParticipations on mount and transitions loading→loaded', async () => {
    mockGetMyParticipations.mockResolvedValue([makeMockEvent(1)])

    const { result } = renderHook(() => useMyParticipations())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetMyParticipations).toHaveBeenCalledOnce()
    expect(result.current.events).toHaveLength(1)
    expect(result.current.error).toBeNull()
  })

  it('returns empty array (stub behavior)', async () => {
    mockGetMyParticipations.mockResolvedValue([])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toEqual([])
  })

  it('sets error message on fetch error', async () => {
    mockGetMyParticipations.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos participations.')
    expect(result.current.events).toEqual([])
  })

  it('refresh() re-fetches events', async () => {
    mockGetMyParticipations.mockResolvedValue([makeMockEvent(1)])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetMyParticipations).toHaveBeenCalledTimes(1)

    result.current.refresh()
    await waitFor(() => expect(mockGetMyParticipations).toHaveBeenCalledTimes(2))
  })
})
