import { Users } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { Link } from 'react-router-dom'
import { useCoOrganizers } from '@/hooks/useCoOrganizers'

interface Props {
  eventId: number
  creatorId: string
  creatorDisplayName?: string | null
  creatorAvatarUrl?: string | null
}

/**
 * Sidebar card on `EventDetailPage` showing the organizer team:
 * - The primary creator (always present).
 * - ACCEPTED co-organizers (PENDING + DECLINED filtered out — they're private
 *   to the creator and editing UI).
 *
 * If the API call fails silently (event is public, anyone can request), we
 * still render the creator alone — never a broken UI.
 */
export default function EventOrganizerTeam({
  eventId,
  creatorId,
  creatorDisplayName,
  creatorAvatarUrl,
}: Readonly<Props>) {
  const { coOrganizers, loading } = useCoOrganizers(eventId)
  const accepted = coOrganizers.filter((c) => c.status === 'ACCEPTED')

  return (
    <section
      aria-label="Équipe organisatrice"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-2xl p-5 border border-border"
    >
      <header className="flex items-center gap-2 mb-3 text-foreground">
        <Users className="w-4 h-4 text-foreground/60" />
        <h3 className="text-sm font-semibold">Équipe organisatrice</h3>
      </header>

      <ul className="space-y-2">
        <OrganizerRow
          userId={creatorId}
          displayName={creatorDisplayName ?? null}
          avatarUrl={creatorAvatarUrl ?? null}
          badgeLabel="Organisateur"
          badgeClass="bg-accent/15 text-accent border-accent/40"
        />
        {loading && (
          <li>
            <Skeleton name="event-organizer-team" loading={true}>
              <div className="space-y-2">
                <div className="h-10 rounded-xl" />
                <div className="h-10 rounded-xl" />
              </div>
            </Skeleton>
          </li>
        )}
        {!loading &&
          accepted.map((co) => (
            <OrganizerRow
              key={co.id}
              userId={co.userId}
              displayName={co.displayName}
              avatarUrl={co.avatarUrl}
              badgeLabel="Co-organisateur"
              badgeClass="bg-emerald-500/10 text-emerald-500 border-emerald-500/30"
            />
          ))}
      </ul>
    </section>
  )
}

function OrganizerRow({
  userId,
  displayName,
  avatarUrl,
  badgeLabel,
  badgeClass,
}: Readonly<{
  userId: string
  displayName: string | null
  avatarUrl: string | null
  badgeLabel: string
  badgeClass: string
}>) {
  const initials = (displayName ?? '??').slice(0, 2).toUpperCase()
  return (
    <li>
      <Link
        to={`/profile/${userId}`}
        className="flex items-center gap-3 p-2 rounded-xl hover:bg-foreground/5 transition-colors"
      >
        <div className="w-8 h-8 rounded-full bg-foreground/10 flex items-center justify-center shrink-0 overflow-hidden">
          {avatarUrl ? (
            <img src={avatarUrl} alt="" className="w-full h-full object-cover" />
          ) : (
            <span className="text-xs font-semibold text-foreground/60">{initials}</span>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-foreground truncate">
            {displayName ?? 'Utilisateur'}
          </p>
        </div>
        <span className={`px-2 py-0.5 rounded-md text-[10px] font-medium border ${badgeClass}`}>
          {badgeLabel}
        </span>
      </Link>
    </li>
  )
}

// Re-exports for tests that need to type the CoOrganizer subset.
export type { CoOrganizer } from '@/types/coOrganizer'
