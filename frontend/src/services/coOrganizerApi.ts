import api from '@/services/api'
import type {
  CoOrganizer,
  CoOrganizerInvitation,
  CoOrganizerStatus,
  InviteCoOrganizerRequest,
} from '@/types/coOrganizer'

/**
 * Co-organizer API client (SCRUM-137).
 * Backend endpoints owned by event-service (post-finalization, co-organizer
 * sub-package). Cf. `openapi/openapi.yaml` paths /events/{id}/co-organizers*
 * and /users/me/co-organizer-invitations.
 *
 * Decision A of the spec: invitation is by UUID, not by user search
 * (`GET /users/search` does not exist). The frontend exposes a UUID field; the
 * backend returns 404 if the UUID is unknown.
 */

export async function inviteCoOrganizer(
  eventId: number,
  userId: string,
): Promise<CoOrganizer> {
  const body: InviteCoOrganizerRequest = { userId }
  const { data } = await api.post<CoOrganizer>(
    `/events/${eventId}/co-organizers`,
    body,
  )
  return data
}

export async function listCoOrganizers(eventId: number): Promise<CoOrganizer[]> {
  const { data } = await api.get<CoOrganizer[]>(`/events/${eventId}/co-organizers`)
  return data
}

export async function removeCoOrganizer(
  eventId: number,
  userId: string,
): Promise<void> {
  await api.delete(`/events/${eventId}/co-organizers/${userId}`)
}

export async function acceptInvitation(eventId: number): Promise<CoOrganizer> {
  const { data } = await api.patch<CoOrganizer>(
    `/events/${eventId}/co-organizers/me/accept`,
  )
  return data
}

export async function declineInvitation(eventId: number): Promise<void> {
  await api.patch(`/events/${eventId}/co-organizers/me/decline`)
}

export interface GetMyInvitationsParams {
  status?: CoOrganizerStatus
  page?: number
  size?: number
}

export async function getMyInvitations(
  params: GetMyInvitationsParams = {},
): Promise<CoOrganizerInvitation[]> {
  const { data } = await api.get<CoOrganizerInvitation[]>(
    '/users/me/co-organizer-invitations',
    { params },
  )
  return data
}
