// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import api from '@/services/api'
import { getEventAttendees } from '@/services/attendeesApi'

const mockApiGet = vi.mocked(api.get)

const sampleAttendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Alice',
  avatarUrl: null,
}

const anonymizedRow = {
  id: 2,
  userId: null,
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
}

beforeEach(() => {
  mockApiGet.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('attendeesApi', () => {
  describe('getEventAttendees', () => {
    it('GETs /events/{id}/attendees with paginated query params', async () => {
      mockApiGet.mockResolvedValue({ data: [sampleAttendance] } as Awaited<ReturnType<typeof api.get>>)

      const result = await getEventAttendees(42, { page: 0, size: 20 })

      expect(mockApiGet).toHaveBeenCalledWith('/events/42/attendees', {
        params: { page: 0, size: 20 },
      })
      expect(result).toEqual([sampleAttendance])
    })

    it('surfaces anonymized rows (userId/displayName/avatarUrl all null) untouched', async () => {
      mockApiGet.mockResolvedValue({
        data: [sampleAttendance, anonymizedRow],
      } as Awaited<ReturnType<typeof api.get>>)

      const result = await getEventAttendees(42, { page: 0, size: 20 })

      expect(result).toEqual([sampleAttendance, anonymizedRow])
      expect(result[1].userId).toBeNull()
      expect(result[1].displayName).toBeNull()
    })

    it('propagates errors', async () => {
      mockApiGet.mockRejectedValue(new Error('boom'))

      await expect(getEventAttendees(42, { page: 0, size: 20 })).rejects.toThrow('boom')
    })
  })
})
