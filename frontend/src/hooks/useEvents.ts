import { useCallback, useEffect, useState } from 'react'
import { getAll } from '../services/eventApi'
import type { Event } from '../types'

const PAGE_SIZE = 12

interface UseEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
  hasMore: boolean
  loadMore: () => void
}

export function useEvents(): UseEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(true)

  const fetchPage = useCallback(async (pageNum: number) => {
    setLoading(true)
    setError(null)
    try {
      const now = new Date().toISOString().slice(0, 19)
      const data = await getAll({ page: pageNum, size: PAGE_SIZE, status: 'PUBLISHED', startDateFrom: now })
      setEvents((prev) => (pageNum === 0 ? data : [...prev, ...data]))
      setHasMore(data.length === PAGE_SIZE)
    } catch {
      setError('Impossible de charger les événements.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchPage(0)
  }, [fetchPage])

  const loadMore = useCallback(() => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchPage(nextPage)
  }, [page, fetchPage])

  return { events, loading, error, hasMore, loadMore }
}
