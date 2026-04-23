// @vitest-environment jsdom

import { describe, expect, it, vi, afterEach } from 'vitest'
import { getEventStats, getEventAttendees } from '@/services/statsApi'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import api from '@/services/api'
const mockGet = api.get as ReturnType<typeof vi.fn>

afterEach(() => vi.resetAllMocks())

describe('getEventStats', () => {
  it('calls GET /events/:id/stats and returns data', async () => {
    const stats = { views: 100, attendingCount: 20, checkInCount: 5 }
    mockGet.mockResolvedValue({ data: stats })

    const result = await getEventStats(42)

    expect(mockGet).toHaveBeenCalledWith('/events/42/stats')
    expect(result).toEqual(stats)
  })

  it('propagates errors from the API', async () => {
    mockGet.mockRejectedValue(new Error('Network error'))
    await expect(getEventStats(1)).rejects.toThrow('Network error')
  })
})

describe('getEventAttendees', () => {
  it('calls GET /events/:id/attendees and returns data', async () => {
    const attendees = [{ id: 1, userId: 'u-1', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01' }]
    mockGet.mockResolvedValue({ data: attendees })

    const result = await getEventAttendees(42)

    expect(mockGet).toHaveBeenCalledWith('/events/42/attendees')
    expect(result).toEqual(attendees)
  })

  it('propagates errors from the API', async () => {
    mockGet.mockRejectedValue(new Error('Forbidden'))
    await expect(getEventAttendees(1)).rejects.toThrow('Forbidden')
  })
})
