import type { FeedGroup } from '@/hooks/useFeed'
import EventFeedCard from './EventFeedCard'

// ─── Date formatting ──────────────────────────────────────────────────────────

const dateFormatter = new Intl.DateTimeFormat('fr-CH', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

function localDateKey(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function formatDateLabel(dateKey: string): string {
  const today = new Date()
  if (dateKey === localDateKey(today)) return "Aujourd'hui"

  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (dateKey === localDateKey(tomorrow)) return 'Demain'

  // Parse comme date locale pour éviter les décalages UTC
  const [y, m, d] = dateKey.split('-').map(Number)
  return dateFormatter.format(new Date(y, m - 1, d))
}

// ─── DateMarker ───────────────────────────────────────────────────────────────

function DateMarker({ dateKey }: Readonly<{ dateKey: string }>) {
  return (
    <div className="flex items-center gap-4" data-testid="date-marker">
      {/* Dot positionné sur le trait vertical */}
      <div
        className="relative z-10 shrink-0 w-7 h-7 md:w-10 md:h-10 flex items-center justify-center"
        aria-hidden="true"
      >
        <div className="w-3 h-3 rounded-full bg-accent ring-4 ring-background" />
      </div>

      {/* Label de date */}
      <span className="text-sm md:text-base font-semibold text-foreground/80 capitalize">
        {formatDateLabel(dateKey)}
      </span>
    </div>
  )
}

// ─── Timeline ─────────────────────────────────────────────────────────────────

interface TimelineProps {
  groups: FeedGroup[]
}

export default function Timeline({ groups }: Readonly<TimelineProps>) {
  return (
    <div className="relative flex flex-col gap-0" role="feed" aria-label="Fil chronologique des événements">
      {/* Trait vertical continu */}
      <div
        className="absolute left-3.5 md:left-5 top-3.5 bottom-3.5 w-0.5 bg-border"
        aria-hidden="true"
        data-testid="timeline-line"
      />

      {groups.map(group => (
        <div key={group.date} className="relative flex flex-col gap-4 pb-8 last:pb-0">
          <DateMarker dateKey={group.date} />

          {/* Cartes événements du groupe */}
          <div className="flex flex-col gap-3 pl-11 md:pl-14">
            {group.events.map(event => (
              <EventFeedCard key={event.id} event={event} />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
