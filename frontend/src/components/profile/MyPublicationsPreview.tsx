import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, LayoutDashboard, Plus, RefreshCw } from 'lucide-react'
import { useMyEvents } from '@/hooks/useMyEvents'
import { type EventStatus } from '@/types/event'
import { EVENT_STATUS_PARAMS } from '@/utils/eventStatusStyles'
import PreviewRow from './PreviewRow'

const TAB_LABELS: Record<EventStatus, string> = {
  PUBLISHED: 'Publiés',
  DRAFT:     'Brouillons',
  CANCELLED: 'Annulés',
  EXPIRED:   'Terminés',
}

const TAB_KEYS = Object.keys(TAB_LABELS) as EventStatus[]

const EMPTY_COPY: Record<EventStatus, string> = {
  PUBLISHED: 'Aucune publication.',
  DRAFT:     'Aucun brouillon.',
  CANCELLED: 'Aucun événement annulé.',
  EXPIRED:   'Aucun événement terminé.',
}

const PREVIEW_LIMIT = 3

interface TabsProps {
  active: EventStatus
  count: number
  onChange: (status: EventStatus) => void
}

// The hook only fetches one status at a time, so we only know the count for
// the active tab. Inactive tabs render plain labels — adding parallel fetches
// just to display counts on tabs the user is not looking at would waste
// requests for marginal value.
function Tabs({ active, count, onChange }: Readonly<TabsProps>) {
  const base = 'px-3 py-1 rounded-full text-xs font-semibold border cursor-pointer bg-transparent transition-colors'
  const activeState = 'border-accent text-accent bg-accent/10'
  const inactiveState = 'border-border text-foreground/60 hover:text-foreground hover:border-foreground/30'
  return (
    <div className="flex items-center gap-2 flex-wrap" role="tablist" aria-label="Filtrer mes publications">
      {TAB_KEYS.map(key => {
        const isActive = key === active
        return (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={isActive}
            aria-pressed={isActive}
            onClick={() => onChange(key)}
            className={`${base} ${isActive ? activeState : inactiveState}`}
          >
            {TAB_LABELS[key]}
            {isActive && ` (${count})`}
          </button>
        )
      })}
    </div>
  )
}

function LoadingRows() {
  return (
    <div className="flex flex-col gap-2" aria-label="Chargement des publications" aria-busy="true">
      {Array.from({ length: PREVIEW_LIMIT }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 p-2 rounded-2xl">
          <div className="size-12 rounded-xl bg-foreground/10 shrink-0 animate-pulse" />
          <div className="flex-1 flex flex-col gap-1.5">
            <div className="h-3.5 w-3/5 rounded bg-foreground/10 animate-pulse" />
            <div className="h-3 w-2/5 rounded bg-foreground/10 animate-pulse" />
          </div>
          <div className="h-4 w-16 rounded-full bg-foreground/10 shrink-0 animate-pulse" />
        </div>
      ))}
    </div>
  )
}

interface EmptyStateProps {
  status: EventStatus
}

function EmptyState({ status }: Readonly<EmptyStateProps>) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
      <LayoutDashboard className="w-10 h-10 text-foreground/20" />
      <p className="text-foreground/50 text-sm font-medium">{EMPTY_COPY[status]}</p>
      {status === 'PUBLISHED' && (
        <Link
          to="/events/new"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-linear-to-r from-accent to-pink-600 text-white text-sm font-semibold no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
        >
          <Plus className="size-4" />
          Créer un événement
        </Link>
      )}
    </div>
  )
}

interface ErrorStateProps {
  onRetry: () => void
}

function ErrorState({ onRetry }: Readonly<ErrorStateProps>) {
  return (
    <div className="flex flex-col items-center gap-3 py-6 text-center">
      <p className="text-sm text-error">Impossible de charger vos publications.</p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-foreground/70 text-xs font-semibold cursor-pointer bg-transparent hover:border-foreground/30 hover:text-foreground transition-colors"
      >
        <RefreshCw className="size-3.5" />
        Réessayer
      </button>
    </div>
  )
}

export default function MyPublicationsPreview() {
  const [status, setStatus] = useState<EventStatus>('PUBLISHED')
  const { events, loading, error, refresh } = useMyEvents(status)

  const visible = events.slice(0, PREVIEW_LIMIT)

  return (
    <section
      aria-labelledby="my-publications-preview-heading"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-4"
    >
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h2 id="my-publications-preview-heading" className="text-xs font-bold uppercase tracking-widest text-foreground/30">
          Mes publications
        </h2>
      </div>

      <Tabs active={status} count={events.length} onChange={setStatus} />

      {loading && <LoadingRows />}
      {!loading && error && <ErrorState onRetry={refresh} />}
      {!loading && !error && visible.length === 0 && <EmptyState status={status} />}
      {!loading && !error && visible.length > 0 && (
        <div className="flex flex-col gap-1">
          {visible.map(event => (
            <PreviewRow key={event.id} event={event} />
          ))}
        </div>
      )}

      <Link
        to={`/my-events/publications?status=${EVENT_STATUS_PARAMS[status]}`}
        className="self-start inline-flex items-center gap-1.5 text-sm font-semibold text-accent no-underline hover:underline"
      >
        Voir toutes mes publications
        <ArrowRight className="size-4" />
      </Link>
    </section>
  )
}
