/**
 * Comment DTO returned by `GET /api/events/{eventId}/comments` (paginated on
 * top-level comments, with replies inlined under `replies[]`) and by
 * `POST /api/events/{eventId}/comments` (single new comment, empty replies).
 *
 * Schema is frozen by openapi.yaml/CommentDTO (SCRUM-139).
 */
export interface Comment {
  id: number
  content: string
  authorId: string | null
  authorDisplayName: string | null
  authorAvatarUrl: string | null
  /**
   * Username public-facing de l'auteur (SCRUM-169). Permet à `CommentItem`
   * d'afficher `@username` en fallback de `displayName` sans round-trip.
   * `null` uniquement sur ligne orpheline (user supprimé sans cascade).
   */
  authorUsername: string | null
  authorIsOrganizer: boolean
  likeCount: number
  likedByMe: boolean
  createdAt: string
  parentCommentId: number | null
  replies: Comment[]
}

/** Body of `POST /api/events/{eventId}/comments`. */
export interface CreateCommentRequest {
  content: string
  parentCommentId?: number | null
}
