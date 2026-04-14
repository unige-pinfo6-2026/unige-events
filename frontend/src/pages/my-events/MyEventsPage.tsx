import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Calendar, Heart, LayoutDashboard, Pencil, Plus, Send, Star, Trash2, Users } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useMyParticipations } from '@/hooks/useMyParticipations'
import { useMyEvents } from '@/hooks/useMyEvents'
import { useToast } from '@/hooks/useToast'
import EventCard from '@/components/event/EventCard'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ButtonPrimary } from '@/components/utils/Buttons'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import { getFavorites } from '@/services/favoriteApi'
import { EVENT_STATUSES, type Event, type EventStatus } from '@/types/event'
import { formatEventDateTimeCompact } from '@/utils/dateTime'

// ─── Types & données ──────────────────────────────────────────────────────────

type TabKey = keyof typeof TABS

const TABS = {
  favoris:       { label: 'Mes Favoris',       icon: Heart },
  participations:{ label: 'Mes Participations',icon: Calendar },
  publications:  { label: 'Mes Publications',  icon: LayoutDashboard },
} as const

const TAB_KEYS = Object.keys(TABS) as TabKey[]

const STATUS_TABS = {
  PUBLISHED: { label: 'Publiés',    param: 'published' },
  DRAFT:     { label: 'Brouillons', param: 'draft' },
  CANCELLED: { label: 'Annulés',    param: 'cancelled' },
} as const

const STATUS_TAB_KEYS = Object.keys(STATUS_TABS) as EventStatus[]

const statusBadgeVariants = {
  PUBLISHED: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
  DRAFT:     'bg-amber-500/20 text-amber-400 border-amber-500/40',
  CANCELLED: 'bg-red-500/20 text-red-400 border-red-500/40',
} as const

function paramToStatus(param: string | null): EventStatus {
  for (const key of STATUS_TAB_KEYS) {
    if (STATUS_TABS[key].param === param) return key
  }
  return 'PUBLISHED'
}

function normalizeTab(raw: string | null): TabKey {
  return (raw && (TAB_KEYS as string[]).includes(raw) ? raw : 'favoris') as TabKey
}

// ─── Helpers UI ───────────────────────────────────────────────────────────────

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  subtitle?: string
  children?: React.ReactNode
}

function EmptyState({ icon: Icon, title, subtitle, children }: Readonly<EmptyStateProps>) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <Icon className="w-16 h-16 text-foreground/20" />
      <p className="text-foreground/50 text-lg font-medium">{title}</p>
      {subtitle && <p className="text-foreground/35 text-sm">{subtitle}</p>}
      {children}
    </div>
  )
}

function EventGridFixture() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {[0, 1, 2, 3].map(i => (
        <div key={i} className="h-[360px] rounded-3xl" />
      ))}
    </div>
  )
}

function MyEventsSkeleton() {
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  return (
    <Skeleton name="my-events" loading animate="pulse" color={skeletonColor}>
      <EventGridFixture />
    </Skeleton>
  )
}

// ─── Tabs principaux ──────────────────────────────────────────────────────────

function MainTabs({ active, onChange }: Readonly<{ active: TabKey; onChange: (tab: TabKey) => void }>) {
  return (
    <div className="flex items-center gap-1 overflow-x-auto border-b border-border">
      {TAB_KEYS.map(key => {
        const { label, icon: Icon } = TABS[key]
        const isActive = key === active
        const base = 'inline-flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap transition-colors border-b-2 -mb-px cursor-pointer bg-transparent'
        const state = isActive
          ? 'border-accent text-foreground'
          : 'border-transparent text-foreground/50 hover:text-foreground'
        return (
          <button key={key} type="button" onClick={() => onChange(key)} className={`${base} ${state}`}>
            <Icon className="size-4 shrink-0" />
            {label}
          </button>
        )
      })}
    </div>
  )
}

// ─── Sous-tab : Favoris ───────────────────────────────────────────────────────

