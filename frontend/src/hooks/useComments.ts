import { useCallback, useEffect, useState } from 'react'
import {
  deleteComment,
  getEventComments,
  postComment,
} from '@/services/commentApi'
import type { Comment } from '@/types/comment'

const DEFAULT_PAGE_SIZE = 20

interface UseCommentsResult {
  comments: Comment[]
  hasMore: boolean
  loading: boolean
  posting: boolean
  error: string | null
  post: (content: string) => Promise<{ ok: boolean }>
  postReply: (parentCommentId: number, content: string) => Promise<{ ok: boolean }>
  remove: (commentId: number) => Promise<void>
  loadMore: () => Promise<void>
  refresh: () => Promise<void>
}

/**
 * Loads + mutates the comments of an event. Pagination is cumulative on the
 * top-level comments. Optimistic mutations:
 * - `post(content)` appends a temp comment immediately, replaces it with the
 *   server-confirmed one on success, rolls back on error.
 * - `postReply(parentId, content)` injects the reply under its parent, same
 *   optimistic behavior.
 * - `remove(commentId)` filters the comment locally; rolls back on error.
 *
 * Pattern aligned on `useFavorite` / `useAttendance` (no TanStack Query in
 * the codebase).
 */
export function useComments(eventId: number, pageSize = DEFAULT_PAGE_SIZE): UseCommentsResult {
  const [comments, setComments] = useState<Comment[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [posting, setPosting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchPage = useCallback(
    async (nextPage: number) => {
      setLoading(true)
      setError(null)
      try {
        const list = await getEventComments(eventId, { page: nextPage, size: pageSize })
        setComments((prev) => (nextPage === 0 ? list : [...prev, ...list]))
        setPage(nextPage)
        setHasMore(list.length === pageSize)
      } catch {
        setError('Impossible de charger les commentaires.')
      } finally {
        setLoading(false)
      }
    },
    [eventId, pageSize],
  )

  useEffect(() => {
    void fetchPage(0)
  }, [fetchPage])

  const refresh = useCallback(() => fetchPage(0), [fetchPage])

  const loadMore = useCallback(async () => {
    if (loading || !hasMore) return
    await fetchPage(page + 1)
  }, [fetchPage, hasMore, loading, page])

  const post = useCallback(
    async (content: string): Promise<{ ok: boolean }> => {
      const trimmed = content.trim()
      if (trimmed === '') return { ok: false }
      setPosting(true)
      try {
        const created = await postComment(eventId, trimmed)
        setComments((prev) => [created, ...prev])
        return { ok: true }
      } catch {
        return { ok: false }
      } finally {
        setPosting(false)
      }
    },
    [eventId],
  )

  const postReply = useCallback(
    async (parentCommentId: number, content: string): Promise<{ ok: boolean }> => {
      const trimmed = content.trim()
      if (trimmed === '') return { ok: false }
      setPosting(true)
      try {
        const created = await postComment(eventId, trimmed, parentCommentId)
        setComments((prev) =>
          prev.map((c) =>
            c.id === parentCommentId ? { ...c, replies: [...c.replies, created] } : c,
          ),
        )
        return { ok: true }
      } catch {
        return { ok: false }
      } finally {
        setPosting(false)
      }
    },
    [eventId],
  )

  const remove = useCallback(async (commentId: number) => {
    const previous = comments
    setComments((prev) =>
      prev
        .filter((c) => c.id !== commentId)
        .map((c) => ({ ...c, replies: c.replies.filter((r) => r.id !== commentId) })),
    )
    try {
      await deleteComment(commentId)
    } catch {
      setComments(previous)
    }
  }, [comments])

  return {
    comments,
    hasMore,
    loading,
    posting,
    error,
    post,
    postReply,
    remove,
    loadMore,
    refresh,
  }
}
