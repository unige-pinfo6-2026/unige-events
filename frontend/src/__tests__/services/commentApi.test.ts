import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { deleteComment, getEventComments, likeComment, postComment, unlikeComment } from '@/services/commentApi'
import type { Comment } from '@/types/comment'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockDelete = vi.mocked(api.delete)

const sample: Comment = {
  id: 1,
  content: 'Hello',
  authorId: 'author-uuid',
  authorDisplayName: 'Alice',
  authorAvatarUrl: null,
  authorUsername: 'alice',
  authorIsOrganizer: false,
  likeCount: 0,
  likedByMe: false,
  createdAt: '2026-05-14T10:00:00',
  parentCommentId: null,
  replies: [],
}

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
  mockDelete.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('commentApi', () => {
  it('fetches comments with pagination params', async () => {
    mockGet.mockResolvedValue({ data: [sample] } as Awaited<ReturnType<typeof api.get>>)

    const result = await getEventComments(42, { page: 1, size: 10 })

    expect(mockGet).toHaveBeenCalledWith('/events/42/comments', { params: { page: 1, size: 10 } })
    expect(result).toEqual([sample])
  })

  it('fetches with default empty params', async () => {
    mockGet.mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.get>>)

    await getEventComments(42)

    expect(mockGet).toHaveBeenCalledWith('/events/42/comments', { params: {} })
  })

  it('posts a top-level comment', async () => {
    mockPost.mockResolvedValue({ data: sample } as Awaited<ReturnType<typeof api.post>>)

    const result = await postComment(42, 'Hi')

    expect(mockPost).toHaveBeenCalledWith('/events/42/comments', {
      content: 'Hi',
      parentCommentId: null,
    })
    expect(result).toEqual(sample)
  })

  it('posts a reply with parentCommentId', async () => {
    mockPost.mockResolvedValue({
      data: { ...sample, parentCommentId: 5 },
    } as Awaited<ReturnType<typeof api.post>>)

    await postComment(42, 'A reply', 5)

    expect(mockPost).toHaveBeenCalledWith('/events/42/comments', {
      content: 'A reply',
      parentCommentId: 5,
    })
  })

  it('deletes a comment', async () => {
    mockDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await deleteComment(7)

    expect(mockDelete).toHaveBeenCalledWith('/comments/7')
  })

  it('likeComment POSTs to /comments/{id}/like and returns the response body', async () => {
    mockPost.mockResolvedValue({ data: { liked: true, likeCount: 3 } } as Awaited<ReturnType<typeof api.post>>)

    const result = await likeComment(11)

    expect(mockPost).toHaveBeenCalledWith('/comments/11/like')
    expect(result).toEqual({ liked: true, likeCount: 3 })
  })

  it('likeComment handles idempotent 200 (second like by same caller)', async () => {
    // Backend returns 200 + same count. Service surface is identical to 201.
    mockPost.mockResolvedValue({ data: { liked: true, likeCount: 3 } } as Awaited<ReturnType<typeof api.post>>)

    const result = await likeComment(11)

    expect(result.liked).toBe(true)
    expect(result.likeCount).toBe(3)
  })

  it('unlikeComment DELETEs to /comments/{id}/like', async () => {
    mockDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await unlikeComment(11)

    expect(mockDelete).toHaveBeenCalledWith('/comments/11/like')
  })

  it('unlikeComment swallows the response body (idempotent 204)', async () => {
    // The endpoint returns 204 ; the wrapper resolves to void.
    mockDelete.mockResolvedValue({ data: undefined } as Awaited<ReturnType<typeof api.delete>>)

    await expect(unlikeComment(11)).resolves.toBeUndefined()
  })
})
