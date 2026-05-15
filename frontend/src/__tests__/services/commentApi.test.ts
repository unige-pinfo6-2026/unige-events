import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { deleteComment, getEventComments, postComment } from '@/services/commentApi'
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
})
