import { useState } from 'react'
import { Flag, MessageSquare, Trash2 } from 'lucide-react'
import { formatRelativeTime } from '@/utils/formatRelativeTime'
import { userDisplayLabel, userInitials } from '@/utils/displayName'
import { useToast } from '@/hooks/useToast'
import CommentForm from '@/components/event/CommentForm'
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
  const [deleting, setDeleting] = useState(false)
  const { showToast } = useToast()

  const isAuthor = currentUserId !== null && comment.authorId === currentUserId
  const isEventCreator = currentUserId !== null && currentUserId === eventCreatorId
  const canDelete = isAuthor || isEventCreator || isAdmin
  const canReply = !isReply && currentUserId !== null

  const initials = userInitials(comment.authorDisplayName)
  // SCRUM-169 — Comment payload doesn't yet carry an `authorUsername`
  // (separate follow-up against engagement-service). Pass `null` for
  // username so the helper falls back to UUID prefix → "Utilisateur".
  const authorLabel = userDisplayLabel(comment.authorDisplayName, null, comment.authorId)

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

  function handleReport() {
    // Décision B (spec) : le signalement de commentaire est scope-réduit à un
    // toast informatif en attendant SCRUM-144. Le projet n'a pas de variant
    // 'info' pour Toast — on utilise 'error' qui a la sémantique "ce que tu
    // viens de faire n'a pas marché", la plus proche d'un avertissement.
    showToast(
      'error',
      'Le signalement de commentaire arrive bientôt. En attendant, vous pouvez signaler l\'événement complet.',
    )
  }

  return (
    <li className={isReply ? 'pl-8 border-l-2 border-border ml-4' : ''}>
      <article className="flex gap-3 p-3 rounded-2xl bg-background/30 border border-border">
        <div className="w-9 h-9 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden">
          {comment.authorAvatarUrl ? (
            <img src={comment.authorAvatarUrl} alt="" className="w-full h-full object-cover" />
          ) : (
            <span className="text-xs font-semibold text-foreground/60">{initials}</span>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <header className="flex flex-wrap items-baseline gap-x-2 gap-y-1 mb-1">
            <span className="text-sm font-semibold text-foreground">
              {authorLabel}
            </span>
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
            {!isAuthor && currentUserId !== null && (
              <button
                type="button"
                onClick={handleReport}
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
    </li>
  )
}
