import api from './api'
import type { FollowDTO } from '@/types/follow'
import type { UserPublicResponse } from '@/types/user'

/**
 * Backend cap on `GET /users/{id}/followers` and `/following` page size
 * (cf. openapi.yaml). Used as the default fetch size by `useFollowList`
 * — exposed so tests can assert on the same constant.
 */
export const FOLLOW_LIST_PAGE_SIZE = 20

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
 * Remove a follower from the caller's own followers list — the mirror of
 * {@link unfollowUser}. Deletes the row where the caller is the `followed` and
 * `followerId` is the `follower`. **Idempotent** (204 even if no row existed),
 * silent (no notification to the dropped follower).
 */
export async function removeFollower(followerId: string): Promise<void> {
  await api.delete(`/users/me/followers/${followerId}`)
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

/**
 * Paginated list of users who follow `targetId` (SCRUM-138 /
 * SCRUM-142 — FE list view). Backend tri `Follow.createdAt DESC,
 * Follow.id DESC`. Items are projected as `UserPublicResponse` with
 * `followerCount`/`followingCount = 0` and `followStatus = null` —
 * the per-item follow relationship is not surfaced (the spec is
 * explicit: counters and followStatus only make sense on the target
 * profile, not on list items).
 *
 * 404 covers both "target user does not exist" AND "target profile
 * is private and caller is not owner" (anti-oracle ISSUE-93). The
 * page surfaces both uniformly via {@link ProfilePrivateState}.
 */
export async function getFollowers(
  targetId: string,
  page: number,
  size: number = FOLLOW_LIST_PAGE_SIZE,
): Promise<UserPublicResponse[]> {
  const response = await api.get<UserPublicResponse[]>(
    `/users/${targetId}/followers`,
    { params: { page, size } },
  )
  return response.data
}

/**
 * Paginated list of users that `targetId` follows. Same shape and
 * authorisation rules as {@link getFollowers}.
 */
export async function getFollowing(
  targetId: string,
  page: number,
  size: number = FOLLOW_LIST_PAGE_SIZE,
): Promise<UserPublicResponse[]> {
  const response = await api.get<UserPublicResponse[]>(
    `/users/${targetId}/following`,
    { params: { page, size } },
  )
  return response.data
}
