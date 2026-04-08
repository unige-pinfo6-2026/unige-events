// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { attend, unattend } from '@/services/attendanceApi'

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

    it('posts INTERESTED status to /events/{id}/attend', async () => {
      const interestedAttendance = { ...sampleAttendance, status: 'INTERESTED' as const }
      mockApiPost.mockResolvedValue({ data: interestedAttendance } as Awaited<ReturnType<typeof api.post>>)

      const result = await attend(42, 'INTERESTED')

      expect(mockApiPost).toHaveBeenCalledWith('/events/42/attend', { status: 'INTERESTED' })
      expect(result).toEqual(interestedAttendance)
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
})
