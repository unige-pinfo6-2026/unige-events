import { useCallback, useEffect, useState } from 'react'
import {
  inviteCoOrganizer,
  listCoOrganizers,
  removeCoOrganizer,
} from '@/services/coOrganizerApi'
import type { CoOrganizer } from '@/types/coOrganizer'

interface UseCoOrganizersResult {
  coOrganizers: CoOrganizer[]
  loading: boolean
  error: string | null
  invite: (userId: string) => Promise<{ ok: boolean; error?: string }>
  remove: (userId: string) => Promise<void>
  refresh: () => Promise<void>
}

/**
 * Loads + mutates the co-organizers of a given event. Null `eventId` means
 * the event doesn't exist yet (e.g. EventCreatePage before first save) — the
 * hook stays idle.
 *
 * Used by `CoOrganizersEditor` in EventForm (edit mode) and by
 * `EventOrganizerTeam` in EventDetailPage. The two consumers share the same
 * loader; we don't bother de-duplicating across renders since each consumer
 * mounts in a different page.
 */
export function useCoOrganizers(eventId: number | null): UseCoOrganizersResult {
  const [coOrganizers, setCoOrganizers] = useState<CoOrganizer[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (eventId == null) {
      setCoOrganizers([])
      return
    }
    setLoading(true)
    setError(null)
    try {
      const list = await listCoOrganizers(eventId)
      setCoOrganizers(list)
    } catch {
      setError('Impossible de charger les co-organisateurs.')
    } finally {
      setLoading(false)
    }
  }, [eventId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const invite = useCallback(
    async (userId: string): Promise<{ ok: boolean; error?: string }> => {
      if (eventId == null) return { ok: false, error: 'event_missing' }
      try {
        const created = await inviteCoOrganizer(eventId, userId)
        setCoOrganizers((prev) => [...prev, created])
        return { ok: true }
      } catch (e: unknown) {
        const status = extractHttpStatus(e)
        const message = mapInviteError(status)
        return { ok: false, error: message }
      }
    },
    [eventId],
  )

  const remove = useCallback(
    async (userId: string): Promise<void> => {
      if (eventId == null) return
      const previous = coOrganizers
      setCoOrganizers((prev) => prev.filter((c) => c.userId !== userId))
      try {
        await removeCoOrganizer(eventId, userId)
      } catch {
        setCoOrganizers(previous)
      }
    },
    [coOrganizers, eventId],
  )

  return { coOrganizers, loading, error, invite, remove, refresh }
}

function extractHttpStatus(e: unknown): number | null {
  if (typeof e === 'object' && e !== null && 'response' in e) {
    const response = (e as { response?: { status?: number } }).response
    return response?.status ?? null
  }
  return null
}

function mapInviteError(status: number | null): string {
  switch (status) {
    case 404:
      return 'Utilisateur introuvable — vérifiez l\'UUID.'
    case 409:
      return 'Cet utilisateur est déjà co-organisateur.'
    case 422:
      return 'Vous ne pouvez pas vous inviter vous-même.'
    case 403:
      return 'Vous n\'êtes pas autorisé à inviter sur cet événement.'
    default:
      return 'Erreur lors de l\'invitation.'
  }
}
