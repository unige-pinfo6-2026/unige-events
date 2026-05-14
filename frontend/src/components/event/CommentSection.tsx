import { Skeleton } from 'boneyard-js/react'
import { Link } from 'react-router-dom'
import { MessageSquare } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useComments } from '@/hooks/useComments'
import { useToast } from '@/hooks/useToast'
import CommentForm from '@/components/event/CommentForm'
import CommentItem from '@/components/event/CommentItem'
import { ButtonNeutral } from '@/components/utils/Buttons'
import type { EventStatus } from '@/types/event'

interface Props {
  eventId: number
  eventCreatorId: string
  eventStatus: EventStatus
}

/**
 * Comments section inserted on `EventDetailPage` (SCRUM-146). Renders:
 * - Sign-in CTA if user is anonymous.
 * - Backend-disabled banner if event isn't PUBLISHED (no posting allowed,
 *   but existing comments could be shown — we currently hide the form only).
 * - The new-comment form (auth + PUBLISHED only).
 * - The list of comments + reply chains.
 * - Pagination "Charger plus de commentaires".
 *
 * Replies are 1 level max (cf. SCRUM-139 backend rules); `CommentItem` enforces
 * it visually by suppressing the "Répondre" button on `isReply`.
 */
export default function CommentSection({
  eventId,
  eventCreatorId,
  eventStatus,
}: Readonly<Props>) {
  const { user, isAdmin } = useAuth()
  const { showToast } = useToast()
  const {
    comments,
    hasMore,
    loading,
    posting,
    error,
    post,
    postReply,
    remove,
    loadMore,
  } = useComments(eventId)

  const isPublished = eventStatus === 'PUBLISHED'
  const isAnonymous = user === null

  async function handlePost(content: string) {
    const outcome = await post(content)
    if (!outcome.ok) {
      showToast('error', 'Impossible de publier votre commentaire.')
    }
    return outcome
  }

  function handleLoadMore() {
    loadMore().catch(() => {
      /* fetchPage already surfaces the error via the `error` state. */
    })
  }

  return (
    <section
      aria-label="Commentaires"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border"
    >
      <header className="flex items-center gap-3 mb-5">
        <div className="w-10 h-10 rounded-2xl bg-linear-to-br from-accent/30 to-pink-600/30 flex items-center justify-center">
          <MessageSquare className="w-5 h-5 text-accent" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-foreground">Commentaires</h2>
          <p className="text-xs text-foreground/60">
            Échangez avec les autres participants autour de cet événement.
          </p>
        </div>
      </header>

      {/* Form area */}
      {isAnonymous && (
        <p className="text-sm text-foreground/60 mb-5 p-4 rounded-2xl border border-border bg-background/30">
          <Link to="/login" className="text-link underline-offset-2 hover:underline">
            Connectez-vous
          </Link>{' '}
          pour commenter cet événement.
        </p>
      )}
      {!isAnonymous && !isPublished && (
        <p className="text-sm text-foreground/60 mb-5 italic">
          Les commentaires sont désactivés sur cet événement (statut « {eventStatus} »).
        </p>
      )}
      {!isAnonymous && isPublished && (
        <div className="mb-5">
          <CommentForm onSubmit={handlePost} submitting={posting} />
        </div>
      )}

      {/* List area */}
      {loading && comments.length === 0 && (
        <Skeleton name="comments" loading={true}>
          <div className="space-y-3">
            <div className="h-24 rounded-2xl" />
            <div className="h-24 rounded-2xl" />
            <div className="h-24 rounded-2xl" />
          </div>
        </Skeleton>
      )}

      {!loading && error && (
        <p className="text-sm text-error">{error}</p>
      )}

      {!loading && !error && comments.length === 0 && (
        <p className="text-sm text-foreground/50 italic">
          Aucun commentaire — soyez le premier.
        </p>
      )}

      {comments.length > 0 && (
        <ul className="space-y-3">
          {comments.map((c) => (
            <CommentItem
              key={c.id}
              comment={c}
              eventCreatorId={eventCreatorId}
              currentUserId={user?.id ?? null}
              isAdmin={isAdmin}
              onReply={postReply}
              onDelete={remove}
              posting={posting}
            />
          ))}
        </ul>
      )}

      {hasMore && (
        <div className="mt-4 flex justify-center">
          <ButtonNeutral onClick={handleLoadMore} disabled={loading} size="sm">
            Charger plus de commentaires
          </ButtonNeutral>
        </div>
      )}
    </section>
  )
}
