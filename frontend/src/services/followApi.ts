import api from './api'
import type { FollowDTO } from '@/types/follow'

/**
 * Follow a user. Backend auto-resolves the status: `ACCEPTED` if the target's
 * profile is public, `PENDING` if private (cf. SCRUM-138 auto-accept rule).
 *
 * Error envelope from the API:
 * - 409 `already_following` if the row already exists (any status).
 * - 422 `cannot_follow_self` if the caller tries to follow their own UUID.
 * - 429 if the per-user rate limit (`follows.follow`, 30/window) is exceeded.
 */
export async function followUser(targetId: string): Promise<FollowDTO> {
  const response = await api.post<FollowDTO>(`/users/${targetId}/follow`)
  return response.data
}

/**
 * Unfollow a user or cancel a pending request. **Idempotent** — the backend
 * returns 204 even when no row existed. The caller should never see a 404 toast.
 */
export async function unfollowUser(targetId: string): Promise<void> {
  await api.delete(`/users/${targetId}/follow`)
}

/**
 * PENDING follow requests received by the authenticated user. The DTOs are
 * id-only; the panel resolves `getPublicProfile(followerId)` per row to
 * display avatar + displayName.
 */
export async function getMyFollowRequests(): Promise<FollowDTO[]> {
  const response = await api.get<FollowDTO[]>('/users/me/follow-requests')
  return response.data
}

/**
 * Accept a PENDING follow request. Reserved to the request's target (`followed`).
 * 403 if the caller isn't the target. 409 `invalid_transition` if already ACCEPTED.
 */
export async function acceptFollowRequest(followId: number): Promise<FollowDTO> {
  const response = await api.patch<FollowDTO>(`/follow-requests/${followId}/accept`)
  return response.data
}

/**
 * Reject a PENDING follow request. Physically deletes the row so the
 * follower can retry later without 409. 204 on success.
 */
export async function rejectFollowRequest(followId: number): Promise<void> {
  await api.patch(`/follow-requests/${followId}/reject`)
}
