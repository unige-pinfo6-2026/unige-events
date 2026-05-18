import axios from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  FOLLOW_LIST_PAGE_SIZE,
  getFollowers,
  getFollowing,
} from '@/services/followApi'
import type { UserPublicResponse } from '@/types/user'

/**
 * Which side of the follow relationship the hook is paginating.
 * - `'followers'` → `GET /users/{id}/followers` (people following the target)
 * - `'following'` → `GET /users/{id}/following` (people the target follows)
 */
export type FollowListMode = 'followers' | 'following'

export interface UseFollowListResult {
  /** Accumulated rows from every fetched page. */
  users: UserPublicResponse[]
  loading: boolean
  /** `true` only while the *next* page is being fetched. */
  loadingMore: boolean
  /** `true` when the backend returned 404 (private profile OR truly missing). */
  isNotFound: boolean
  error: string | null
  /**
   * `true` while at least one more page is potentially available — i.e. the
   * last batch returned exactly {@link FOLLOW_LIST_PAGE_SIZE} rows. Flips to
   * `false` once a short page comes back.
   */
  hasMore: boolean
  /**
   * Fetches the next page and appends it. No-op while a fetch is in flight or
   * once {@link hasMore} is `false`.
   */
  loadMore: () => void
}

/**
 * Bespoke pagination hook for the followers / following lists
 * (SCRUM-142 — FE follow list pages). Mirrors the project convention
 * (`useUserProfile`, `useMyFollowRequests`, `useOrganizerEvents`) — no
 * TanStack Query, just `useState` + `useEffect` with a monotonic
 * `requestIdRef` to discard stale resolves on unmount or `targetId` / `mode`
 * change.
 *
 * Pagination is "load more" by appending: the page index lives in local
 * state, `loadMore()` bumps it, and the next fetch's rows are concatenated
 * onto `users`. The hook never refetches earlier pages — if a follow row
 * disappears mid-session the UI surfaces it on the next mount.
 *
 * `targetId === undefined` keeps the hook in loading state without firing a
 * request — used while the parent component resolves the URL `:username`
 * param to a uuid.
 */
export function useFollowList(
  targetId: string | undefined,
  mode: FollowListMode,
): UseFollowListResult {
  const [users, setUsers] = useState<UserPublicResponse[]>([])
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState<boolean>(true)
  const [loadingMore, setLoadingMore] = useState<boolean>(false)
  const [isNotFound, setIsNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState<boolean>(false)
  // Bumped on every fetch — late resolves from earlier fetches compare their
  // captured id against the current ref value and exit cleanly if outdated.
  const requestIdRef = useRef(0)

  // Reset accumulated state when the target or mode changes. Doing it inside
  // the fetch effect would race with the in-flight request from the previous
  // (target, mode); doing it here keeps `users` aligned with `(targetId, mode)`.
  useEffect(() => {
    setUsers([])
    setPage(0)
    setLoading(true)
    setLoadingMore(false)
    setIsNotFound(false)
    setError(null)
    setHasMore(false)
  }, [targetId, mode])

  // Single effect drives both initial load and `loadMore` (page changes).
  // `loadMore` mutates `page`, the effect re-runs and appends the next batch.
  useEffect(() => {
    if (!targetId) return

    const myRequestId = ++requestIdRef.current
    const isInitial = page === 0
    if (isInitial) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }
    setError(null)

    const fetcher = mode === 'followers' ? getFollowers : getFollowing
    fetcher(targetId, page, FOLLOW_LIST_PAGE_SIZE)
      .then(batch => {
        if (requestIdRef.current !== myRequestId) return
        // Append on non-initial pages, replace on initial (handles the case
        // where the reset effect hasn't flushed before this resolves — the
        // initial fetch is authoritative for `users`).
        setUsers(prev => (isInitial ? batch : [...prev, ...batch]))
        setHasMore(batch.length === FOLLOW_LIST_PAGE_SIZE)
      })
      .catch(err => {
        if (requestIdRef.current !== myRequestId) return
        if (axios.isAxiosError(err) && err.response?.status === 404) {
          setIsNotFound(true)
          setHasMore(false)
          return
        }
        setError(
          mode === 'followers'
            ? 'Impossible de charger les followers.'
            : 'Impossible de charger les abonnements.',
        )
        setHasMore(false)
      })
      .finally(() => {
        if (requestIdRef.current !== myRequestId) return
        setLoading(false)
        setLoadingMore(false)
      })

    return () => {
      // Bump on unmount / dep change so any late resolve is discarded.
      requestIdRef.current += 1
    }
  }, [targetId, mode, page])

  const loadMore = useCallback(() => {
    setPage(prev => prev + 1)
  }, [])

  return { users, loading, loadingMore, isNotFound, error, hasMore, loadMore }
}
