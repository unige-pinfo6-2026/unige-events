import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '@/hooks/useAuth'
import {
  acceptInvitation,
  declineInvitation,
  getMyInvitations,
} from '@/services/coOrganizerApi'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'

interface UseCoOrganizerInvitationsResult {
  invitations: CoOrganizerInvitation[]
  pendingCount: number
  loading: boolean
  error: string | null
  accept: (eventId: number) => Promise<void>
  decline: (eventId: number) => Promise<void>
  refresh: () => Promise<void>
}

/**
 * Loads the caller's own pending co-organizer invitations. Idle when the user
 * is anonymous. Used by:
 *  - `CoOrganizerInvitationsBadge` in the navbar (compteur).
 *  - `CoOrganizerInvitationsList` in `ProfilePage` (self-view).
 *
 * Optimistic mutations: accept/decline remove the invitation locally before
 * confirmation. On error, the row is restored via a refresh.
 */
export function useCoOrganizerInvitations(): UseCoOrganizerInvitationsResult {
  const { isAuthenticated } = useAuth()
  const [invitations, setInvitations] = useState<CoOrganizerInvitation[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setInvitations([])
      return
    }
    setLoading(true)
    setError(null)
    try {
      const list = await getMyInvitations({ status: 'PENDING' })
      setInvitations(list)
    } catch {
      setError('Impossible de charger vos invitations.')
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const accept = useCallback(
    async (eventId: number) => {
      const previous = invitations
      setInvitations((prev) => prev.filter((inv) => inv.event.id !== eventId))
      try {
        await acceptInvitation(eventId)
      } catch {
        setInvitations(previous)
        await refresh()
      }
    },
    [invitations, refresh],
  )

  const decline = useCallback(
    async (eventId: number) => {
      const previous = invitations
      setInvitations((prev) => prev.filter((inv) => inv.event.id !== eventId))
      try {
        await declineInvitation(eventId)
      } catch {
        setInvitations(previous)
        await refresh()
      }
    },
    [invitations, refresh],
  )

  return {
    invitations,
    pendingCount: invitations.length,
    loading,
    error,
    accept,
    decline,
    refresh,
  }
}
