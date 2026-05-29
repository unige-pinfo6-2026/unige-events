import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarDays, Check, X } from 'lucide-react'
import UserAvatar from '@/components/user/UserAvatar'
import { formatEventDateTime } from '@/utils/dateTime'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'
import type { FollowRequestRow } from '@/hooks/useMyFollowRequests'

interface RequestsInboxProps {
  invitations: CoOrganizerInvitation[]
  followRequests: FollowRequestRow[]
  loading: boolean
  error: string | null
  onAcceptInvitation: (eventId: number) => Promise<void>
  onDeclineInvitation: (eventId: number) => Promise<void>
  onAcceptFollow: (followId: number) => Promise<void>
  onRejectFollow: (followId: number) => Promise<void>
}

const acceptButtonClass =
  'inline-flex items-center justify-center size-7 rounded-lg border border-emerald-500/40 bg-emerald-500/10 text-emerald-400 cursor-pointer transition-colors hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed'

const rejectButtonClass =
  'inline-flex items-center justify-center size-7 rounded-lg border border-border bg-transparent text-foreground/60 cursor-pointer transition-colors hover:border-foreground/30 hover:text-foreground disabled:opacity-50 disabled:cursor-not-allowed'

const sectionTitleClass = 'px-3 pt-3 pb-1 text-[10px] font-bold uppercase tracking-widest text-foreground/30'

interface ActionRowProps {
  label: string
  sublabel?: string
  href: string
  leading: React.ReactNode
  busy: boolean
  acceptLabel: string
  rejectLabel: string
  onAccept: () => void
  onReject: () => void
}

function ActionRow({ label, sublabel, href, leading, busy, acceptLabel, rejectLabel, onAccept, onReject }: Readonly<ActionRowProps>) {
  return (
    <li className="flex items-center gap-2 px-3 py-2 hover:bg-foreground/5 transition-colors">
      <Link to={href} className="shrink-0 no-underline">
        {leading}
      </Link>
      <Link to={href} className="flex-1 min-w-0 no-underline">
        <span className="block text-sm font-semibold text-foreground truncate hover:text-accent transition-colors">{label}</span>
        {sublabel && <span className="block text-xs text-foreground/50 truncate">{sublabel}</span>}
      </Link>
      <button type="button" onClick={onAccept} disabled={busy} aria-label={acceptLabel} className={acceptButtonClass}>
        <Check className="size-3.5" aria-hidden="true" />
      </button>
      <button type="button" onClick={onReject} disabled={busy} aria-label={rejectLabel} className={rejectButtonClass}>
        <X className="size-3.5" aria-hidden="true" />
      </button>
    </li>
  )
}

function LoadingRows() {
  return (
    <div className="flex flex-col gap-2 p-3" aria-busy="true">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex items-center gap-2">
          <div className="size-9 rounded-full bg-foreground/10 shrink-0 animate-pulse" />
          <div className="flex-1 h-4 rounded bg-foreground/10 animate-pulse" />
          <div className="size-7 rounded-lg bg-foreground/10 animate-pulse" />
          <div className="size-7 rounded-lg bg-foreground/10 animate-pulse" />
        </div>
      ))}
    </div>
  )
}

/**
 * Panneau de la boîte de réception combinée (SCRUM-141 redesign) : deux
 * sections — invitations à co-organiser + demandes de suivi reçues — listées
 * en lignes compactes avec actions Accepter / Refuser. Rendu dans le dropdown
 * navbar `RequestsInboxDropdown` (qui monte les hooks et fournit les
 * handlers). Présentationnel : la logique optimiste vit dans les hooks ; ce
 * composant ne gère que l'état « busy » global pour neutraliser les boutons
 * pendant un aller-retour réseau.
 */
export function RequestsInbox({
  invitations,
  followRequests,
  loading,
  error,
  onAcceptInvitation,
  onDeclineInvitation,
  onAcceptFollow,
  onRejectFollow,
}: Readonly<RequestsInboxProps>) {
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    if (busy) return
    setBusy(true)
    try {
      await action()
    } finally {
      setBusy(false)
    }
  }

  const isEmpty = invitations.length === 0 && followRequests.length === 0

  return (
    <div className="w-80">
      <div className="flex items-center px-3 h-12">
        <span className="text-sm font-semibold text-foreground">Demandes reçues</span>
      </div>
      <div className="h-px bg-border" />

      {loading ? (
        <LoadingRows />
      ) : error ? (
        <div className="px-4 py-6 text-xs text-center text-error">{error}</div>
      ) : isEmpty ? (
        <div className="px-4 py-6 text-xs text-center text-foreground/40">Aucune demande en attente</div>
      ) : (
        <div className="max-h-[360px] overflow-y-auto">
          {invitations.length > 0 && (
            <>
              <p className={sectionTitleClass}>Invitations à co-organiser</p>
              <ul className="flex flex-col">
                {invitations.map((inv) => (
                  <ActionRow
                    key={inv.id}
                    href={`/events/${inv.event.id}`}
                    label={inv.event.title}
                    sublabel={formatEventDateTime(inv.event.startDate)}
                    leading={
                      <span className="flex items-center justify-center size-9 rounded-full bg-accent/15 text-accent">
                        <CalendarDays className="size-4" aria-hidden="true" />
                      </span>
                    }
                    busy={busy}
                    acceptLabel={`Accepter l'invitation pour ${inv.event.title}`}
                    rejectLabel={`Décliner l'invitation pour ${inv.event.title}`}
                    onAccept={() => run(() => onAcceptInvitation(inv.event.id))}
                    onReject={() => run(() => onDeclineInvitation(inv.event.id))}
                  />
                ))}
              </ul>
            </>
          )}

          {followRequests.length > 0 && (
            <>
              <p className={sectionTitleClass}>Demandes de suivi</p>
              <ul className="flex flex-col">
                {followRequests.map((row) => {
                  const displayName = row.follower?.displayName ?? 'Utilisateur'
                  return (
                    <ActionRow
                      key={row.request.id}
                      href={`/profile/${row.request.followerId}`}
                      label={displayName}
                      leading={<UserAvatar user={row.follower} className="size-9" />}
                      busy={busy}
                      acceptLabel={`Accepter la demande de ${displayName}`}
                      rejectLabel={`Refuser la demande de ${displayName}`}
                      onAccept={() => run(() => onAcceptFollow(row.request.id))}
                      onReject={() => run(() => onRejectFollow(row.request.id))}
                    />
                  )
                })}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  )
}
