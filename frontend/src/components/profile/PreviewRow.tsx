import { Link } from 'react-router-dom'
import { Calendar, Users } from 'lucide-react'
import { EVENT_CATEGORIES, EVENT_STATUSES, type Event } from '@/types/event'
import { EVENT_STATUS_VARIANTS } from '@/utils/eventStatusStyles'
import { formatEventDateTimeCompact } from '@/utils/dateTime'

interface PreviewRowProps {
  event: Event
}

export default function PreviewRow({ event }: Readonly<PreviewRowProps>) {
  const category = EVENT_CATEGORIES[event.category]
  const statusClass = EVENT_STATUS_VARIANTS[event.status]
  const thumbnail = event.bannerUrl
    ? { backgroundImage: `url(${event.bannerUrl})` }
    : { background: `linear-gradient(135deg, ${category.color}55, ${category.color}cc)` }

  return (
    <Link
      to={`/events/${event.id}`}
      aria-label={event.title}
      className="group flex items-center gap-3 rounded-2xl p-2 no-underline text-inherit hover:bg-foreground/5 transition-colors"
    >
      <div
        className="size-12 rounded-xl bg-cover bg-center shrink-0"
        style={thumbnail}
        aria-hidden="true"
      />
      <div className="flex-1 min-w-0 flex flex-col gap-0.5">
        <span className="text-sm font-semibold text-foreground group-hover:text-accent line-clamp-1 break-words">
          {event.title}
        </span>
        <div className="flex items-center gap-3 text-xs text-foreground/55">
          <span className="inline-flex items-center gap-1 truncate">
            <Calendar className="size-3 shrink-0" style={{ color: category.color }} />
            <span className="truncate">{formatEventDateTimeCompact(event.startDate, event.allDay)}</span>
          </span>
          <span className="inline-flex items-center gap-1 shrink-0">
            <Users className="size-3" style={{ color: category.color }} />
            {event.attendingCount}
          </span>
        </div>
      </div>
      <span
        className={`shrink-0 text-[10px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full border ${statusClass}`}
      >
        {EVENT_STATUSES[event.status].name}
      </span>
    </Link>
  )
}
