// @vitest-environment jsdom

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getMe, getUserById, updateProfile, uploadPhoto } from '@/services/userService'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

import api from '@/services/api'

const mockApiGet = api.get as ReturnType<typeof vi.fn>
const mockApiPut = api.put as ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.resetAllMocks()
})

describe('userService', () => {
  describe('getMe', () => {
    it('returns user data from GET /users/me', async () => {
      const user = { sub: 'auth0|1', email: 'a@b.com', name: 'Alice' }
      mockApiGet.mockResolvedValue({ data: user })
      const result = await getMe()
      expect(result).toEqual(user)
      expect(mockApiGet).toHaveBeenCalledWith('/users/me')
    })
  })

  describe('getUserById', () => {
    it('returns null (stub)', async () => {
      mockApiGet.mockResolvedValue({ data: null })
      const result = await getUserById('auth0|1')
      expect(result).toBeNull()
    })
  })

  describe('updateProfile', () => {
    it('returns the same data passed in (stub)', async () => {
      const data = { displayName: 'Bob', bio: 'Hello' }
      mockApiPut.mockResolvedValue({ data })
      const result = await updateProfile(data)
      expect(result).toEqual(data)
    })
  })

  describe('uploadPhoto', () => {
    it('returns empty avatarUrl (stub)', async () => {
      const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })
      const result = await uploadPhoto(file)
      expect(result).toEqual({ photoUrl: '' })
    })
  })
})
