import { useEffect, useState } from 'react'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'

interface UseEventResult {
  event: Event | null
  loading: boolean
  error: string | null
}

export function useEvent(id: number | null): UseEventResult {
  const [event, setEvent] = useState<Event | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (id === null) return
    setLoading(true)
    setError(null)
    getById(id)
      .then(setEvent)
      .catch(() => setError('Impossible de charger cet événement.'))
      .finally(() => setLoading(false))
  }, [id])

  return { event, loading, error }
}
