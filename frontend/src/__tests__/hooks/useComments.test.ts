import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/commentApi', () => ({
  deleteComment: vi.fn(),
  getEventComments: vi.fn(),
  postComment: vi.fn(),
}))

import {
  deleteComment,
  getEventComments,
  postComment,
} from '@/services/commentApi'
import { useComments } from '@/hooks/useComments'
import type { Comment } from '@/types/comment'

const mockGet = vi.mocked(getEventComments)
const mockPost = vi.mocked(postComment)
const mockDelete = vi.mocked(deleteComment)

function makeComment(id: number, content = 'Hi', replies: Comment[] = []): Comment {
  return {
    id,
    content,
    authorId: 'a',
    authorDisplayName: 'A',
    authorAvatarUrl: null,
    authorUsername: 'a',
    authorIsOrganizer: false,
    likeCount: 0,
    likedByMe: false,
    createdAt: '2026-05-14T10:00:00',
    parentCommentId: null,
    replies,
  }
}

beforeEach(() => {
  mockGet.mockReset()
  mockPost.mockReset()
  mockDelete.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('useComments', () => {
  it('loads first page on mount', async () => {
    mockGet.mockResolvedValue([makeComment(1), makeComment(2)])

    const { result } = renderHook(() => useComments(42))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.comments).toHaveLength(2)
    expect(mockGet).toHaveBeenCalledWith(42, { page: 0, size: 20 })
  })

  it('detects hasMore when page is full', async () => {
    mockGet.mockResolvedValue(Array.from({ length: 20 }, (_, i) => makeComment(i + 1)))

    const { result } = renderHook(() => useComments(42, 20))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(true)
  })

  it('loadMore fetches the next page', async () => {
    mockGet.mockResolvedValueOnce([makeComment(1), makeComment(2)])
    mockGet.mockResolvedValueOnce([makeComment(3)])

    const { result } = renderHook(() => useComments(42, 2))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(true)

    await act(async () => {
      await result.current.loadMore()
    })

    expect(result.current.comments).toHaveLength(3)
    expect(result.current.hasMore).toBe(false)
  })

  it('post prepends a new top-level comment on success', async () => {
    mockGet.mockResolvedValue([makeComment(1)])
    mockPost.mockResolvedValue(makeComment(2, 'New'))

    const { result } = renderHook(() => useComments(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let outcome = { ok: false }
    await act(async () => {
      outcome = await result.current.post('New')
    })

    expect(outcome.ok).toBe(true)
    expect(result.current.comments[0].content).toBe('New')
    expect(result.current.comments).toHaveLength(2)
  })

  it('post ignores empty content', async () => {
    mockGet.mockResolvedValue([])
    const { result } = renderHook(() => useComments(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let outcome = { ok: true }
    await act(async () => {
      outcome = await result.current.post('   ')
    })

    expect(outcome.ok).toBe(false)
    expect(mockPost).not.toHaveBeenCalled()
  })

  it('postReply injects under the parent', async () => {
    mockGet.mockResolvedValue([makeComment(1)])
    const reply: Comment = {
      ...makeComment(99, 'reply'),
      parentCommentId: 1,
    }
    mockPost.mockResolvedValue(reply)

    const { result } = renderHook(() => useComments(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.postReply(1, 'reply')
    })

    expect(result.current.comments[0].replies).toEqual([reply])
  })

  it('remove deletes the comment locally and confirms server-side', async () => {
    mockGet.mockResolvedValue([makeComment(1), makeComment(2)])
    mockDelete.mockResolvedValue(undefined)

    const { result } = renderHook(() => useComments(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.remove(1)
    })

    expect(mockDelete).toHaveBeenCalledWith(1)
    expect(result.current.comments).toHaveLength(1)
  })

  it('remove rolls back on error', async () => {
    mockGet.mockResolvedValue([makeComment(1), makeComment(2)])
    mockDelete.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useComments(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.remove(1)
    })

    expect(result.current.comments).toHaveLength(2)
  })

  it('sets error state when loading fails', async () => {
    mockGet.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useComments(42))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toMatch(/charger/i)
  })
})