function FavoritesTab() {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchFavorites = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getFavorites()
      setEvents(data)
    } catch {
      setError('Impossible de charger vos favoris.')
    } finally {
      setLoading(false)
    }
  }, [setEvents, setError, setLoading])

  useEffect(() => { fetchFavorites() }, [fetchFavorites])

  function handleRemove(eventId: number) {
    setEvents(prev => prev.filter(e => e.id !== eventId))
  }

  if (loading) return <MyEventsSkeleton />
  if (error) return <InfoMessage type="error" message={error} />
  if (events.length === 0) return (
    <EmptyState
      icon={Star}
      title="Aucun favori pour le moment"
      subtitle="Explorez des événements et ajoutez-les à vos favoris."
    />
  )

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {events.map(event => (
        <EventCard
          key={event.id}
          event={event}
          favorited
          onFavoriteRemove={() => handleRemove(event.id)}
        />
      ))}
    </div>
  )
}

// ─── Sous-tab : Participations ────────────────────────────────────────────────

function ParticipationsTab() {
  const { events, loading, error } = useMyParticipations()
  const { favoritedIds } = useFavoritesContext()

  if (loading) return <MyEventsSkeleton />
  if (error) return <InfoMessage type="error" message={error} />
  if (events.length === 0) return (
    <EmptyState
      icon={Calendar}
      title="Vous ne participez à aucun événement"
      subtitle="Retrouvez ici tous les événements auxquels vous vous inscrivez."
    />
  )

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {events.map(event => (
        <div key={event.id} className="relative">
          <span className="absolute top-4 left-1/2 -translate-x-1/2 z-10 text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full bg-emerald-500/90 text-white shadow-md">
            Inscrit
          </span>
          <EventCard event={event} favorited={favoritedIds.has(event.id)} />
        </div>
      ))}
    </div>
  )
}

// ─── Sous-tab : Publications ──────────────────────────────────────────────────

function StatusSubTabs({ active, onChange }: Readonly<{ active: EventStatus; onChange: (s: EventStatus) => void }>) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      {STATUS_TAB_KEYS.map(key => {
        const { label } = STATUS_TABS[key]
        const isActive = key === active
        const base = 'px-4 py-1.5 rounded-full text-xs font-semibold border cursor-pointer bg-transparent transition-colors'
        const state = isActive
          ? 'border-accent text-accent bg-accent/10'
          : 'border-border text-foreground/60 hover:text-foreground hover:border-foreground/30'
        return (
          <button key={key} type="button" onClick={() => onChange(key)} className={`${base} ${state}`}>
            {label}
          </button>
        )
      })}
    </div>
  )
}

interface ConfirmModalProps {
  title: string
  message: React.ReactNode
  confirmLabel: string
  pending: boolean
  onConfirm: () => void
  onClose: () => void
}

