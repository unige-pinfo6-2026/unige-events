import type { FollowStatus } from '@/types/user'

/**
 * Row `Follow` returned by the SCRUM-138 follow endpoints. Id-only payload —
 * the backend does NOT enrich with `displayName` / `avatarUrl`, so the
 * frontend resolves `GET /users/{followerId}` per row when it needs to
 * render the requester's identity (cf. SCRUM-110 received-requests panel).
 *
 * Schema source: `openapi/openapi.yaml#/components/schemas/FollowDTO`.
 */
export interface FollowDTO {
  id: number
  followerId: string
  followedId: string
  status: FollowStatus
  createdAt: string
}
