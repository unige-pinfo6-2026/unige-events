import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import {
  cancelEvent,
  deleteEvent,
  getMyEvents,
  publishEvent,
  restoreEvent,
} from '@/services/eventApi'
import type { Event, EventStatus } from '@/types/event'

function extractValidationErrors(e: unknown): string[] {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { errors?: unknown } | undefined
    if (data && Array.isArray(data.errors)) {
      return data.errors.filter((s): s is string => typeof s === 'string')
    }
  }
  return []
}

export type PublishResult =
  | { ok: true }
  | { ok: false; errors: string[] }

interface UseMyEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
  publish: (id: number) => Promise<PublishResult>
  cancel: (id: number) => Promise<boolean>
  restore: (id: number) => Promise<boolean>
  permanentlyDelete: (id: number) => Promise<boolean>
}

export function useMyEvents(status: EventStatus): UseMyEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getMyEvents({ status, size: 100 })
      setEvents(data)
    } catch {
      setError('Impossible de charger vos événements.')
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    fetch()
  }, [fetch])

  const publish = useCallback(async (id: number): Promise<PublishResult> => {
    try {
      await publishEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return { ok: true }
    } catch (e) {
      const errors = extractValidationErrors(e)
      return { ok: false, errors }
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
