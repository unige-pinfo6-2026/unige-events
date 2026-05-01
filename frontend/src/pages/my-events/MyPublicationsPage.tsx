import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Ban, Calendar, LayoutDashboard, Pencil, Plus, Send, Trash2, Undo2, Users } from 'lucide-react'
import { useMyEvents } from '@/hooks/useMyEvents'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ButtonPrimary } from '@/components/utils/Buttons'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { EVENT_CATEGORIES, EVENT_STATUSES, type Event, type EventStatus } from '@/types/event'
import { EVENT_STATUS_VARIANTS } from '@/utils/eventStatusStyles'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import { formatEventDateTimeCompact } from '@/utils/dateTime'
import { PublicationGridFixture } from './shared'

// ─── Const maps ───────────────────────────────────────────────────────────────

const STATUS_TABS = {
  PUBLISHED: { label: 'Publiés',    param: 'published' },
  DRAFT:     { label: 'Brouillons', param: 'draft' },
  CANCELLED: { label: 'Annulés',    param: 'cancelled' },
} as const

const STATUS_KEYS = Object.keys(STATUS_TABS) as EventStatus[]

function paramToStatus(param: string | null): EventStatus {
  for (const key of STATUS_KEYS) {
    if (STATUS_TABS[key].param === param) return key
  }
  return 'PUBLISHED'
}

// ─── Sous-tabs statut ─────────────────────────────────────────────────────────

interface StatusTabsProps {
  active: EventStatus
  onChange: (status: EventStatus) => void
}

