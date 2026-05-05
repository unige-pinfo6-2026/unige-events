
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

// Routes calls by their (status, timeframe) pair so the hook's three concurrent
// requests resolve to distinct fixtures we can assert on independently.
function mockByStatusTimeframe(map: Record<string, ReturnType<typeof makeMockEvent>[]>) {
  mockGetMyParticipations.mockImplementation((status?: string, timeframe?: string) => {
    return Promise.resolve(map[`${status}|${timeframe}`] ?? [])
  })
}

describe('useMyParticipations', () => {
  it('fires three parallel calls with the correct status+timeframe pairs', async () => {
    mockByStatusTimeframe({
      'ATTENDING|upcoming': [makeMockEvent(1)],
      'WAITLISTED|upcoming': [makeMockEvent(2), makeMockEvent(3)],
      'ATTENDING|past': [makeMockEvent(4), makeMockEvent(5), makeMockEvent(6)],
    })

    const { result } = renderHook(() => useMyParticipations())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetMyParticipations).toHaveBeenCalledTimes(3)
    expect(mockGetMyParticipations).toHaveBeenCalledWith('ATTENDING', 'upcoming')
    expect(mockGetMyParticipations).toHaveBeenCalledWith('WAITLISTED', 'upcoming')
    expect(mockGetMyParticipations).toHaveBeenCalledWith('ATTENDING', 'past')
    expect(result.current.attending).toHaveLength(1)
    expect(result.current.waitlisted).toHaveLength(2)
    expect(result.current.pastAttending).toHaveLength(3)
    expect(result.current.error).toBeNull()
  })

  it('returns empty arrays for each list when all endpoints return empty', async () => {
    mockGetMyParticipations.mockResolvedValue([])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.attending).toEqual([])
    expect(result.current.waitlisted).toEqual([])
    expect(result.current.pastAttending).toEqual([])
  })

  it('any single rejection sets the error and clears no partial data', async () => {
    mockGetMyParticipations.mockImplementation((_status?: string, timeframe?: string) => {
      if (timeframe === 'past') return Promise.reject(new Error('5xx'))
      return Promise.resolve([makeMockEvent(1)])
    })

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos participations.')
    expect(result.current.attending).toEqual([])
    expect(result.current.waitlisted).toEqual([])
    expect(result.current.pastAttending).toEqual([])
  })

  it('sets generic error message when waitlisted endpoint fails', async () => {
    mockGetMyParticipations.mockImplementation((status?: string) => {
      if (status === 'WAITLISTED') return Promise.reject(new Error('boom'))
      return Promise.resolve([])
    })

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos participations.')
  })

  it('refresh() re-fires all three calls', async () => {
    mockGetMyParticipations.mockResolvedValue([])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetMyParticipations).toHaveBeenCalledTimes(3)

    result.current.refresh()
    await waitFor(() => expect(mockGetMyParticipations).toHaveBeenCalledTimes(6))
  })
})
