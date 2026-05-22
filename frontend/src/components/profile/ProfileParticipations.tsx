import { Ticket } from 'lucide-react'
import PreviewRow from './PreviewRow'
import type { Event } from '@/types/event'

interface ProfileParticipationsProps {
  events: Event[]
  loading: boolean
  error: string | null
}

function LoadingRows() {
  return (
    <div className="flex flex-col gap-2" aria-busy="true">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 p-2 rounded-2xl">
          <div className="size-12 rounded-xl bg-foreground/10 shrink-0 animate-pulse" />
          <div className="flex-1 flex flex-col gap-1.5">
            <div className="h-3.5 w-3/5 rounded bg-foreground/10 animate-pulse" />
            <div className="h-3 w-2/5 rounded bg-foreground/10 animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
      <Ticket className="w-10 h-10 text-foreground/20" />
      <p className="text-foreground/50 text-sm font-medium">
        Aucune participation publique pour le moment.
      </p>
    </div>
  )
}

/**
 * Section "Participations publiques" on a public profile page (SCRUM-141
 * follow-up). Lists the PUBLISHED events the profile owner is registered to
 * (ATTENDING), fetched via `GET /users/{id}/participations` (privacy-gated
 * server-side: self → always, public target → list, private non-owner → empty).
 * Reuses the same {@link PreviewRow} primitive as {@code ProfileEventsList} for
 * visual parity with the rest of the profile area.
 */
export default function ProfileParticipations({ events, loading, error }: Readonly<ProfileParticipationsProps>) {
  return (
    <section
      aria-labelledby="profile-participations-heading"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-4"
    >
      <h2 id="profile-participations-heading" className="text-xs font-bold uppercase tracking-widest text-foreground/30">
        Participations publiques
      </h2>

      {loading && <LoadingRows />}
      {!loading && error && (
        <p className="text-sm text-error">{error}</p>
      )}
      {!loading && !error && events.length === 0 && <EmptyState />}
      {!loading && !error && events.length > 0 && (
        <div className="flex flex-col gap-1">
          {events.map(event => (
            <PreviewRow key={event.id} event={event} />
          ))}
        </div>
      )}
    </section>
  )
}
