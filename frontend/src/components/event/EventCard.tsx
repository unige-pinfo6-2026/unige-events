import { Link } from 'react-router-dom'
import { EVENT_CATEGORIES, type Event } from '@/types/event'

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('fr-CH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function EventCard({ event }: { event: Event }) {
  const category = EVENT_CATEGORIES[event.category];

  return (
    <Link to={`/dashboard/events/${event.id}`} className="block no-underline">
      <article className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl overflow-hidden cursor-pointer transition-all duration-150 hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(0,0,0,0.12)]">
        {/* Banner */}
        <div
          className="h-[140px] flex items-end p-3"
          style={{
            background: event.bannerUrl
              ? `url(${event.bannerUrl}) center/cover`
              : `linear-gradient(135deg, ${category.color}22, ${category.color}44)`,
          }}
        >
          <span
            className="text-white text-xs font-bold px-2.5 py-0.5 rounded-full tracking-wide"
            style={{ background: category.color }}
          >
            {category.name}
          </span>
        </div>

        {/* Content */}
        <div className="px-5 py-4">
          <h3 className="text-[var(--text-primary)] text-base font-bold leading-snug mb-2 line-clamp-2">
            {event.title}
          </h3>

          <div className="flex flex-col gap-1">
            <span className="text-[0.8125rem] text-[var(--text-secondary)]">
              📅 {formatDate(event.startDate)}
            </span>
            <span className="text-[0.8125rem] text-[var(--text-secondary)]">
              📍 {event.location}
            </span>
            {event.capacity != null && (
              <span className="text-[0.8125rem] text-[var(--text-secondary)]">
                👥 {event.capacity} places
              </span>
            )}
          </div>
        </div>
      </article>
    </Link>
  )
}

export default EventCard