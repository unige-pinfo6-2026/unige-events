import { Link } from 'react-router-dom'
import type { Event, EventCategory } from '../types'
import { formatEventDateTimeCompact } from '../utils/dateTime'

const CATEGORY_LABELS: Record<EventCategory, string> = {
  ACADEMIC: 'Académique',
  SPORTS: 'Sports',
  CULTURAL: 'Culturel',
  SOCIAL: 'Social',
  CONFERENCE: 'Conférence',
  OTHER: 'Autre',
}

const CATEGORY_COLORS: Record<EventCategory, string> = {
  ACADEMIC: '#2563eb',
  SPORTS: '#16a34a',
  CULTURAL: '#9333ea',
  SOCIAL: '#ea580c',
  CONFERENCE: '#0891b2',
  OTHER: '#6b7280',
}

interface EventCardProps {
  event: Event
}

function EventCard({ event }: EventCardProps) {
  const categoryColor = CATEGORY_COLORS[event.category]

  return (
    <Link
      to={`/events/${event.id}`}
      style={{ textDecoration: 'none', display: 'block' }}
    >
      <article
        style={{
          background: 'var(--card-bg)',
          border: '1px solid var(--card-border)',
          borderRadius: 16,
          overflow: 'hidden',
          transition: 'background 0.3s, border-color 0.3s, transform 0.15s, box-shadow 0.15s',
          cursor: 'pointer',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-2px)'
          e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.12)'
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'translateY(0)'
          e.currentTarget.style.boxShadow = 'none'
        }}
      >
        {/* Banner */}
        <div
          style={{
            height: 140,
            background: event.bannerUrl
              ? `url(${event.bannerUrl}) center/cover`
              : `linear-gradient(135deg, ${categoryColor}22, ${categoryColor}44)`,
            display: 'flex',
            alignItems: 'flex-end',
            padding: '0.75rem',
          }}
        >
          <span
            style={{
              background: categoryColor,
              color: 'white',
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '0.2rem 0.6rem',
              borderRadius: 20,
              letterSpacing: '0.04em',
            }}
          >
            {CATEGORY_LABELS[event.category]}
          </span>
        </div>

        {/* Content */}
        <div style={{ padding: '1rem 1.25rem' }}>
          <h3
            style={{
              margin: '0 0 0.5rem',
              fontSize: '1rem',
              fontWeight: 700,
              color: 'var(--text-primary)',
              lineHeight: 1.3,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {event.title}
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              📅 {formatEventDateTimeCompact(event.startDate)}
            </span>
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              📍 {event.location}
            </span>
            {event.capacity !== undefined && event.capacity !== null && (
              <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
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
