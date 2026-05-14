import type { Event } from '@/types/event'

/**
 * Co-organizer invitation status, as exposed by the backend
 * (cf. `CoOrganizerStatus` in `openapi/openapi.yaml`).
 *
 * `DECLINED` is a transient value returned by `PATCH .../decline`; the row is
 * physically removed right after, so it never appears in list responses
 * (`GET /events/{id}/co-organizers`, `GET /users/me/co-organizer-invitations`).
 * We keep it in the union for exhaustiveness in `switch` statements.
 */
export type CoOrganizerStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED'

/**
 * Representation of a co-organizer inside `GET /events/{id}/co-organizers`.
 * Privacy: `email`, `bio`, `faculty`, `studyLevel` are NOT exposed.
 */
export interface CoOrganizer {
  id: number
  userId: string
  displayName: string | null
  avatarUrl: string | null
  status: CoOrganizerStatus
  invitedAt: string
}

/**
 * Representation of one's own co-organizer invitation
 * (`GET /users/me/co-organizer-invitations`). Embeds the full `Event` so the UI
 * can render title / dates / banner without a follow-up fetch.
 */
export interface CoOrganizerInvitation {
  id: number
  event: Event
  status: CoOrganizerStatus
  invitedAt: string
}

/** Body of `POST /events/{id}/co-organizers`. Invitation by UUID (SCRUM-137 Decision A). */
export interface InviteCoOrganizerRequest {
  userId: string
}
