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
 * top-level comments. Mutation semantics:
 * - `post(content)` and `postReply(parentId, content)` are **blocking submits**:
 *   the button is disabled (`posting=true`) until the server confirms, then the
 *   confirmed comment is inserted. We prefer this over a true-optimistic flow
 *   here because comments need an authoritative `id` for the delete/reply
 *   actions of `CommentItem` (no temp-id reconciliation drift).
 * - `remove(commentId)` is **truly optimistic**: filters the comment locally
 *   before the API call and rolls back atomically on error.
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

  // We snapshot `comments` *before* the optimistic mutation. React strict
  // mode (active in tests) invokes state updaters twice, so a `let snapshot`
  // captured inside a `setComments(prev => ...)` updater is unreliable —
  // it sees the post-filter state on the second invocation.
  //
  // Trade-off: the useCallback depends on `comments`, so the function
  // reference changes on every state mutation. In practice, comment delete
  // is a low-frequency one-at-a-time action (user clicks, awaits confirm,
  // clicks the next one) so the lack of reference stability doesn't matter
  // for our consumers (CommentSection / CommentItem). Two concurrent
  // remove() calls would still risk over-rollback, but that flow doesn't
  // exist in the UI.
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
