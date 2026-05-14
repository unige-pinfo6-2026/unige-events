import { Ticket } from 'lucide-react'

/**
 * Section "Participations publiques" on a public profile page (SCRUM-141).
 *
 * TODO(SCRUM-141 follow-up): the backend does not expose an endpoint for
 * listing the public attendances of an arbitrary user yet. `/users/me/participations`
 * exists but is self-only; there is no `/users/{id}/participations`. A
 * follow-up ticket needs to add that endpoint (authenticated; backend
 * filters to events the caller can see — published-only for non-organizers,
 * with privacy gating mirroring `GET /events/{id}/attendees`).
 *
 * Until then the section renders an empty placeholder so the page layout
 * keeps the planned slot for participations and the gap is visible to
 * product / users instead of being silently dropped.
 */
export default function ProfileParticipations() {
  return (
    <section
      aria-labelledby="profile-participations-heading"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-4"
    >
      <h2 id="profile-participations-heading" className="text-xs font-bold uppercase tracking-widest text-foreground/30">
        Participations publiques
      </h2>
      <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
        <Ticket className="w-10 h-10 text-foreground/20" />
        <p className="text-foreground/50 text-sm font-medium">
          Aucune participation publique à afficher.
        </p>
      </div>
    </section>
  )
}
