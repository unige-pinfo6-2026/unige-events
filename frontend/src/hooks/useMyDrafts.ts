import { useEffect, useState } from 'react'
import { getMyDrafts } from '@/services/eventApi'
import type { Event } from '@/types/event'

export const DRAFTS_FETCH_SIZE = 10

export interface UseMyDraftsResult {
  drafts: Event[]
  loading: boolean
  error: string | null
}

function sortByUpdatedAtDesc(drafts: Event[]): Event[] {
  return [...drafts].sort((a, b) => {
    const aTime = new Date(a.updatedAt ?? a.createdAt).getTime()
    const bTime = new Date(b.updatedAt ?? b.createdAt).getTime()
    return bTime - aTime
  })
}

export function useMyDrafts(organizerId: string | undefined): UseMyDraftsResult {
  const [drafts, setDrafts] = useState<Event[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!organizerId) {
      setDrafts([])
      setLoading(true)
      setError(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    getMyDrafts(organizerId, DRAFTS_FETCH_SIZE)
      .then(result => {
        if (cancelled) return
        setDrafts(sortByUpdatedAtDesc(result))
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        console.warn('[useMyDrafts] failed to load drafts', err)
        setDrafts([])
        setError('Erreur de chargement des brouillons')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [organizerId])

  return { drafts, loading, error }
}
