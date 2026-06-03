import { useRef, useState } from 'react'
import { Inbox } from 'lucide-react'
import { Dropdown } from '@/components/utils/Dropdown'
import { RequestsInbox } from '@/components/utils/RequestsInbox'
import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'
import { useMyFollowRequests } from '@/hooks/useMyFollowRequests'
import { useToast } from '@/hooks/useToast'

function InboxBell({ count }: Readonly<{ count: number }>) {
  return (
    <div className="relative">
      {/* Presentational only — the control is the Dropdown trigger (role="button"
          + aria-label) wrapping this bell. Avoids a button-in-button that taps
          unreliably on iOS. Hover background gated to hover-capable devices. */}
      <span className="flex p-2 rounded-lg text-foreground [@media(hover:hover)]:hover:bg-foreground/5 transition-colors">
        <Inbox className="size-5" />
      </span>
      {count > 0 && (
        <span
          aria-label={`${count} demande${count > 1 ? 's' : ''} en attente`}
          className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[16px] h-4 px-1 text-[10px] font-bold leading-none text-white bg-accent rounded-full pointer-events-none select-none"
        >
          {count > 99 ? '99+' : count}
        </span>
      )}
    </div>
  )
}

/**
 * Boîte de réception combinée de la navbar (SCRUM-141 redesign) : regroupe les
 * invitations à co-organiser et les demandes de suivi reçues dans un seul
 * dropdown (au survol, comme la cloche de notifications), avec un compteur
 * total. Remplace les anciens gros blocs `CoOrganizerInvitationsList` /
 * `FollowRequestsPanel` qui vivaient sur la page profil.
 *
 * Les hooks sont montés ici (et non dans le panneau) car le compteur du
 * trigger en a besoin en permanence — le panneau, lui, est purement
 * présentationnel.
 *
 * ### Pourquoi `forceOpen` + `keepOpenTimer`
 * Le `Dropdown` s'ouvre via CSS `group-hover`. Quand on accepte/refuse une
 * demande, la ligne correspondante disparaît, le panel rétrécit, et la souris
 * se retrouve hors de la zone hover → le dropdown se ferme entre chaque
 * action. `forceOpen=true` maintient la visibilité pendant la mutation ET
 * pendant un bref délai (~350 ms) après sa fin, le temps que React re-render
 * le nouveau contenu et que l'utilisateur déplace sa souris vers l'item
 * suivant.
 */
export function RequestsInboxDropdown() {
  const invites = useCoOrganizerInvitations()
  const follows = useMyFollowRequests()
  const toast = useToast()

  // Maintient le dropdown ouvert pendant et juste après une mutation.
  const [forceOpen, setForceOpen] = useState(false)
  const keepOpenTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const totalPending = invites.pendingCount + follows.rows.length

  /** Ouvre forceOpen et le referme après un délai une fois la mutation terminée. */
  function withKeepOpen<T>(fn: () => Promise<T>): Promise<T> {
    if (keepOpenTimer.current) clearTimeout(keepOpenTimer.current)
    setForceOpen(true)
    return fn().finally(() => {
      keepOpenTimer.current = setTimeout(() => setForceOpen(false), 350)
    })
  }

  async function acceptInvitation(eventId: number) {
    await withKeepOpen(async () => {
      try {
        await invites.accept(eventId)
        toast.showToast('success', 'Invitation à co-organiser acceptée !')
      } catch {
        toast.showToast('error', "Impossible d'accepter cette invitation.")
      }
    })
  }
  async function declineInvitation(eventId: number) {
    await withKeepOpen(async () => {
      try {
        await invites.decline(eventId)
        toast.showToast('success', 'Invitation à co-organiser refusée.')
      } catch {
        toast.showToast('error', 'Impossible de refuser cette invitation.')
      }
    })
  }
  async function acceptFollow(followId: number) {
    await withKeepOpen(async () => {
      try {
        await follows.accept(followId)
        toast.showToast('success', 'Demande de suivi acceptée !')
      } catch {
        toast.showToast('error', "Impossible d'accepter cette demande.")
      }
    })
  }
  async function rejectFollow(followId: number) {
    await withKeepOpen(async () => {
      try {
        await follows.reject(followId)
        toast.showToast('success', 'Demande de suivi refusée.')
      } catch {
        toast.showToast('error', 'Impossible de refuser cette demande.')
      }
    })
  }

  return (
    <Dropdown align="right" showChevron={false} forceOpen={forceOpen} triggerLabel="Demandes reçues" trigger={<InboxBell count={totalPending} />}>
      <RequestsInbox
        invitations={invites.invitations}
        followRequests={follows.rows}
        loading={invites.loading || follows.loading}
        error={invites.error ?? follows.error}
        onAcceptInvitation={acceptInvitation}
        onDeclineInvitation={declineInvitation}
        onAcceptFollow={acceptFollow}
        onRejectFollow={rejectFollow}
      />
    </Dropdown>
  )
}
