import api from './api'
import type { ReportReason } from '@/types/report'

export interface ReportEventRequest {
  reason: ReportReason
  description?: string
}

/**
 * Body de `POST /api/events/{id}/report` ET `POST /api/comments/{id}/report`
 * (SCRUM-144 / SCRUM-147). Le backend valide `reason` via Bean Validation
 * (non-null + enum). `description` est optionnel (max 2000 chars).
 */
export interface ReportCommentRequest {
  reason: ReportReason
  description?: string
}

export async function reportEvent(eventId: number, body: ReportEventRequest): Promise<void> {
  await api.post(`/events/${eventId}/report`, body)
}

/**
 * Signale un commentaire pour modération admin — SCRUM-147.
 *
 * Codes applicatifs côté UI :
 *  - `201` : signalement créé.
 *  - `409 already_reported` : UK partiel `(reporterId, commentId)` ; le caller
 *    a déjà signalé ce commentaire. UX : toast "Vous avez déjà signalé ce
 *    commentaire." + close modal.
 *  - `422 cannot_report_own_comment` : `caller.uuid == comment.authorId`.
 *    Ne devrait pas se produire (le bouton est masqué pour l'auteur côté UI),
 *    filet défensif.
 */
export async function reportComment(commentId: number, body: ReportCommentRequest): Promise<void> {
  await api.post(`/comments/${commentId}/report`, body)
}
