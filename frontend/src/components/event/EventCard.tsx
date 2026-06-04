import { Link } from 'react-router-dom'
import { Ban, Calendar, MapPin, RefreshCw, Users } from 'lucide-react'
import { EVENT_CATEGORIES, type Event } from '@/types/event'
import { formatEventDateTimeCompact } from '@/utils/dateTime'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import type { Faculty } from '@/types/faculty'
import FavoriteButton from './FavoriteButton'
import EventBanner from './EventBanner'

export default function EventCard({
  event,
  favorited = false,
  onFavoriteRemove,
}: Readonly<{
  event: Event
  favorited?: boolean
  onFavoriteRemove?: () => void
}>) {
  const category = EVENT_CATEGORIES[event.category]

  return (
    <Link to={`/events/${event.id}`} className="block no-underline">
      <article className="relative h-full bg-background border border-border rounded-3xl overflow-hidden transition-transform duration-300 hover:-translate-y-1.5 hover:shadow-2xl hover:shadow-black/20 hover:border-foreground/20">

        {/* Colored top accent line */}
        <div className="absolute top-0 left-0 right-0 h-0.5 z-10" style={{ background: category.color }} />

        {/* Banner */}
        <EventBanner event={event} className="h-52">
          <div className="absolute inset-0 bg-linear-to-t from-black/85 via-black/30 to-black/10" />

          <span
            className="absolute top-4 left-4 text-white text-xs font-bold px-3 py-1.5 rounded-full tracking-wide backdrop-blur-sm"
            style={{ background: `${category.color}dd` }}
          >
            {category.name}
          </span>

          {/* Title + faculty overlaid on banner */}
          <div className="absolute bottom-0 left-0 right-0 p-5 flex flex-col gap-2"/>
          <div className="absolute top-4 right-4 z-10">
            <FavoriteButton eventId={event.id} initialFavorited={favorited} onRemove={onFavoriteRemove} />
          </div>

          {event.parentEventId != null && (
            <span
              className="absolute bottom-4 right-4 z-10 flex items-center gap-1.5 bg-background/80 backdrop-blur-sm border border-border/40 text-foreground/80 px-2.5 py-1 rounded-full text-xs font-medium"
              aria-label="Occurrence d'un événement récurrent"
            >
              <RefreshCw className="w-3.5 h-3.5" aria-hidden="true" />
              Récurrent
            </span>
          )}

          {/* Title overlaid on banner */}
          <div className="absolute bottom-0 left-0 right-0 p-5">
            {event.status === 'CANCELLED' && (
              <span className="inline-flex items-center gap-1.5 mb-2 bg-error text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wide">
                <Ban className="w-3.5 h-3.5" aria-hidden="true" />
                Annulé
              </span>
            )}
            <h3 className="text-white text-lg font-bold leading-snug line-clamp-2 drop-shadow-sm wrap-anywhere">
              {event.title}
            </h3>
            <FacultyBadge id={event.faculty as Faculty} />
          </div>
        </EventBanner>

        {/* Content */}
        <div className="p-5 flex flex-col gap-3">
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <Calendar className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="font-medium">{formatEventDateTimeCompact(event.startDate, event.allDay)}</span>
            </div>
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <MapPin className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="line-clamp-1">{event.location}</span>
            </div>
            {event.capacity != null && (
              <div className="flex items-center gap-2.5 text-sm text-foreground/55">
                <Users className="w-4 h-4 shrink-0" style={{ color: category.color }} />
                <span>{event.capacity} {event.capacity === 1 ? 'place' : 'places'}</span>
              </div>
            )}
          </div>

          {event.description && (
            <>
              <div className="border-t border-border" />
              <p className="text-sm text-foreground/45 line-clamp-2 leading-relaxed wrap-anywhere">
                {event.description}
              </p>
            </>
          )}
        </div>
      </article>
    </Link>
  )
}
