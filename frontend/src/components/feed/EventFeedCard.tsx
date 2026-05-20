import { Link } from 'react-router-dom'
import { Clock, MapPin, Users } from 'lucide-react'
import { EVENT_CATEGORIES, type Event } from '@/types/event'
import { parseApiUtcDateTime } from '@/utils/dateTime'
import FavoriteButton from '@/components/event/FavoriteButton'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import EventBanner from '@/components/event/EventBanner'
import type { Faculty } from '@/types/faculty'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatTime(dateString: string, allDay: boolean): string {
  if (allDay) return 'Toute la journée'
  const date = parseApiUtcDateTime(dateString)
  return date.toLocaleTimeString('fr-CH', { hour: '2-digit', minute: '2-digit' })
}

// ─── CapacityIndicator ────────────────────────────────────────────────────────

const capacityStates = {
  full: 'text-error',
  low: 'text-warning',
  normal: 'text-foreground/50',
} as const

function CapacityIndicator({ event }: Readonly<{ event: Event }>) {
  if (event.capacity == null) return null

  const available = event.availableSpots

  if (available === 0) {
    return (
      <span className={`flex items-center gap-1 text-xs font-medium ${capacityStates.full}`}>
        <Users className="w-3 h-3 shrink-0" />
        Complet
      </span>
    )
  }

  if (available != null && available <= 10) {
    return (
      <span className={`flex items-center gap-1 text-xs font-medium ${capacityStates.low}`}>
        <Users className="w-3 h-3 shrink-0" />
        {available} {available === 1 ? 'place restante' : 'places restantes'}
      </span>
    )
  }

  return (
    <span className={`flex items-center gap-1 text-xs ${capacityStates.normal}`}>
      <Users className="w-3 h-3 shrink-0" />
      {event.capacity} places
    </span>
  )
}

// ─── EventFeedCard ────────────────────────────────────────────────────────────

export default function EventFeedCard({ event }: Readonly<{ event: Event }>) {
  const category = EVENT_CATEGORIES[event.category]
  const time = formatTime(event.startDate, event.allDay)

  return (
    <Link to={`/events/${event.id}`} className="block no-underline">
      <article
        className="relative bg-background/60 backdrop-blur-xl border border-border rounded-2xl overflow-hidden transition-colors hover:border-foreground/20 flex flex-col md:flex-row"
        aria-label={event.title}
      >
        {/* Banner — 128px tall on mobile, 112px on desktop */}
        <div className="relative shrink-0 md:w-48 h-32 md:h-28">
          <EventBanner event={event} className="absolute inset-0">
            <div className="absolute inset-0 bg-linear-to-t from-black/60 via-black/20 to-transparent" />

            {/* Favorite button */}
            <div className="absolute top-2 right-2 z-10">
              <FavoriteButton eventId={event.id} />
            </div>

            {/* Category badge */}
            <span
              className="absolute bottom-2 left-2 text-white text-xs font-bold px-2 py-0.5 rounded-full backdrop-blur-sm"
              style={{ background: `${category.color}dd` }}
            >
              {category.name}
            </span>
          </EventBanner>
        </div>

        {/* Info */}
        <div className="flex flex-1 flex-col justify-between px-4 py-3 gap-2 min-w-0">
          {/* Title + meta */}
          <div className="flex flex-col gap-1 min-w-0">
            <h3 className="text-sm font-semibold text-foreground line-clamp-2 leading-snug wrap-anywhere">
              {event.title}
            </h3>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5">
              <span className="flex items-center gap-1 text-xs text-foreground/55">
                <Clock className="w-3 h-3 shrink-0" style={{ color: category.color }} />
                {time}
              </span>
              <span className="flex items-center gap-1 text-xs text-foreground/55 min-w-0">
                <MapPin className="w-3 h-3 shrink-0" style={{ color: category.color }} />
                <span className="truncate">{event.location}</span>
              </span>
            </div>
          </div>

          {/* Footer: faculty badge + capacity */}
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="shrink-0">
              {event.faculty
                ? <FacultyBadge id={event.faculty as Faculty} />
                : <span className="text-xs text-foreground/30">—</span>
              }
            </div>
            <CapacityIndicator event={event} />
          </div>
        </div>
      </article>
    </Link>
  )
}
