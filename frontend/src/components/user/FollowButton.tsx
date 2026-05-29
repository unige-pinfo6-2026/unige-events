import { useEffect, useState } from 'react'
import { followUser, unfollowUser } from '@/services/followApi'
import { useToast } from '@/hooks/useToast'
import ConfirmDialog from '@/components/utils/ConfirmDialog'
import type { FollowStatus } from '@/types/user'

interface FollowButtonProps {
  targetId: string
  followStatus?: FollowStatus | null
  /** Called after a successful mutation so the parent can refetch the profile. */
  onMutated?: () => void
  /**
   * Quand `true`, un clic sur "Se désabonner" ouvre une modale de confirmation
   * avant d'envoyer la requête DELETE — utile pour les profils privés où
   * se désabonner perd l'accès au contenu.
   */
  confirmOnUnfollow?: boolean
}

/**
 * Three-state follow button (SCRUM-110 — US-21):
 *
 * - `null` → "Suivre" (primary CTA). Click POSTs `/users/{id}/follow`. Server
 *   auto-resolves to ACCEPTED (public target) or PENDING (private target).
 * - `'PENDING'` → "Demande envoyée" (muted). Click DELETEs to cancel the
 *   request. Native `title` tooltip "Cliquer pour annuler".
 * - `'ACCEPTED'` → "Abonné" by default, "Se désabonner" on hover (CSS
 *   group/group-hover swap — no JS / animation lib). Click DELETEs.
 *
 * No optimistic state flip on DELETE — the button stays in its current state
 * (disabled) while the request is in flight, and the parent's refetch drives
 * the final state. Avoids the PENDING→idle→PENDING flash that an optimistic
 * flip causes when `pending` clears before the prop update arrives.
 *
 * DELETE is idempotent server-side (204 even when no row exists), so
 * "se désabonner" / "annuler la demande" never produce a 404 toast.
 */
type LocalState = 'idle' | 'pending' | 'accepted'

const followButtonVariants = {
  idle:     'border-0 bg-linear-to-r from-accent to-pink-600 hover:from-accent/90 hover:to-pink-600/90 text-white shadow-lg shadow-accent/30',
  pending:  'border-2 border-border/60 bg-foreground/5 text-foreground/60 hover:border-foreground/30 hover:text-foreground/80',
  accepted: 'border-2 border-border bg-transparent text-foreground hover:border-error/50 hover:text-error',
} as const

const baseClass =
  'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background'

function toLocalState(status: FollowStatus | null | undefined): LocalState {
  if (status === 'PENDING') return 'pending'
  if (status === 'ACCEPTED') return 'accepted'
  return 'idle'
}

export default function FollowButton({
  targetId,
  followStatus,
  onMutated,
  confirmOnUnfollow = false,
}: Readonly<FollowButtonProps>) {
  const toast = useToast()
  const [state, setState] = useState<LocalState>(toLocalState(followStatus))
  const [pending, setPending] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  // Sync local state from the parent's followStatus prop while no mutation is
  // in flight. The parent's refetch lands and this effect adopts the
  // server-authoritative value — important for the auto-ACCEPT case where the
  // user clicks "Suivre" on a public profile: the server resolves to ACCEPTED,
  // the parent refetches, and we flip to ACCEPTED here.
  useEffect(() => {
    if (pending) return
    setState(toLocalState(followStatus))
  }, [followStatus, pending])

  // The button is idempotent on the server, but the UI carries an in-flight
  // guard so a double-click during the network round-trip can't queue two
  // mutations.
  async function handleUnfollow() {
    if (pending) return
    setPending(true)
    const previousState = state
    try {
      await unfollowUser(targetId)
      onMutated?.()
    } catch {
      setState(previousState)
      toast.showToast('error', 'Impossible de mettre à jour le suivi.')
    } finally {
      setPending(false)
    }
  }

  async function handleFollow() {
    if (pending) return
    setPending(true)
    const previousState = state
    try {
      // No optimistic flip — the server auto-accepts for public targets and
      // returns PENDING for private ones. We let the parent refetch (via
      // onMutated) drive the state so there's no "Demande envoyée" → "Abonné"
      // flash on public profiles. The button stays disabled during the request.
      await followUser(targetId)
      onMutated?.()
    } catch {
      setState(previousState)
      toast.showToast('error', 'Impossible de mettre à jour le suivi.')
    } finally {
      setPending(false)
    }
  }

  function handleUnfollowClick() {
    if (confirmOnUnfollow) {
      setShowConfirm(true)
    } else {
      void handleUnfollow()
    }
  }

  // aria-pressed is the canonical pattern for a toggle button (cf. WAI-ARIA
  // toggle pattern): "false" when not following, "true" otherwise. PENDING
  // is reported as pressed since the user did request to follow — they're
  // toggling that "intent" off when they cancel.
  const ariaPressed = state !== 'idle'

  if (state === 'pending') {
    return (
      <button
        type="button"
        onClick={handleUnfollowClick}
        disabled={pending}
        aria-pressed={ariaPressed}
        aria-label="Annuler la demande de suivi"
        title="Cliquer pour annuler"
        className={`${baseClass} ${followButtonVariants.pending}`}
      >
        Demande envoyée
      </button>
    )
  }

  if (state === 'accepted') {
    return (
      <>
        <button
          type="button"
          onClick={handleUnfollowClick}
          disabled={pending}
          aria-pressed={ariaPressed}
          aria-label="Se désabonner"
          title="Se désabonner"
          className={`group ${baseClass} ${followButtonVariants.accepted}`}
        >
          <span className="group-hover:hidden">Abonné</span>
          <span className="hidden group-hover:inline">Se désabonner</span>
        </button>
        {showConfirm && (
          <ConfirmDialog
            title="Se désabonner ?"
            message="Ce compte est privé. En vous désabonnant, vous perdrez l'accès à son profil et devrez envoyer une nouvelle demande pour le suivre à nouveau."
            confirmLabel="Se désabonner"
            pending={pending}
            onConfirm={() => { setShowConfirm(false); void handleUnfollow() }}
            onClose={() => setShowConfirm(false)}
          />
        )}
      </>
    )
  }

  return (
    <button
      type="button"
      onClick={() => void handleFollow()}
      disabled={pending}
      aria-pressed={ariaPressed}
      aria-label="Suivre cet utilisateur"
      title="Suivre"
      className={`${baseClass} ${followButtonVariants.idle}`}
    >
      Suivre
    </button>
  )
}
