import { useEffect, useState } from 'react'
import { getOrganizerUuids } from '@/services/eventApi'
import { getUserById } from '@/services/userService'

export interface PublicOrganizer {
  userId: string
  displayName: string | null
  username: string | null
  avatarUrl: string | null
}

interface UsePublicOrganizersResult {
  coOrganizers: PublicOrganizer[]
  loading: boolean
}

/**
 * Resolves the ACCEPTED co-organizers of an event for the PUBLIC event detail
 * page — works for anonymous viewers, unlike `useCoOrganizers` which hits the
 * `@Authenticated` `GET /events/{id}/co-organizers` (401 for anonymous).
 *
 * Two public hops:
 *  1. `GET /events/{id}/organizer-uuids` (`@PermitAll`, ADR-002) → creator +
 *     ACCEPTED co-org UUIDs (never PENDING/DECLINED, so nothing private leaks).
 *  2. `GET /users/{id}` (`@PermitAll`) per non-creator UUID → display name /
 *     username / avatar.
 *
 * The creator is rendered separately by `EventOrganizerTeam`, so it's filtered
 * out here. A co-org whose `/users/{id}` lookup fails (hard-deleted, transient)
 * is still listed with just its UUID rather than dropped — the row degrades to
 * the short UUID prefix instead of vanishing.
 */
export function usePublicOrganizers(
  eventId: number | null,
  creatorId: string,
): UsePublicOrganizersResult {
  const [coOrganizers, setCoOrganizers] = useState<PublicOrganizer[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (eventId == null) {
      setCoOrganizers([])
      return
    }
    let active = true
    setLoading(true)
    getOrganizerUuids(eventId)
      .then(async (uuids) => {
        const coOrgIds = uuids.filter((id) => id !== creatorId)
        const settled = await Promise.allSettled(coOrgIds.map((id) => getUserById(id)))
        if (!active) return
        const resolved: PublicOrganizer[] = settled.map((r, i) => {
          if (r.status === 'fulfilled' && r.value) {
            const u = r.value
            return {
              userId: u.id,
              displayName: u.displayName ?? null,
              username: u.username ?? null,
              avatarUrl: u.avatarUrl ?? null,
            }
          }
          // Lookup failed (deleted / unreachable) — keep the row with just the UUID.
          return { userId: coOrgIds[i], displayName: null, username: null, avatarUrl: null }
        })
        setCoOrganizers(resolved)
      })
      .catch(() => {
        if (active) setCoOrganizers([])
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [eventId, creatorId])

  return { coOrganizers, loading }
}
