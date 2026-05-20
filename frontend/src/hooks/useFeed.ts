import { useCallback, useEffect, useRef, useState } from 'react'
import { getAll } from '@/services/eventApi'
import { parseApiUtcDateTime } from '@/utils/dateTime'
import type { Event } from '@/types/event'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface FeedGroup {
  /** Clé YYYY-MM-DD (date locale de startDate) */
  date: string
  events: Event[]
}

export interface UseFeedOptions {
  /**
   * Futur filtre côté backend (SCRUM-138 / followedOnly).
   * Ignoré tant que l'endpoint ne le supporte pas.
   */
  followedOnly?: boolean
}

export interface UseFeedResult {
  groups: FeedGroup[]
  loading: boolean
  error: string | null
  hasMore: boolean
  loadMore: () => void
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20

/** Convertit une date API (ISO UTC ou sans suffix) en clé locale YYYY-MM-DD. */
export function toDateKey(dateString: string): string {
  const date = parseApiUtcDateTime(dateString)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/**
 * Groupe une liste plate d'événements (triés par startDate ASC) en groupes de
 * dates. La fusion inter-pages est automatique : les events de la page N et N+1
 * qui tombent le même jour se retrouvent dans le même groupe car on regroupe
 * depuis le tableau complet `allEvents`.
 */
export function groupEventsByDate(events: Event[]): FeedGroup[] {
  const map = new Map<string, Event[]>()
  for (const event of events) {
    const key = toDateKey(event.startDate)
    const list = map.get(key)
    if (list) {
      list.push(event)
    } else {
      map.set(key, [event])
    }
  }
  // Map préserve l'ordre d'insertion ; les events arrivent triés startDate ASC.
  return Array.from(map.entries()).map(([date, evts]) => ({ date, events: evts }))
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useFeed({ followedOnly: _followedOnly = false }: UseFeedOptions = {}): UseFeedResult {
  const [allEvents, setAllEvents] = useState<Event[]>([])
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(true)

  // Guard pour éviter les doubles appels (mode Strict + IntersectionObserver)
  const fetchingRef = useRef(false)

  const fetchPage = useCallback(async (pageNum: number) => {
    if (fetchingRef.current) return
    fetchingRef.current = true
    setLoading(true)
    setError(null)
    try {
      const now = new Date().toISOString().slice(0, 19)
      const data = await getAll({
        page: pageNum,
        size: PAGE_SIZE,
        status: 'PUBLISHED',
        endDateFrom: now,
        // followedOnly : non supporté côté backend pour l'instant (SCRUM-138)
      })
      setAllEvents(prev => (pageNum === 0 ? data : [...prev, ...data]))
      setHasMore(data.length === PAGE_SIZE)
    } catch {
      setError("Impossible de charger le fil d'événements.")
    } finally {
      setLoading(false)
      fetchingRef.current = false
    }
  }, [])

  useEffect(() => {
    setPage(0)
    setAllEvents([])
    fetchPage(0)
  }, [fetchPage])

  const loadMore = useCallback(() => {
    if (fetchingRef.current) return
    const nextPage = page + 1
    setPage(nextPage)
    fetchPage(nextPage)
  }, [page, fetchPage])

  const groups = groupEventsByDate(allEvents)

  return { groups, loading, error, hasMore, loadMore }
}