function ConfirmModal({ title, message, confirmLabel, pending, onConfirm, onClose }: Readonly<ConfirmModalProps>) {
  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">
        <h2 className="text-lg font-bold text-foreground mb-2">{title}</h2>
        <div className="text-sm text-foreground/50 mb-6">{message}</div>
        <div className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
          >
            Annuler
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
          >
            {pending ? '…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

interface PublicationRowProps {
  event: Event
  onPublish: (id: number) => void
  onCancel: (event: Event) => void
  publishing: boolean
}

function PublicationRow({ event, onPublish, onCancel, publishing }: Readonly<PublicationRowProps>) {
  const badge = statusBadgeVariants[event.status]
  return (
    <tr className="border-b border-border/50 hover:bg-foreground/3 transition-colors">
      <td className="px-4 py-4">
        <Link to={`/events/${event.id}`} className="text-sm font-semibold text-foreground hover:text-accent no-underline">
          {event.title}
        </Link>
      </td>
      <td className="px-4 py-4 text-sm text-foreground/60 whitespace-nowrap">
        {formatEventDateTimeCompact(event.startDate)}
      </td>
      <td className="px-4 py-4 text-sm text-foreground/60">
        <span className="inline-flex items-center gap-1.5">
          <Users className="size-3.5" />
          {event.attendingCount}
          {event.capacity != null && <span className="text-foreground/30">/ {event.capacity}</span>}
        </span>
      </td>
      <td className="px-4 py-4">
        <span className={`inline-block text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full border ${badge}`}>
          {EVENT_STATUSES[event.status].name}
        </span>
      </td>
      <td className="px-4 py-4">
        <div className="flex items-center gap-2 justify-end">
          <Link
            to={`/events/${event.id}/edit`}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-foreground/70 text-xs font-semibold no-underline hover:border-foreground/30 hover:text-foreground transition-colors"
          >
            <Pencil className="size-3.5" />
            Modifier
          </Link>
          {event.status === 'DRAFT' && (
            <button
              type="button"
              onClick={() => onPublish(event.id)}
              disabled={publishing}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors disabled:opacity-50"
            >
              <Send className="size-3.5" />
              Publier
            </button>
          )}
          {event.status !== 'CANCELLED' && (
            <button
              type="button"
              onClick={() => onCancel(event)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-error/10 border border-error/30 text-error text-xs font-semibold cursor-pointer hover:bg-error/20 transition-colors"
            >
              <Trash2 className="size-3.5" />
              Annuler
            </button>
          )}
        </div>
      </td>
    </tr>
  )
}

function PublicationCard({ event, onPublish, onCancel, publishing }: Readonly<PublicationRowProps>) {
  const badge = statusBadgeVariants[event.status]
  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-2xl p-5 border border-border flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <Link to={`/events/${event.id}`} className="text-base font-semibold text-foreground hover:text-accent no-underline">
          {event.title}
        </Link>
        <span className={`inline-block text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full border shrink-0 ${badge}`}>
          {EVENT_STATUSES[event.status].name}
        </span>
      </div>
      <div className="flex flex-col gap-1 text-xs text-foreground/60">
        <span className="inline-flex items-center gap-1.5">
          <Calendar className="size-3.5 shrink-0" />
          {formatEventDateTimeCompact(event.startDate)}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <Users className="size-3.5 shrink-0" />
          {event.attendingCount}{event.capacity != null ? ` / ${event.capacity}` : ''} participants
        </span>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <Link
          to={`/events/${event.id}/edit`}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-foreground/70 text-xs font-semibold no-underline hover:border-foreground/30 hover:text-foreground transition-colors"
        >
          <Pencil className="size-3.5" />
          Modifier
        </Link>
        {event.status === 'DRAFT' && (
          <button
            type="button"
            onClick={() => onPublish(event.id)}
            disabled={publishing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors disabled:opacity-50"
          >
            <Send className="size-3.5" />
            Publier
          </button>
        )}
        {event.status !== 'CANCELLED' && (
          <button
            type="button"
            onClick={() => onCancel(event)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-error/10 border border-error/30 text-error text-xs font-semibold cursor-pointer hover:bg-error/20 transition-colors"
          >
            <Trash2 className="size-3.5" />
            Annuler
          </button>
        )}
      </div>
    </div>
  )
}

interface PublicationsTabProps {
  organizerId: string
  status: EventStatus
  onStatusChange: (s: EventStatus) => void
}

function PublicationsTab({ organizerId, status, onStatusChange }: Readonly<PublicationsTabProps>) {
  const { events, loading, error, publish, cancel } = useMyEvents(organizerId, status)
  const toast = useToast()
  const [toCancel, setToCancel] = useState<Event | null>(null)
  const [pending, setPending] = useState(false)
  const [publishingId, setPublishingId] = useState<number | null>(null)

  async function handlePublish(id: number) {
    setPublishingId(id)
    const ok = await publish(id)
    setPublishingId(null)
    if (ok) toast.showToast('success', 'Événement publié.')
    else toast.showToast('error', 'Impossible de publier cet événement.')
  }

  async function handleCancel() {
    if (!toCancel) return
    setPending(true)
    const ok = await cancel(toCancel.id)
    setPending(false)
    setToCancel(null)
    if (ok) toast.showToast('success', 'Événement annulé.')
    else toast.showToast('error', 'Impossible d\'annuler cet événement.')
  }

  return (
    <div className="flex flex-col gap-6">
      <StatusSubTabs active={status} onChange={onStatusChange} />

      {loading && <MyEventsSkeleton />}
      {!loading && error && <InfoMessage type="error" message={error} />}
      {!loading && !error && events.length === 0 && (
        <EmptyState
          icon={LayoutDashboard}
          title="Aucun événement dans cette catégorie"
          subtitle="Créez votre premier événement pour démarrer."
        >
          <Link to="/events/new" className="mt-2">
            <ButtonPrimary size="sm">
              <Plus className="size-4" />
              Créer un événement
            </ButtonPrimary>
          </Link>
        </EmptyState>
      )}
      {!loading && !error && events.length > 0 && (
        <>
          {/* Desktop : table */}
          <div className="hidden md:block overflow-x-auto rounded-2xl border border-border bg-background/40 backdrop-blur-xl">
            <table className="w-full text-left border-collapse">
              <thead className="bg-foreground/5">
                <tr>
                  <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-widest text-foreground/40">Titre</th>
                  <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-widest text-foreground/40">Date</th>
                  <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-widest text-foreground/40">Participants</th>
                  <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-widest text-foreground/40">Statut</th>
                  <th className="px-4 py-3 text-[11px] font-bold uppercase tracking-widest text-foreground/40 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {events.map(event => (
                  <PublicationRow
                    key={event.id}
                    event={event}
                    onPublish={handlePublish}
                    onCancel={setToCancel}
                    publishing={publishingId === event.id}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile : cards empilées */}
          <div className="md:hidden flex flex-col gap-3">
            {events.map(event => (
              <PublicationCard
                key={event.id}
                event={event}
                onPublish={handlePublish}
                onCancel={setToCancel}
                publishing={publishingId === event.id}
              />
            ))}
          </div>
        </>
      )}

      {toCancel && (
        <ConfirmModal
          title="Annuler l'événement ?"
          message={<>Cette action annulera l'événement <strong className="text-foreground">"{toCancel.title}"</strong>. Elle est irréversible.</>}
          confirmLabel="Confirmer"
          pending={pending}
          onConfirm={handleCancel}
          onClose={() => setToCancel(null)}
        />
      )}
    </div>
  )
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function MyEventsPage() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = normalizeTab(searchParams.get('tab'))
  const activeStatus = paramToStatus(searchParams.get('status'))

  const setTab = useCallback((tab: TabKey) => {
    const next = new URLSearchParams(searchParams)
    next.set('tab', tab)
    if (tab !== 'publications') next.delete('status')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams])

  const setStatus = useCallback((status: EventStatus) => {
    const next = new URLSearchParams(searchParams)
    next.set('tab', 'publications')
    next.set('status', STATUS_TABS[status].param)
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams])

  const tabContent = useMemo(() => {
    if (activeTab === 'favoris') return <FavoritesTab />
    if (activeTab === 'participations') return <ParticipationsTab />
    if (!user) return null
    return (
      <PublicationsTab
        organizerId={user.id}
        status={activeStatus}
        onStatusChange={setStatus}
      />
    )
  }, [activeTab, activeStatus, user, setStatus])

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Événements</mark></>}
        subtitle="Vos favoris, vos participations et vos publications, centralisés."
      />

      <MainTabs active={activeTab} onChange={setTab} />

      {tabContent}

      {activeTab === 'publications' && (
        <Link
          to="/events/new"
          aria-label="Créer un événement"
          className="fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
        >
          <Plus className="size-5" />
          Créer un événement
        </Link>
      )}
    </SectionWrapper>
  )
}
