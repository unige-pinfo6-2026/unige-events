
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { attend, getMyAttendance, getMyParticipations, unattend } from '@/services/attendanceApi'

const mockApiGet = vi.mocked(api.get)
const mockApiPost = vi.mocked(api.post)
const mockApiDelete = vi.mocked(api.delete)

const sampleAttendance = {
  id: 1,
  userId: 'abc-123',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiPost.mockReset()
  mockApiDelete.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('attendanceApi', () => {
  describe('attend', () => {
    it('posts ATTENDING status to /events/{id}/attend', async () => {
      mockApiPost.mockResolvedValue({ data: sampleAttendance } as Awaited<ReturnType<typeof api.post>>)

      const result = await attend(42, 'ATTENDING')

      expect(mockApiPost).toHaveBeenCalledWith('/events/42/attend', { status: 'ATTENDING' })
      expect(result).toEqual(sampleAttendance)
    })

    it('propagates API errors', async () => {
      mockApiPost.mockRejectedValue(new Error('Network error'))

      await expect(attend(42, 'ATTENDING')).rejects.toThrow('Network error')
    })
  })

  describe('unattend', () => {
    it('sends DELETE to /events/{id}/attend', async () => {
      mockApiDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

      await unattend(42)

      expect(mockApiDelete).toHaveBeenCalledWith('/events/42/attend')
    })

    it('propagates API errors', async () => {
      mockApiDelete.mockRejectedValue(new Error('Network error'))

      await expect(unattend(42)).rejects.toThrow('Network error')
    })
  })

  describe('getMyAttendance', () => {
    const allAttendances = [
      { id: 1, userId: 'abc-123', eventId: 10, status: 'ATTENDING' as const, createdAt: '2026-04-08T10:00:00.000Z' },
      { id: 2, userId: 'abc-123', eventId: 42, status: 'ATTENDING' as const, createdAt: '2026-04-08T10:00:00.000Z' },
    ]

    it('returns the status for the given eventId', async () => {
      mockApiGet.mockResolvedValue({ data: allAttendances } as Awaited<ReturnType<typeof api.get>>)

      const result = await getMyAttendance(42)

      expect(mockApiGet).toHaveBeenCalledWith('/users/me/attendances')
      expect(result).toBe('ATTENDING')
    })

    it('returns ATTENDING for a different eventId', async () => {
      mockApiGet.mockResolvedValue({ data: allAttendances } as Awaited<ReturnType<typeof api.get>>)

      const result = await getMyAttendance(10)

      expect(result).toBe('ATTENDING')
    })

    it('returns null when the event is not in the list', async () => {
      mockApiGet.mockResolvedValue({ data: allAttendances } as Awaited<ReturnType<typeof api.get>>)

      const result = await getMyAttendance(99)

      expect(result).toBeNull()
    })

    it('returns null when the list is empty', async () => {
      mockApiGet.mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.get>>)

      const result = await getMyAttendance(42)

      expect(result).toBeNull()
    })

    it('returns null on 401 Axios error', async () => {
      const axiosError = Object.assign(new Error('Unauthorized'), {
        isAxiosError: true,
        response: { status: 401 },
      })
      mockApiGet.mockRejectedValue(axiosError)

      const result = await getMyAttendance(42)

      expect(result).toBeNull()
    })

    it('propagates non-401 Axios errors', async () => {
      const axiosError = Object.assign(new Error('Server Error'), {
        isAxiosError: true,
        response: { status: 500 },
      })
      mockApiGet.mockRejectedValue(axiosError)

      await expect(getMyAttendance(42)).rejects.toThrow('Server Error')
    })

    it('propagates non-Axios errors', async () => {
      mockApiGet.mockRejectedValue(new Error('Network error'))

      await expect(getMyAttendance(42)).rejects.toThrow('Network error')
    })
  })

  describe('getMyParticipations', () => {
    it('returns an empty array (stub)', async () => {
      const result = await getMyParticipations()

      expect(result).toEqual([])
      expect(Array.isArray(result)).toBe(true)
    })
  })
})
