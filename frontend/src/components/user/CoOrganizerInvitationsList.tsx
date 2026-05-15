import { Check, Inbox, X } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { Link } from 'react-router-dom'
import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'
import { ButtonNeutral, ButtonPrimary } from '@/components/utils/Buttons'
import { formatEventDateTime } from '@/utils/dateTime'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'

/**
 * Self-view list of PENDING co-organizer invitations. Mounted in `ProfilePage`
 * when `isOwnProfile`. Each card shows the event title + dates and offers
 * accept/decline buttons.
 */
export default function CoOrganizerInvitationsList() {
  const { invitations, loading, error, accept, decline } = useCoOrganizerInvitations()

  return (
    <section
      aria-label="Mes invitations à co-organiser"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border"
    >
      <header className="flex items-center gap-3 mb-4">
        <div className="w-10 h-10 rounded-2xl bg-linear-to-br from-accent/30 to-pink-600/30 flex items-center justify-center">
          <Inbox className="w-5 h-5 text-accent" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-foreground">Invitations à co-organiser</h2>
          <p className="text-xs text-foreground/60">
            Les autres organisateurs peuvent vous inviter à gérer leurs événements.
          </p>
        </div>
      </header>

      {loading && (
        <Skeleton name="co-organizer-invitations" loading={true}>
          <div className="space-y-3">
            <div className="h-24 rounded-2xl" />
            <div className="h-24 rounded-2xl" />
          </div>
        </Skeleton>
      )}
      {!loading && error && <p className="text-sm text-error">{error}</p>}
      {!loading && !error && invitations.length === 0 && (
        <p className="text-sm text-foreground/50 italic">Aucune invitation en attente.</p>
      )}
      {!loading && invitations.length > 0 && (
        <ul className="space-y-3">
          {invitations.map((inv) => (
            <InvitationCard
              key={inv.id}
              invitation={inv}
              onAccept={() => accept(inv.event.id)}
              onDecline={() => decline(inv.event.id)}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function InvitationCard({
  invitation,
  onAccept,
  onDecline,
}: Readonly<{
  invitation: CoOrganizerInvitation
  onAccept: () => void
  onDecline: () => void
}>) {
  return (
    <li className="p-4 rounded-2xl border border-border bg-background/30">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex-1 min-w-0">
          <Link
            to={`/events/${invitation.event.id}`}
            className="text-base font-semibold text-foreground hover:text-accent transition-colors line-clamp-1"
          >
            {invitation.event.title}
          </Link>
          <p className="text-sm text-foreground/60 mt-1">
            {formatEventDateTime(invitation.event.startDate)}
          </p>
        </div>
        <div className="flex gap-2">
          <ButtonPrimary onClick={onAccept} size="sm">
            <Check className="w-4 h-4" />
            Accepter
          </ButtonPrimary>
          <ButtonNeutral onClick={onDecline} size="sm">
            <X className="w-4 h-4" />
            Décliner
          </ButtonNeutral>
        </div>
      </div>
    </li>
  )
}
