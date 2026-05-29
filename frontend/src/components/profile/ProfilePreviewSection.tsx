import type { LucideIcon } from 'lucide-react'
import PreviewRow from './PreviewRow'
import type { Event } from '@/types/event'

interface ProfilePreviewSectionProps {
  /** `id` of the heading, referenced by the section's `aria-labelledby`. */
  headingId: string
  title: string
  events: Event[]
  loading: boolean
  error: string | null
  /** Icon shown in the empty state. */
  emptyIcon: LucideIcon
  emptyMessage: string
  /**
   * Masque le titre interne (la section est alors labellisée via `aria-label`).
   * Utilisé quand la section est rendue dans une barre d'onglets — l'onglet
   * actif fait office de titre, le doublon serait redondant.
   */
  hideHeading?: boolean
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

/**
 * Shared shell for the two public-profile preview lists — `ProfileEventsList`
 * ("Événements organisés") and `ProfileParticipations` ("Participations
 * publiques"), both SCRUM-141. Renders the glass card + uppercase heading and
 * the loading / error / empty / data states (a `PreviewRow` per event). Single
 * source of truth so the two sections don't duplicate the layout + state logic.
 */
export default function ProfilePreviewSection({
  headingId,
  title,
  events,
  loading,
  error,
  emptyIcon: EmptyIcon,
  emptyMessage,
  hideHeading = false,
}: Readonly<ProfilePreviewSectionProps>) {
  return (
    <section
      aria-labelledby={hideHeading ? undefined : headingId}
      aria-label={hideHeading ? title : undefined}
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-4"
    >
      {!hideHeading && (
        <h2 id={headingId} className="text-xs font-bold uppercase tracking-widest text-foreground/30">
          {title}
        </h2>
      )}

      {loading && <LoadingRows />}
      {!loading && error && <p className="text-sm text-error">{error}</p>}
      {!loading && !error && events.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
          <EmptyIcon className="w-10 h-10 text-foreground/20" />
          <p className="text-foreground/50 text-sm font-medium">{emptyMessage}</p>
        </div>
      )}
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
