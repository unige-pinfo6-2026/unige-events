import { Calendar, FilePen, MapPin, Tag } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { EVENT_CATEGORIES, type Event } from '@/types/event'
import { formatRelativeTime } from '@/utils/formatRelativeTime'
import { parseApiUtcDateTime } from '@/utils/dateTime'

interface DraftResumeCardProps {
  draft: Event
  onOpen: (id: number) => void
}

type TitleVariant = 'filled' | 'empty'
type MetaKind = 'location' | 'date' | 'category'

interface MetaLine {
  kind: MetaKind
  icon: LucideIcon
  label: string
}

const titleVariants: Record<TitleVariant, string> = {
  filled: 'truncate text-sm font-semibold text-foreground',
  empty: 'truncate text-sm italic text-foreground/60',
}

const CARD_CLASS =
  'group relative shrink-0 snap-start w-72 h-[72px] overflow-hidden rounded-xl border border-border bg-background/60 backdrop-blur-xl flex text-left transition-all duration-200 hover:border-foreground/30 motion-safe:hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background cursor-pointer'

function formatShortDate(dateTime: string): string {
  if (!dateTime) return ''
  const date = parseApiUtcDateTime(dateTime)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('fr-CH', { day: 'numeric', month: 'short' })
}

function resolveMeta(draft: Event, categoryName: string): MetaLine {
  const location = draft.location.trim()
  if (location) return { kind: 'location', icon: MapPin, label: location }

  const shortDate = formatShortDate(draft.startDate)
  if (shortDate) return { kind: 'date', icon: Calendar, label: shortDate }

  return { kind: 'category', icon: Tag, label: categoryName }
}

export default function DraftResumeCard({ draft, onOpen }: Readonly<DraftResumeCardProps>) {
  const category = EVENT_CATEGORIES[draft.category]
  const relativeTime = formatRelativeTime(draft.updatedAt ?? draft.createdAt)
  const trimmedTitle = draft.title.trim()
  const titleVariant: TitleVariant = trimmedTitle ? 'filled' : 'empty'
  const title = trimmedTitle || 'Brouillon sans titre'
  const meta = resolveMeta(draft, category.name)
  const MetaIcon = meta.icon
  const ariaLabel = `Reprendre le brouillon « ${title} » — ${category.name}, modifié ${relativeTime}`

  return (
    <button type="button" onClick={() => onOpen(draft.id)} aria-label={ariaLabel} className={CARD_CLASS}>
      <span
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{ background: `linear-gradient(to right, ${category.color}26, transparent 72%)` }}
      />
      <span
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-0 w-[3px]"
        style={{ background: category.color }}
      />
      <span
        aria-hidden
        className="pointer-events-none absolute top-0 right-0 w-20 h-20 bg-linear-to-br from-foreground/5 to-transparent rounded-bl-full"
      />
      <div className="relative flex min-w-0 flex-1 flex-col justify-center gap-1 pl-4 pr-3">
        <div className="flex items-center gap-2 min-w-0">
          <p className={`${titleVariants[titleVariant]} flex-1 min-w-0`}>{title}</p>
          <span className="flex items-center gap-1 shrink-0 text-[10px] font-bold uppercase tracking-widest px-2 py-0.5 rounded-full border bg-warning/20 text-warning border-warning/40">
            <FilePen className="w-3 h-3" aria-hidden />
            Brouillon
          </span>
        </div>
        <div className="flex items-center gap-1.5 min-w-0 text-xs text-foreground/55">
          <MetaIcon className="w-3.5 h-3.5 shrink-0" style={{ color: category.color }} aria-hidden />
          <span className="truncate">{meta.label}</span>
          <span aria-hidden className="text-foreground/25">·</span>
          <span className="shrink-0 text-foreground/45">{relativeTime}</span>
        </div>
      </div>
    </button>
  )
}
