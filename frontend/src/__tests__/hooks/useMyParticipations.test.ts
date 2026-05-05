
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
  it('fires two parallel calls (ATTENDING and WAITLISTED) and exposes both lists', async () => {
    mockGetMyParticipations.mockImplementation((status?: string) => {
      if (status === 'ATTENDING') return Promise.resolve([makeMockEvent(1), makeMockEvent(2)])
      if (status === 'WAITLISTED') return Promise.resolve([makeMockEvent(3)])
      return Promise.resolve([])
    })

    const { result } = renderHook(() => useMyParticipations())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetMyParticipations).toHaveBeenCalledTimes(2)
    expect(mockGetMyParticipations).toHaveBeenCalledWith('ATTENDING')
    expect(mockGetMyParticipations).toHaveBeenCalledWith('WAITLISTED')
    expect(result.current.attending).toHaveLength(2)
    expect(result.current.waitlisted).toHaveLength(1)
    expect(result.current.error).toBeNull()
  })

  it('returns empty arrays when both endpoints return empty', async () => {
    mockGetMyParticipations.mockResolvedValue([])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.attending).toEqual([])
    expect(result.current.waitlisted).toEqual([])
  })

  it('sets error message when either endpoint rejects', async () => {
    mockGetMyParticipations.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos participations.')
    expect(result.current.attending).toEqual([])
    expect(result.current.waitlisted).toEqual([])
  })

  it('sets error when waitlisted endpoint specifically fails (Promise.all rejection)', async () => {
    mockGetMyParticipations.mockImplementation((status?: string) => {
      if (status === 'WAITLISTED') return Promise.reject(new Error('5xx'))
      return Promise.resolve([makeMockEvent(1)])
    })

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos participations.')
  })

  it('refresh() re-fires both calls', async () => {
    mockGetMyParticipations.mockResolvedValue([])

    const { result } = renderHook(() => useMyParticipations())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetMyParticipations).toHaveBeenCalledTimes(2)

    result.current.refresh()
    await waitFor(() => expect(mockGetMyParticipations).toHaveBeenCalledTimes(4))
  })
})
