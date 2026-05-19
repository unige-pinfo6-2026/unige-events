import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Flag, Heart, MessageSquare, Trash2 } from 'lucide-react'
import { formatRelativeTime } from '@/utils/formatRelativeTime'
import { userDisplayLabel, userInitials } from '@/utils/displayName'
import { useCommentLike } from '@/hooks/useCommentLike'
import { useReportComment } from '@/hooks/useReportComment'
import CommentForm from '@/components/event/CommentForm'
import ReportModal from '@/components/event/ReportModal'
import ConfirmDialog from '@/components/utils/ConfirmDialog'
import type { Comment } from '@/types/comment'

interface Props {
  comment: Comment
  eventCreatorId: string
  currentUserId: string | null
  isAdmin: boolean
  isReply?: boolean
  onReply: (parentCommentId: number, content: string) => Promise<{ ok: boolean }>
  onDelete: (commentId: number) => Promise<void>
  posting: boolean
}

/**
 * Card rendering one comment + its replies (max 1 nesting level — replies
 * cannot reply, per SCRUM-139 backend rules).
 *
 * Visibility rules:
 * - "Répondre" : visible only on top-level (`!isReply`) and only to logged-in users.
 * - "Supprimer" : auteur OR créateur de l'event OR admin (co-org ACCEPTED also,
 *   but we don't know that client-side without an extra call → backend enforces).
 * - "Signaler" : informational toast for now (Décision B — full feature in SCRUM-144).
 */
