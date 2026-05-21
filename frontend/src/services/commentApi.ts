import api from '@/services/api'
import type { Comment, CreateCommentRequest } from '@/types/comment'

/**
 * Comment API client (SCRUM-146 / SCRUM-139 / SCRUM-144 / SCRUM-147).
 * Backend endpoints owned by engagement-service (post-finalization, comment
 * sub-package).
 *
 * Pagination is on the top-level comments only. Each comment's `replies[]` is
 * inlined server-side via a 2-query batch — the frontend doesn't need a
 * second roundtrip for replies.
 */

export interface GetCommentsParams {
  page?: number
  size?: number
}

export async function getEventComments(
  eventId: number,
  params: GetCommentsParams = {},
): Promise<Comment[]> {
  const { data } = await api.get<Comment[]>(`/events/${eventId}/comments`, { params })
  return data
}

export async function postComment(
  eventId: number,
  content: string,
  parentCommentId?: number | null,
): Promise<Comment> {
  const body: CreateCommentRequest = { content, parentCommentId: parentCommentId ?? null }
  const { data } = await api.post<Comment>(`/events/${eventId}/comments`, body)
  return data
}

export async function deleteComment(commentId: number): Promise<void> {
  await api.delete(`/comments/${commentId}`)
}

/**
 * Body of `POST /api/comments/{commentId}/like` — SCRUM-144 backend.
 * `liked` is always `true` (the caller is now associated with a like) ;
 * `likeCount` is the post-operation count.
 */
export interface CommentLikeResponse {
  liked: boolean
  likeCount: number
}

/**
 * Pose un like sur le commentaire pour le caller authentifié — SCRUM-147.
 * Idempotent : un second POST du même caller renvoie `200` avec le même
 * compteur, pas de double-incrément.
 *
 * Codes de retour : `201` premier like, `200` idempotent, `401` non
 * authentifié, `404` commentaire inexistant.
 */
export async function likeComment(commentId: number): Promise<CommentLikeResponse> {
  const { data } = await api.post<CommentLikeResponse>(`/comments/${commentId}/like`)
  return data
}

/**
 * Retire le like du caller sur le commentaire — SCRUM-147. Idempotent :
 * `DELETE` sur un like inexistant renvoie `204` aussi.
 */
export async function unlikeComment(commentId: number): Promise<void> {
  await api.delete(`/comments/${commentId}/like`)
}
