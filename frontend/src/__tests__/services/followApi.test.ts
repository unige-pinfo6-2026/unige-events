// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}))

import api from '@/services/api'
import {
  acceptFollowRequest,
  followUser,
  getMyFollowRequests,
  rejectFollowRequest,
  unfollowUser,
} from '@/services/followApi'

const mockGet = api.get as ReturnType<typeof vi.fn>
const mockPost = api.post as ReturnType<typeof vi.fn>
const mockDelete = api.delete as ReturnType<typeof vi.fn>
const mockPatch = api.patch as ReturnType<typeof vi.fn>

const followDto = {
  id: 42,
  followerId: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  followedId: 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1',
  status: 'PENDING' as const,
  createdAt: '2026-05-14T10:00:00Z',
}

beforeEach(() => {
  vi.resetAllMocks()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('followApi', () => {
  describe('followUser', () => {
    it('POSTs /users/{id}/follow and returns the FollowDTO', async () => {
      mockPost.mockResolvedValue({ data: followDto })

      const result = await followUser(followDto.followedId)

      expect(mockPost).toHaveBeenCalledWith(`/users/${followDto.followedId}/follow`)
      expect(result).toEqual(followDto)
    })

    it('propagates 409 already_following', async () => {
      mockPost.mockRejectedValue(new Error('conflict'))
      await expect(followUser('any')).rejects.toThrow('conflict')
    })
  })

  describe('unfollowUser', () => {
    it('DELETEs /users/{id}/follow and resolves void', async () => {
      mockDelete.mockResolvedValue({ status: 204 })

      await expect(unfollowUser('uuid-x')).resolves.toBeUndefined()
      expect(mockDelete).toHaveBeenCalledWith('/users/uuid-x/follow')
    })

    it('propagates network failures', async () => {
      mockDelete.mockRejectedValue(new Error('boom'))
      await expect(unfollowUser('uuid-x')).rejects.toThrow('boom')
    })
  })

  describe('getMyFollowRequests', () => {
    it('GETs /users/me/follow-requests and returns the list', async () => {
      mockGet.mockResolvedValue({ data: [followDto] })

      const result = await getMyFollowRequests()

      expect(mockGet).toHaveBeenCalledWith('/users/me/follow-requests')
      expect(result).toEqual([followDto])
    })
  })

  describe('acceptFollowRequest', () => {
    it('PATCHes /follow-requests/{id}/accept and returns the upgraded FollowDTO', async () => {
      const accepted = { ...followDto, status: 'ACCEPTED' as const }
      mockPatch.mockResolvedValue({ data: accepted })

      const result = await acceptFollowRequest(42)

      expect(mockPatch).toHaveBeenCalledWith('/follow-requests/42/accept')
      expect(result.status).toBe('ACCEPTED')
    })
  })

  describe('rejectFollowRequest', () => {
    it('PATCHes /follow-requests/{id}/reject and resolves void', async () => {
      mockPatch.mockResolvedValue({ status: 204 })

      await expect(rejectFollowRequest(42)).resolves.toBeUndefined()
      expect(mockPatch).toHaveBeenCalledWith('/follow-requests/42/reject')
    })
  })
})