export default function CommentItem({
  comment,
  eventCreatorId,
  currentUserId,
  isAdmin,
  isReply = false,
  onReply,
  onDelete,
  posting,
}: Readonly<Props>) {
  const [showReplyForm, setShowReplyForm] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showReportModal, setShowReportModal] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const isAuthor = currentUserId !== null && comment.authorId === currentUserId
  const isEventCreator = currentUserId !== null && currentUserId === eventCreatorId
  const canDelete = isAuthor || isEventCreator || isAdmin
  const canReply = !isReply && currentUserId !== null
  const canLike = currentUserId !== null
  // SCRUM-147 — l'auteur du commentaire ne peut pas se signaler lui-même
  // (le backend renvoie 422 cannot_report_own_comment de toute façon).
  const canReport = currentUserId !== null && !isAuthor

  // SCRUM-147 — optimistic like/unlike. Disabled for anonymous callers ;
  // the hook is still wired but `toggle()` is gated below.
  const { liked, likeCount, toggling, toggle } = useCommentLike(
    comment.id,
    comment.likedByMe,
    comment.likeCount,
  )

  // SCRUM-147 — report flow. Wired through ReportModal (target='comment').
  const { submitting: reporting, submit: submitReport } = useReportComment(comment.id)

  const initials = userInitials(comment.authorDisplayName)
  // SCRUM-169 — label fallback order: displayName -> @username -> UUID prefix.
  const authorLabel = userDisplayLabel(
    comment.authorDisplayName,
    comment.authorUsername,
    comment.authorId,
  )
  // SCRUM-169 — link to `/profile/{username}`. Falls back to the UUID when the
  // username is missing (orphan row, hard-deleted user) — `ProfilePage` then
  // redirects the legacy UUID URL to the canonical username one. The author is
  // unknown only when `authorId` is also null, in which case we skip the link.
  const profileSlug = comment.authorUsername ?? comment.authorId

  async function confirmDelete() {
    if (deleting) return
    setDeleting(true)
    try {
      await onDelete(comment.id)
    } finally {
      setDeleting(false)
      setShowDeleteConfirm(false)
    }
  }

  async function handleReplySubmit(content: string) {
    const outcome = await onReply(comment.id, content)
    if (outcome.ok) setShowReplyForm(false)
    return outcome
  }

  // SCRUM-147 — wraps the hook so the modal's `onSubmit` matches the
  // expected `(reason, description?) => Promise<void>` shape ; the hook
  // returns a boolean we translate into "close on true, stay on false".
  async function handleReportSubmit(reason: import('@/types/report').ReportReason, description?: string) {
    const shouldClose = await submitReport({ reason, description })
    if (shouldClose) setShowReportModal(false)
  }

  return (
    <li className={isReply ? 'pl-8 border-l-2 border-border ml-4' : ''}>
      <article className="flex gap-3 p-3 rounded-2xl bg-background/30 border border-border">
        {profileSlug ? (
          <Link
            to={`/profile/${profileSlug}`}
            aria-label={`Voir le profil de ${authorLabel}`}
            className="w-9 h-9 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden hover:opacity-80 transition-opacity"
          >
            {comment.authorAvatarUrl ? (
              <img src={comment.authorAvatarUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <span className="text-xs font-semibold text-foreground/60">{initials}</span>
            )}
          </Link>
        ) : (
          <div className="w-9 h-9 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden">
            {comment.authorAvatarUrl ? (
              <img src={comment.authorAvatarUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <span className="text-xs font-semibold text-foreground/60">{initials}</span>
            )}
          </div>
        )}
        <div className="flex-1 min-w-0">
          <header className="flex flex-wrap items-baseline gap-x-2 gap-y-1 mb-1">
            {profileSlug ? (
              <Link
                to={`/profile/${profileSlug}`}
                className="text-sm font-semibold text-foreground no-underline hover:underline"
              >
                {authorLabel}
              </Link>
            ) : (
              <span className="text-sm font-semibold text-foreground">
                {authorLabel}
              </span>
            )}
            {comment.authorIsOrganizer && (
              <span className="px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-accent/15 text-accent border border-accent/30">
                Organisateur
              </span>
            )}
            <span className="text-xs text-foreground/40">
              {formatRelativeTime(comment.createdAt)}
            </span>
          </header>
          <p className="text-sm text-foreground/80 whitespace-pre-wrap break-words">
            {comment.content}
          </p>
          <footer className="flex flex-wrap items-center gap-3 mt-2 text-xs">
            <button
              type="button"
              onClick={() => { if (canLike) void toggle() }}
              disabled={!canLike || toggling}
              aria-label={liked ? 'Retirer le like' : 'Aimer ce commentaire'}
              aria-pressed={liked}
              title={canLike ? undefined : 'Connectez-vous pour aimer'}
              className={`inline-flex items-center gap-1 bg-transparent border-0 p-0 transition-colors ${
                canLike ? 'cursor-pointer' : 'cursor-not-allowed'
              } ${
                liked ? 'text-error' : 'text-foreground/50 hover:text-error'
              } ${toggling ? 'opacity-60' : ''}`}
            >
              <Heart className={`w-3 h-3 ${liked ? 'fill-current' : ''}`} />
              {likeCount > 0 && <span>{likeCount}</span>}
            </button>
            {canReply && (
              <button
                type="button"
                onClick={() => setShowReplyForm((p) => !p)}
                className="inline-flex items-center gap-1 text-foreground/50 hover:text-accent transition-colors bg-transparent border-0 p-0 cursor-pointer"
              >
                <MessageSquare className="w-3 h-3" />
                Répondre
              </button>
            )}
            {canDelete && (
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(true)}
                className="inline-flex items-center gap-1 text-foreground/50 hover:text-error transition-colors bg-transparent border-0 p-0 cursor-pointer"
              >
                <Trash2 className="w-3 h-3" />
                Supprimer
              </button>
            )}
            {canReport && (
              <button
                type="button"
                onClick={() => setShowReportModal(true)}
                className="inline-flex items-center gap-1 text-foreground/40 hover:text-foreground/70 transition-colors bg-transparent border-0 p-0 cursor-pointer"
                aria-label="Signaler ce commentaire"
              >
                <Flag className="w-3 h-3" />
                Signaler
              </button>
            )}
          </footer>
          {showReplyForm && (
            <div className="mt-3">
              <CommentForm
                onSubmit={handleReplySubmit}
                submitting={posting}
                placeholder={`Répondre à ${authorLabel}…`}
                onCancel={() => setShowReplyForm(false)}
                autoFocus
                submitLabel="Répondre"
                // SCRUM-147 — pre-fill `@<parentAuthorUsername> ` so the
                // reply pings the parent author by default. Fall back to
                // an empty prefix when authorUsername is missing (orphan
                // row, hard-deleted user) so the user isn't blocked.
                initialValue={comment.authorUsername ? `@${comment.authorUsername} ` : ''}
              />
            </div>
          )}
        </div>
      </article>
      {comment.replies.length > 0 && (
        <ul className="mt-2 space-y-2">
          {comment.replies.map((reply) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              eventCreatorId={eventCreatorId}
              currentUserId={currentUserId}
              isAdmin={isAdmin}
              isReply
              onReply={onReply}
              onDelete={onDelete}
              posting={posting}
            />
          ))}
        </ul>
      )}
      {showDeleteConfirm && (
        <ConfirmDialog
          title="Supprimer ce commentaire ?"
          message="Cette action est irréversible — le commentaire et ses réponses éventuelles seront définitivement supprimés."
          confirmLabel={deleting ? 'Suppression…' : 'Confirmer'}
          pending={deleting}
          onConfirm={() => { void confirmDelete() }}
          onClose={() => setShowDeleteConfirm(false)}
        />
      )}
      {showReportModal && (
        <ReportModal
          target="comment"
          onClose={() => setShowReportModal(false)}
          onSubmit={handleReportSubmit}
          submitting={reporting}
        />
      )}
    </li>
  )
}
