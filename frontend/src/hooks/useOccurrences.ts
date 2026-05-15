import { useEffect, useRef, useState } from 'react'
import { getOccurrences } from '@/services/eventApi'
import type { Event } from '@/types/event'

interface UseOccurrencesOptions {
  enabled: boolean
}

interface UseOccurrencesResult {
  loading: boolean
  error: string | null
  data: Event[] | null
}

/**
 * Lazy fetch of a recurring parent's occurrences. `enabled: false` short-circuits
 * the effect entirely so the network call only fires once the user explicitly
 * opens the collapsible section in `EventDetailPage` (cf. Décision G).
 *
 * Data is kept across an `enabled` flip from true → false so re-opening the
 * section is instantaneous.
 */
export function useOccurrences(
  parentId: number | null,
  { enabled }: UseOccurrencesOptions,
): UseOccurrencesResult {
  const [data, setData] = useState<Event[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Monotonic counter — in-flight responses whose id no longer matches the
  // latest fetch are silently dropped (avoids a setState after unmount / refetch).
  const requestIdRef = useRef(0)

  useEffect(() => {
    if (!enabled || parentId === null) return

    requestIdRef.current += 1
    const myRequestId = requestIdRef.current
    const isCurrent = () => requestIdRef.current === myRequestId

    setLoading(true)
    setError(null)
    getOccurrences(parentId)
      .then((occurrences) => {
        if (!isCurrent()) return
        setData(occurrences)
      })
      .catch(() => {
        if (!isCurrent()) return
        setError('Impossible de charger les occurrences.')
        setData(null)
      })
      .finally(() => {
        if (!isCurrent()) return
        setLoading(false)
      })

    return () => {
      requestIdRef.current += 1
    }
  }, [parentId, enabled])

  return { loading, error, data }
}
