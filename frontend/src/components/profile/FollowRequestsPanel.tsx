import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, UserPlus, X } from 'lucide-react'
import { useMyFollowRequests } from '@/hooks/useMyFollowRequests'
import { useToast } from '@/hooks/useToast'
import UserAvatar from '@/components/user/UserAvatar'
import type { FollowRequestRow } from '@/hooks/useMyFollowRequests'

const cardClass =
  'bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-4'

const headingClass = 'text-xs font-bold uppercase tracking-widest text-foreground/30'

const acceptButtonClass =
  'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-emerald-500/40 bg-emerald-500/10 text-emerald-400 text-xs font-semibold cursor-pointer transition-colors hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed'

const rejectButtonClass =
  'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-border bg-transparent text-foreground/60 text-xs font-semibold cursor-pointer transition-colors hover:border-foreground/30 hover:text-foreground disabled:opacity-50 disabled:cursor-not-allowed'

function LoadingRows() {
  return (
    <div className="flex flex-col gap-2" aria-busy="true">
      {Array.from({ length: 2 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 p-2 rounded-2xl">
          <div className="size-10 rounded-full bg-foreground/10 shrink-0 animate-pulse" />
          <div className="flex-1 h-4 rounded bg-foreground/10 animate-pulse" />
          <div className="h-7 w-16 rounded-xl bg-foreground/10 animate-pulse" />
          <div className="h-7 w-16 rounded-xl bg-foreground/10 animate-pulse" />
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
      <UserPlus className="w-9 h-9 text-foreground/20" />
      <p className="text-foreground/50 text-sm font-medium">
        Aucune demande de suivi en attente.
      </p>
    </div>
  )
}

interface RowProps {
  row: FollowRequestRow
  /**
   * `true` when *any* row has an in-flight accept / reject — disables every
   * action button across the list, not only the in-flight one. Prevents the
   * UX glitch where clicks on other rows appeared interactive but were
   * silently ignored by the outer `pendingId !== null` guard.
   */
  busy: boolean
  onAccept: (id: number) => void
  onReject: (id: number) => void
}

function FollowRequestRowItem({ row, busy, onAccept, onReject }: Readonly<RowProps>) {
  const { request, follower } = row
  const displayName = follower?.displayName ?? 'Utilisateur'
  const profileHref = `/profile/${request.followerId}`

  return (
    <li className="flex items-center gap-3 py-2">
      <Link to={profileHref} className="shrink-0 no-underline">
        <UserAvatar user={follower} className="size-10" />
      </Link>
      <Link
        to={profileHref}
        className="flex-1 min-w-0 text-sm font-semibold text-foreground truncate no-underline hover:text-accent transition-colors"
      >
        {displayName}
      </Link>
      <button
        type="button"
        onClick={() => onAccept(request.id)}
        disabled={busy}
        aria-label={`Accepter la demande de ${displayName}`}
        className={acceptButtonClass}
      >
        <Check className="size-3.5" aria-hidden="true" />
        Accepter
      </button>
      <button
        type="button"
        onClick={() => onReject(request.id)}
        disabled={busy}
        aria-label={`Refuser la demande de ${displayName}`}
        className={rejectButtonClass}
      >
        <X className="size-3.5" aria-hidden="true" />
        Refuser
      </button>
    </li>
  )
}

/**
 * Owner-only section on `/profile/me` listing PENDING follow requests
 * received by the caller (SCRUM-110 — US-21). Each row renders the
 * requester's avatar + displayName (resolved per row in
 * {@link useMyFollowRequests} via `getPublicProfile` with `Promise.allSettled`
 * so a single 404 doesn't fail the whole batch), plus Accept / Reject buttons.
 *
 * Optimistic UI: clicked row disappears immediately; rolls back on error.
 * After the API call succeeds, the list is refreshed in case other rows
 * appeared concurrently.
 *
 * While one accept / reject is in flight, every Accept / Reject button across
 * every row is disabled (not just the in-flight row's) — the outer
 * `pendingId !== null` guard silently early-returns clicks on other rows,
 * so signaling "busy" globally is the honest UX.
 *
 * Counter staleness — known limitation: when the owner accepts a request,
 * their `followerCount` on `/profile/{ownUuid}` (public view) goes up
 * server-side, but `useAuth` doesn't expose a refresh hook for the cached
 * `User`. The next page reload reflects the new count. This is consistent
 * with how `MyPublicationsPreview` and `CalendarSubscribeButton` handle
 * their owner-state lifecycles and is logged as a SCRUM-110 follow-up.
 */
export default function FollowRequestsPanel() {
  const { rows, loading, error, accept, reject } = useMyFollowRequests()
  const toast = useToast()
  const [pendingId, setPendingId] = useState<number | null>(null)

  async function handleAccept(id: number) {
    if (pendingId !== null) return
    setPendingId(id)
    try {
      await accept(id)
    } catch {
      toast.showToast('error', 'Impossible d\'accepter cette demande.')
    } finally {
      setPendingId(null)
    }
  }

  async function handleReject(id: number) {
    if (pendingId !== null) return
    setPendingId(id)
    try {
      await reject(id)
    } catch {
      toast.showToast('error', 'Impossible de refuser cette demande.')
    } finally {
      setPendingId(null)
    }
  }

  return (
    <section aria-labelledby="follow-requests-heading" className={cardClass}>
      <div className="flex items-center justify-between gap-3">
        <h2 id="follow-requests-heading" className={headingClass}>
          Demandes de suivi reçues
        </h2>
        {!loading && rows.length > 0 && (
          <span className="text-xs font-bold text-foreground/40">{rows.length}</span>
        )}
      </div>

      {loading && <LoadingRows />}
      {!loading && error && (
        <p className="text-sm text-error">{error}</p>
      )}
      {!loading && !error && rows.length === 0 && <EmptyState />}
      {!loading && !error && rows.length > 0 && (
        <ul className="flex flex-col divide-y divide-border/40">
          {rows.map(row => (
            <FollowRequestRowItem
              key={row.request.id}
              row={row}
              busy={pendingId !== null}
              onAccept={handleAccept}
              onReject={handleReject}
            />
          ))}
        </ul>
      )}
    </section>
  )
}
