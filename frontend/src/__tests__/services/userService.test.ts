
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AxiosError } from 'axios'
import {
  checkUsernameAvailable,
  deleteBanner,
  getCalendarToken,
  getMe,
  getPublicProfile,
  getUserById,
  getUserByUsername,
  regenerateCalendarToken,
  searchUsernames,
  updateProfile,
  updateUsername,
  uploadBanner,
  uploadPhoto,
} from '@/services/userService'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    head: vi.fn(),
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

const mockApiGet = api.get as ReturnType<typeof vi.fn>
const mockApiPut = api.put as ReturnType<typeof vi.fn>
const mockApiPost = api.post as ReturnType<typeof vi.fn>
const mockApiPatch = api.patch as ReturnType<typeof vi.fn>
const mockApiDelete = api.delete as ReturnType<typeof vi.fn>
const mockApiHead = api.head as ReturnType<typeof vi.fn>
const mockIsAxiosError = vi.mocked(axios.isAxiosError)

function axios404(): AxiosError {
  const error = new AxiosError('Not Found')
  // happy-dom doesn't ship axios internals — we forge the minimal shape used by the service.
  ;(error as unknown as { response: { status: number } }).response = { status: 404 }
  return error
}

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

  describe('getPublicProfile', () => {
    it('returns the UserPublicResponse on success', async () => {
      const profile = {
        id: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
        displayName: 'Alice',
        followerCount: 12,
        followingCount: 7,
        followStatus: null,
      }
      mockApiGet.mockResolvedValue({ data: profile })

      const result = await getPublicProfile(profile.id)

      expect(mockApiGet).toHaveBeenCalledWith(`/users/${profile.id}`, { skipGlobalRedirect: true })
      expect(result).toEqual(profile)
    })

    it('returns null on 404 (private or missing — ISSUE-93 anti-oracle)', async () => {
      const err = Object.assign(new Error('not found'), {
        isAxiosError: true,
        response: { status: 404 },
      })
      mockApiGet.mockRejectedValue(err)
      mockIsAxiosError.mockReturnValue(true)

      const result = await getPublicProfile('a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c')

      expect(result).toBeNull()
    })

    it('rethrows on 500', async () => {
      const err = Object.assign(new Error('server'), {
        isAxiosError: true,
        response: { status: 500 },
      })
      mockApiGet.mockRejectedValue(err)
      mockIsAxiosError.mockReturnValue(true)

      await expect(getPublicProfile('a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c')).rejects.toThrow('server')
    })

    it('rethrows non-axios errors', async () => {
      mockApiGet.mockRejectedValue(new Error('network'))
      mockIsAxiosError.mockReturnValue(false)

      await expect(getPublicProfile('a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c')).rejects.toThrow('network')
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

  // ─── SCRUM-169 ──────────────────────────────────────────────────────

  describe('getUserByUsername', () => {
    it('calls GET /users/by-username/{u} and returns the user on 200', async () => {
      const user = { id: 'uid', username: 'jean.dupont', displayName: 'Jean' }
      mockApiGet.mockResolvedValue({ data: user })
      const result = await getUserByUsername('jean.dupont')
      expect(result).toEqual(user)
      expect(mockApiGet).toHaveBeenCalledWith('/users/by-username/jean.dupont', { skipGlobalRedirect: true })
    })

    it('returns null when the backend responds 404', async () => {
      mockApiGet.mockRejectedValue(axios404())
      const result = await getUserByUsername('ghost.handle')
      expect(result).toBeNull()
    })

    it('encodes the username path segment', async () => {
      mockApiGet.mockResolvedValue({ data: null })
      await getUserByUsername('a.b/c')
      expect(mockApiGet).toHaveBeenCalledWith('/users/by-username/a.b%2Fc', { skipGlobalRedirect: true })
    })

    it('rethrows non-404 errors', async () => {
      const networkError = new Error('boom')
      mockApiGet.mockRejectedValue(networkError)
      await expect(getUserByUsername('any')).rejects.toBe(networkError)
    })
  })

  describe('updateUsername', () => {
    it('calls PATCH /users/me/username with the new username', async () => {
      const user = { id: 'uid', username: 'new.handle' }
      mockApiPatch.mockResolvedValue({ data: user })
      const result = await updateUsername('new.handle')
      expect(result).toEqual(user)
      expect(mockApiPatch).toHaveBeenCalledWith('/users/me/username', { username: 'new.handle' })
    })

    it('propagates 409 to the caller', async () => {
      const conflict = new AxiosError('Conflict')
      ;(conflict as unknown as { response: { status: number } }).response = { status: 409 }
      mockApiPatch.mockRejectedValue(conflict)
      await expect(updateUsername('taken.handle')).rejects.toBe(conflict)
    })
  })

  describe('searchUsernames (SCRUM-137 autocomplete)', () => {
    const matches = [
      { id: 'u1', username: 'nexiumito', displayName: 'Nexium Ito', avatarUrl: null },
      { id: 'u2', username: 'nexus.dev', displayName: 'Nexus Dev', avatarUrl: null },
    ]

    it('calls GET /users/search with q (no explicit limit) and returns the array', async () => {
      mockApiGet.mockResolvedValue({ data: matches })
      const result = await searchUsernames('nex')
      expect(result).toEqual(matches)
      expect(mockApiGet).toHaveBeenCalledWith('/users/search', { params: { q: 'nex' } })
    })

    it('propagates the explicit limit parameter', async () => {
      mockApiGet.mockResolvedValue({ data: matches.slice(0, 1) })
      await searchUsernames('nex', 1)
      expect(mockApiGet).toHaveBeenCalledWith('/users/search', { params: { q: 'nex', limit: 1 } })
    })

    it('rethrows the axios error so the caller can surface a search-failed state', async () => {
      const boom = new Error('boom')
      mockApiGet.mockRejectedValue(boom)
      await expect(searchUsernames('nex')).rejects.toBe(boom)
    })
  })

  describe('checkUsernameAvailable', () => {
    it('returns true when HEAD returns 404 (libre)', async () => {
      mockApiHead.mockRejectedValue(axios404())
      const result = await checkUsernameAvailable('libre.handle')
      expect(result).toBe(true)
      expect(mockApiHead).toHaveBeenCalledWith('/users/by-username/libre.handle', { skipGlobalRedirect: true })
    })

    it('returns false when HEAD returns 200 (pris)', async () => {
      mockApiHead.mockResolvedValue({})
      const result = await checkUsernameAvailable('pris.handle')
      expect(result).toBe(false)
    })

    it('rethrows non-404 errors so the form can surface a check error', async () => {
      const networkError = new Error('network down')
      mockApiHead.mockRejectedValue(networkError)
      await expect(checkUsernameAvailable('any')).rejects.toBe(networkError)
    })
  })
})
