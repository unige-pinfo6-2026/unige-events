import type { Event } from '@/types/event'
import { formatRelativeTime } from '@/utils/formatRelativeTime'

interface DraftResumeCardProps {
  draft: Event
  onOpen: (id: number) => void
}

const cardClass =
  'snap-start shrink-0 w-64 h-10 rounded-xl border border-border/50 bg-background/40 hover:bg-background/80 px-3 flex items-center gap-2 transition-all duration-200 motion-safe:hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background'

export default function DraftResumeCard({ draft, onOpen }: Readonly<DraftResumeCardProps>) {
  const relativeTime = formatRelativeTime(draft.updatedAt ?? draft.createdAt)
  const title = draft.title.trim() || 'Brouillon sans titre'
  const ariaLabel = `Reprendre le brouillon « ${title} », modifié ${relativeTime}`

  return (
    <button
      type="button"
      onClick={() => onOpen(draft.id)}
      aria-label={ariaLabel}
      className={cardClass}
    >
      <div className="min-w-0 flex-1 text-left">
        <p
          className={
            draft.title.trim()
              ? 'truncate text-sm font-medium text-foreground'
              : 'truncate text-sm italic text-foreground/60'
          }
        >
          {title}
        </p>
        <p className="text-xs text-foreground/50">{relativeTime}</p>
      </div>
    </button>
  )
}