function StatusTabs({ active, onChange }: Readonly<StatusTabsProps>) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      {STATUS_KEYS.map(key => {
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

// ─── Confirm modal ────────────────────────────────────────────────────────────

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

// ─── Publish errors modal ────────────────────────────────────────────────────

interface PublishErrorsModalProps {
  errors: string[]
  onClose: () => void
}

function PublishErrorsModal({ errors, onClose }: Readonly<PublishErrorsModalProps>) {
  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-background border border-border rounded-3xl p-8 max-w-md w-[90%] shadow-2xl">
        <h2 className="text-lg font-bold text-foreground mb-3">Impossible de publier cet événement</h2>
        <p className="text-sm text-foreground/60 mb-3">Corrigez les points suivants avant de publier :</p>
        <ul className="list-disc pl-5 mb-6 text-sm text-error space-y-1">
          {errors.map((err) => <li key={err}>{err}</li>)}
        </ul>
        <div className="flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
          >
            Fermer
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── PublicationCard ──────────────────────────────────────────────────────────

interface PublicationCardProps {
  event: Event
  publishing: boolean
  restoring: boolean
  onPublish: (id: number) => void
  onCancel: (event: Event) => void
  onRestore: (id: number) => void
  onDelete: (event: Event) => void
}

function PublicationCard({ event, publishing, restoring, onPublish, onCancel, onRestore, onDelete }: Readonly<PublicationCardProps>) {
  const category = EVENT_CATEGORIES[event.category]
  const statusClass = EVENT_STATUS_VARIANTS[event.status]
  const banner = event.bannerUrl
    ? { backgroundImage: `url(${event.bannerUrl})` }
    : { background: `linear-gradient(135deg, ${category.color}55, ${category.color}cc)` }

  const stop = (e: React.MouseEvent) => e.stopPropagation()

  return (
    <article className="group flex flex-col rounded-2xl bg-background border border-border overflow-hidden transition-all duration-200 hover:border-primary/30 hover:shadow-xl hover:shadow-black/10 hover:-translate-y-0.5">
      <Link to={`/events/${event.id}`} className="flex flex-col flex-1 no-underline text-inherit" aria-label={event.title}>
        {/* Banner */}
        <div className="relative h-36 bg-cover bg-center" style={banner}>
          <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: category.color }} />
          <div className="absolute inset-0 bg-linear-to-t from-black/50 to-transparent" />

          <span
            className="absolute top-3 left-3 text-white text-[10px] font-bold px-2.5 py-1 rounded-full tracking-wide backdrop-blur-sm"
            style={{ background: `${category.color}dd` }}
          >
            {category.name}
          </span>
        </div>

        {/* Body */}
        <div className="flex flex-col gap-2 p-4 flex-1">
          <div className="flex items-start gap-3">
            <span
              title={event.title}
              className="flex-1 min-w-0 text-base font-bold text-foreground group-hover:text-accent line-clamp-1 break-words"
            >
              {event.title}
            </span>
            <span className={`shrink-0 text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full border ${statusClass}`}>
              {EVENT_STATUSES[event.status].name}
            </span>
          </div>
          {event.faculty ? (
            <FacultyBadge id={event.faculty} />
          ) : (
            <span className="inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full bg-foreground/10 text-foreground/70">
              Toutes facultés
            </span>
          )}
          <div className="flex items-center gap-2 text-xs text-foreground/55">
            <Calendar className="size-3.5 shrink-0" style={{ color: category.color }} />
            <span className="truncate">{formatEventDateTimeCompact(event.startDate, event.allDay)}</span>
          </div>
          <div className="flex items-center gap-2 text-xs text-foreground/55">
            <Users className="size-3.5 shrink-0" style={{ color: category.color }} />
            <span>
              {event.attendingCount}
              {event.capacity != null && <span className="text-foreground/35"> / {event.capacity}</span>} participants
            </span>
          </div>
        </div>
      </Link>

      {/* Actions */}
      <div className="flex flex-wrap gap-2 p-3 border-t border-border">
        {event.status === 'CANCELLED' ? (
          <>
            <button
              type="button"
              onClick={(e) => { stop(e); onRestore(event.id) }}
              disabled={restoring}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-foreground/70 text-xs font-semibold cursor-pointer bg-transparent hover:border-foreground/30 hover:text-foreground transition-colors disabled:opacity-50"
            >
              <Undo2 className="size-3.5" />
              Remettre en brouillon
            </button>
            <button
              type="button"
              onClick={(e) => { stop(e); onDelete(event) }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-error/10 border border-error/30 text-error text-xs font-semibold cursor-pointer hover:bg-error/20 transition-colors ml-auto"
            >
              <Trash2 className="size-3.5" />
              Supprimer
            </button>
          </>
        ) : (
          <>
            <Link
              to={`/events/${event.id}/edit`}
              onClick={stop}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border text-foreground/70 text-xs font-semibold no-underline hover:border-foreground/30 hover:text-foreground transition-colors"
            >
              <Pencil className="size-3.5" />
              Modifier
            </Link>
            {event.status === 'DRAFT' && (
              <button
                type="button"
                onClick={(e) => { stop(e); onPublish(event.id) }}
                disabled={publishing}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold cursor-pointer hover:bg-emerald-500/20 transition-colors disabled:opacity-50"
              >
                <Send className="size-3.5" />
                Publier
              </button>
            )}
            <button
              type="button"
              onClick={(e) => { stop(e); onCancel(event) }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-error/10 border border-error/30 text-error text-xs font-semibold cursor-pointer hover:bg-error/20 transition-colors ml-auto"
            >
              <Ban className="size-3.5" />
              Annuler
            </button>
          </>
        )}
      </div>
    </article>
  )
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function MyPublicationsPage() {
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = paramToStatus(searchParams.get('status'))

  const { events, loading, error, publish, cancel, restore, permanentlyDelete } = useMyEvents(status)
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [toCancel, setToCancel] = useState<Event | null>(null)
  const [toDelete, setToDelete] = useState<Event | null>(null)
  const [pending, setPending] = useState(false)
  const [deletePending, setDeletePending] = useState(false)
  const [publishingId, setPublishingId] = useState<number | null>(null)
  const [restoringId, setRestoringId] = useState<number | null>(null)
  const [publishErrors, setPublishErrors] = useState<string[] | null>(null)

  const setStatus = useCallback((next: EventStatus) => {
    const sp = new URLSearchParams(searchParams)
    sp.set('status', STATUS_TABS[next].param)
    setSearchParams(sp, { replace: true })
  }, [searchParams, setSearchParams])

  async function handlePublish(id: number) {
    setPublishingId(id)
    const result = await publish(id)
    setPublishingId(null)
    if (result.ok) {
      toast.showToast('success', 'Événement publié.')
      return
    }
    if (result.errors.length > 0) {
      setPublishErrors(result.errors)
    } else {
      toast.showToast('error', 'Impossible de publier cet événement.')
    }
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

  async function handleRestore(id: number) {
    setRestoringId(id)
    const ok = await restore(id)
    setRestoringId(null)
    if (ok) toast.showToast('success', 'Événement remis en brouillon.')
    else toast.showToast('error', 'Impossible de restaurer cet événement.')
  }

  async function handleDelete() {
    if (!toDelete) return
    setDeletePending(true)
    const ok = await permanentlyDelete(toDelete.id)
    setDeletePending(false)
    setToDelete(null)
    if (ok) toast.showToast('success', 'Événement supprimé définitivement.')
    else toast.showToast('error', 'Impossible de supprimer cet événement.')
  }

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Publications</mark></>}
        subtitle="Gérez les événements que vous organisez."
      />

      <StatusTabs active={status} onChange={setStatus} />

      {loading && (
        <Skeleton name="my-publications" loading animate="pulse" color={skeletonColor}>
          <PublicationGridFixture />
        </Skeleton>
      )}
      {!loading && error && <InfoMessage type="error" message={error} />}
      {!loading && !error && events.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
          <LayoutDashboard className="w-16 h-16 text-foreground/20" />
          <p className="text-foreground/50 text-lg font-medium">Aucun événement dans cette catégorie</p>
          <p className="text-foreground/35 text-sm">Créez votre premier événement pour démarrer.</p>
          <Link to="/events/new" className="mt-2">
            <ButtonPrimary size="sm">
              <Plus className="size-4" />
              Créer un événement
            </ButtonPrimary>
          </Link>
        </div>
      )}
      {!loading && !error && events.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
          {events.map(event => (
            <PublicationCard
              key={event.id}
              event={event}
              publishing={publishingId === event.id}
              restoring={restoringId === event.id}
              onPublish={handlePublish}
              onCancel={setToCancel}
              onRestore={handleRestore}
              onDelete={setToDelete}
            />
          ))}
        </div>
      )}

      <Link
        to="/events/new"
        aria-label="Créer un événement"
        className="sticky bottom-6 self-end z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
      >
        <Plus className="size-5" />
        Créer un événement
      </Link>

      {toCancel && (
        <ConfirmModal
          title="Annuler l'événement ?"
          message={<>Cette action annulera l'événement <strong className="text-foreground">"{toCancel.title}"</strong>. Vous pourrez le remettre en brouillon depuis l'onglet Annulés.</>}
          confirmLabel="Confirmer"
          pending={pending}
          onConfirm={handleCancel}
          onClose={() => setToCancel(null)}
        />
      )}

      {publishErrors && (
        <PublishErrorsModal errors={publishErrors} onClose={() => setPublishErrors(null)} />
      )}

      {toDelete && (
        <ConfirmModal
          title="Supprimer définitivement ?"
          message={<>Cette action supprimera définitivement l'événement <strong className="text-foreground">"{toDelete.title}"</strong>. Cette opération est irréversible.</>}
          confirmLabel="Supprimer"
          pending={deletePending}
          onConfirm={handleDelete}
          onClose={() => setToDelete(null)}
        />
      )}
    </SectionWrapper>
  )
}
