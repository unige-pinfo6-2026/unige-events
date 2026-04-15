import { useCallback, useEffect, useState } from 'react'
import { cancelEvent, deleteEvent, getAll, publishEvent, restoreEvent } from '@/services/eventApi'
import type { Event, EventStatus } from '@/types/event'

interface UseMyEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
  publish: (id: number) => Promise<boolean>
  cancel: (id: number) => Promise<boolean>
  restore: (id: number) => Promise<boolean>
  permanentlyDelete: (id: number) => Promise<boolean>
}

export function useMyEvents(organizerId: string | null, status: EventStatus): UseMyEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    if (!organizerId) {
      setEvents([])
      setError(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const data = await getAll({ organizerId, status, size: 100 })
      const sorted = [...data].sort((a, b) => b.startDate.localeCompare(a.startDate))
      setEvents(sorted)
    } catch {
      setError('Impossible de charger vos événements.')
    } finally {
      setLoading(false)
    }
  }, [organizerId, status])

  useEffect(() => {
    fetch()
  }, [fetch])

  const publish = useCallback(async (id: number) => {
    try {
      await publishEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const cancel = useCallback(async (id: number) => {
    try {
      await cancelEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const restore = useCallback(async (id: number) => {
    try {
      await restoreEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const permanentlyDelete = useCallback(async (id: number) => {
    try {
      await deleteEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  return { events, loading, error, refresh: fetch, publish, cancel, restore, permanentlyDelete }
}
