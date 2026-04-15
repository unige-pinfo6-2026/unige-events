import { Link } from 'react-router-dom'
import { Calendar, MapPin, Users } from 'lucide-react'
import { EVENT_CATEGORIES, type Event } from '@/types/event'
import { formatEventDateTimeCompact } from '@/utils/dateTime'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import type { Faculty } from '@/types/faculty'
import FavoriteButton from './FavoriteButton'

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
        <div
          className="relative h-52 overflow-hidden"
          style={{
            background: event.bannerUrl
              ? `url(${event.bannerUrl}) center/cover`
              : `linear-gradient(135deg, ${category.color}55, ${category.color}cc)`,
          }}
        >
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

          {/* Title overlaid on banner */}
          <div className="absolute bottom-0 left-0 right-0 p-5">
            <h3 className="text-white text-lg font-bold leading-snug line-clamp-2 drop-shadow-sm">
              {event.title}
            </h3>
            <FacultyBadge id={event.faculty as Faculty} />
          </div>
        </div>

        {/* Content */}
        <div className="p-5 flex flex-col gap-3">
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <Calendar className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="font-medium">{formatEventDateTimeCompact(event.startDate)}</span>
            </div>
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <MapPin className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="line-clamp-1">{event.location}</span>
            </div>
            {event.capacity != null && (
              <div className="flex items-center gap-2.5 text-sm text-foreground/55">
                <Users className="w-4 h-4 shrink-0" style={{ color: category.color }} />
                <span>{event.capacity} places</span>
              </div>
            )}
          </div>

          {event.description && (
            <>
              <div className="border-t border-border" />
              <p className="text-sm text-foreground/45 line-clamp-2 leading-relaxed">
                {event.description}
              </p>
            </>
          )}
        </div>
      </article>
    </Link>
  )
}
