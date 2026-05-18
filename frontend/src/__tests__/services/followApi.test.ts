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
  FOLLOW_LIST_PAGE_SIZE,
  followUser,
  getFollowers,
  getFollowing,
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

  describe('getFollowers', () => {
    it('GETs /users/{id}/followers with pagination params and returns the array', async () => {
      const targetId = 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1'
      const user = {
        id: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
        username: 'alice',
        followerCount: 0,
        followingCount: 0,
        followStatus: null,
      }
      mockGet.mockResolvedValue({ data: [user] })

      const result = await getFollowers(targetId, 0, 20)

      expect(mockGet).toHaveBeenCalledWith(
        `/users/${targetId}/followers`,
        { params: { page: 0, size: 20 } },
      )
      expect(result).toEqual([user])
    })

    it('uses FOLLOW_LIST_PAGE_SIZE as the default size', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await getFollowers('target-id', 2)

      expect(mockGet).toHaveBeenCalledWith(
        '/users/target-id/followers',
        { params: { page: 2, size: FOLLOW_LIST_PAGE_SIZE } },
      )
    })

    it('propagates 404 (private profile / not found)', async () => {
      mockGet.mockRejectedValue(new Error('not found'))
      await expect(getFollowers('x', 0)).rejects.toThrow('not found')
    })
  })

  describe('getFollowing', () => {
    it('GETs /users/{id}/following with pagination params', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await getFollowing('target-id', 1, 50)

      expect(mockGet).toHaveBeenCalledWith(
        '/users/target-id/following',
        { params: { page: 1, size: 50 } },
      )
    })

    it('uses FOLLOW_LIST_PAGE_SIZE as the default size', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await getFollowing('target-id', 0)

      expect(mockGet).toHaveBeenCalledWith(
        '/users/target-id/following',
        { params: { page: 0, size: FOLLOW_LIST_PAGE_SIZE } },
      )
    })
  })
})
