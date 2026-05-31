import { useCallback, useEffect, useRef, useState } from 'react'
import {
  acceptFollowRequest,
  getMyFollowRequests,
  rejectFollowRequest,
} from '@/services/followApi'
import { getPublicProfile } from '@/services/userService'
import { INBOX_POLL_INTERVAL_MS } from '@/constants/polling'
import type { FollowDTO } from '@/types/follow'
import type { UserPublicResponse } from '@/types/user'

/**
 * One row in the "Demandes reçues" panel: the raw FollowDTO plus the
 * resolved follower's public profile (`null` while loading, or if the
 * lookup failed — the panel surfaces a neutral fallback in that case).
 */
export interface FollowRequestRow {
  request: FollowDTO
  follower: UserPublicResponse | null
}

export interface UseMyFollowRequestsResult {
  rows: FollowRequestRow[]
  loading: boolean
  error: string | null
  accept: (followId: number) => Promise<void>
  reject: (followId: number) => Promise<void>
  refresh: () => void
}

/**
 * Loads PENDING follow requests received by the authenticated user
 * (SCRUM-110 — US-21). The backend returns id-only `FollowDTO`s, so for
 * each row we resolve `GET /users/{followerId}` to display avatar +
 * displayName. Failures on the per-row lookup fall back to `follower:
 * null` rather than failing the whole panel.
 *
 * Accept / reject are optimistic: the row disappears immediately, then
 * we refresh on success. On error the row reappears.
 *
 * Stale-response guard via a monotonic `requestIdRef` so the in-flight
 * batch is discarded on unmount or another refresh.
 */
export function useMyFollowRequests(): UseMyFollowRequestsResult {
  const [rows, setRows] = useState<FollowRequestRow[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const requestIdRef = useRef(0)

  const fetchAll = useCallback(async (silent = false) => {
    const myRequestId = ++requestIdRef.current
    // Silent (poll) refetches keep the current rows on screen — no spinner
    // flicker — exactly like the notification bell.
    if (!silent) setLoading(true)
    setError(null)
    try {
      const requests = await getMyFollowRequests()
      if (requestIdRef.current !== myRequestId) return
      const profiles = await Promise.allSettled(
        requests.map(r => getPublicProfile(r.followerId)),
      )
      if (requestIdRef.current !== myRequestId) return
      const merged: FollowRequestRow[] = requests.map((request, i) => {
        const settled = profiles[i]
        const follower = settled.status === 'fulfilled' ? settled.value : null
        return { request, follower }
      })
      setRows(merged)
    } catch {
      if (requestIdRef.current !== myRequestId) return
      setError('Impossible de charger les demandes de suivi.')
      setRows([])
    } finally {
      if (requestIdRef.current === myRequestId) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchAll()
    // Auto-refresh in lockstep with the notification bell so the inbox badge
    // updates without a manual reload (silent — no spinner on each tick).
    const interval = setInterval(() => void fetchAll(true), INBOX_POLL_INTERVAL_MS)
    return () => {
      clearInterval(interval)
      // Bump on unmount so any late resolve is discarded.
      requestIdRef.current += 1
    }
  }, [fetchAll])

  const accept = useCallback(async (followId: number) => {
    const previous = rows
    setRows(prev => prev.filter(r => r.request.id !== followId))
    try {
      await acceptFollowRequest(followId)
      await fetchAll()
    } catch (e) {
      setRows(previous)
      throw e
    }
  }, [rows, fetchAll])

  const reject = useCallback(async (followId: number) => {
    const previous = rows
    setRows(prev => prev.filter(r => r.request.id !== followId))
    try {
      await rejectFollowRequest(followId)
      await fetchAll()
    } catch (e) {
      setRows(previous)
      throw e
    }
  }, [rows, fetchAll])

  const refresh = useCallback(() => {
    void fetchAll()
  }, [fetchAll])

  return { rows, loading, error, accept, reject, refresh }
}
