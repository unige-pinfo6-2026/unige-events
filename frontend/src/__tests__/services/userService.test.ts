
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { deleteBanner, getCalendarToken, getMe, getUserById, regenerateCalendarToken, updateProfile, uploadBanner, uploadPhoto } from '@/services/userService'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'

const mockApiGet = api.get as ReturnType<typeof vi.fn>
const mockApiPut = api.put as ReturnType<typeof vi.fn>
const mockApiPost = api.post as ReturnType<typeof vi.fn>
const mockApiDelete = api.delete as ReturnType<typeof vi.fn>

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
    it('posts file to /users/me/image and returns updated user', async () => {
      const user = { id: '1', avatarUrl: '/api/uploads/uuid.jpg' }
      mockApiPost.mockResolvedValue({ data: user })
      const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' })
      const result = await uploadPhoto(file)
      expect(result).toEqual(user)
      expect(mockApiPost).toHaveBeenCalledWith(
        '/users/me/image',
        expect.any(FormData),
      )
    })
  })

  describe('uploadBanner', () => {
    it('posts file to /users/me/banner and returns updated user', async () => {
      const user = { id: '1', bannerUrl: '/api/uploads/banner.jpg' }
      mockApiPost.mockResolvedValue({ data: user })
      const file = new File(['img'], 'banner.jpg', { type: 'image/jpeg' })
      const result = await uploadBanner(file)
      expect(result).toEqual(user)
      expect(mockApiPost).toHaveBeenCalledWith(
        '/users/me/banner',
        expect.any(FormData),
      )
    })
  })

  describe('deleteBanner', () => {
    it('calls DELETE /users/me/banner and returns updated user', async () => {
      const user = { id: '1', bannerUrl: null }
      mockApiDelete.mockResolvedValue({ data: user })
      const result = await deleteBanner()
      expect(result).toEqual(user)
      expect(mockApiDelete).toHaveBeenCalledWith('/users/me/banner')
    })
  })

  describe('getCalendarToken', () => {
    it('returns calendar token from GET /users/me/calendar-token', async () => {
      const token = { calendarToken: 'tok', webcalUrl: 'webcal://x', httpsUrl: 'https://x' }
      mockApiGet.mockResolvedValue({ data: token })
      const result = await getCalendarToken()
      expect(result).toEqual(token)
      expect(mockApiGet).toHaveBeenCalledWith('/users/me/calendar-token')
    })
  })

  describe('regenerateCalendarToken', () => {
    it('returns new token from POST /users/me/calendar-token/regenerate', async () => {
      const token = { calendarToken: 'new', webcalUrl: 'webcal://y', httpsUrl: 'https://y' }
      mockApiPost.mockResolvedValue({ data: token })
      const result = await regenerateCalendarToken()
      expect(result).toEqual(token)
      expect(mockApiPost).toHaveBeenCalledWith('/users/me/calendar-token/regenerate')
    })
  })
})
