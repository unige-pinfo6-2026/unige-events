import api from '@/services/api'
import type { Comment, CreateCommentRequest } from '@/types/comment'

/**
 * Comment API client (SCRUM-146 / SCRUM-139).
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
