// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios')
  return {
    ...actual,
    default: { ...actual.default, isAxiosError: vi.fn() },
    isAxiosError: vi.fn(),
  }
})

import api from '@/services/api'
import axios from 'axios'
import { getEventAttendees, getPublicUser } from '@/services/attendeesApi'

const mockApiGet = vi.mocked(api.get)
const mockIsAxiosError = vi.mocked(axios.isAxiosError)

const sampleAttendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
}

const samplePublicProfile = {
  id: 'user-1',
  displayName: 'Alice Martin',
  faculty: 'SCIENCES',
  studyLevel: 'MASTER',
  bio: null,
  interests: [],
  avatarUrl: null,
  bannerUrl: null,
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockIsAxiosError.mockReset()
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

    it('propagates errors', async () => {
      mockApiGet.mockRejectedValue(new Error('boom'))

      await expect(getEventAttendees(42, { page: 0, size: 20 })).rejects.toThrow('boom')
    })
  })

  describe('getPublicUser', () => {
    it('returns the public profile on success', async () => {
      mockApiGet.mockResolvedValue({ data: samplePublicProfile } as Awaited<ReturnType<typeof api.get>>)

      const result = await getPublicUser('user-1')

      expect(mockApiGet).toHaveBeenCalledWith('/users/user-1')
      expect(result).toEqual(samplePublicProfile)
    })

    it('returns null on 403 (private profile)', async () => {
      const err = Object.assign(new Error('forbidden'), {
        isAxiosError: true,
        response: { status: 403 },
      })
      mockApiGet.mockRejectedValue(err)
      mockIsAxiosError.mockReturnValue(true)

      const result = await getPublicUser('user-1')

      expect(result).toBeNull()
    })

    it('returns null on 404 (not found)', async () => {
      const err = Object.assign(new Error('not found'), {
        isAxiosError: true,
        response: { status: 404 },
      })
      mockApiGet.mockRejectedValue(err)
      mockIsAxiosError.mockReturnValue(true)

      const result = await getPublicUser('user-1')

      expect(result).toBeNull()
    })

    it('rethrows on 500', async () => {
      const err = Object.assign(new Error('server'), {
        isAxiosError: true,
        response: { status: 500 },
      })
      mockApiGet.mockRejectedValue(err)
      mockIsAxiosError.mockReturnValue(true)

      await expect(getPublicUser('user-1')).rejects.toThrow('server')
    })

    it('rethrows non-axios errors', async () => {
      mockApiGet.mockRejectedValue(new Error('network'))
      mockIsAxiosError.mockReturnValue(false)

      await expect(getPublicUser('user-1')).rejects.toThrow('network')
    })
  })
})
