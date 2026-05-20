import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent, useFavorite, useOccurrences } from '@/hooks'
import { useAttendees } from '@/hooks/useAttendees'
import { useReport } from '@/hooks/useReport'
import { useToast } from '@/hooks/useToast'
import { getUserById } from '@/services/userService'
import { cancelEvent, deleteEvent, restoreEvent } from '@/services/eventApi'
import { recordEventView } from '@/services/statsApi'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES, EVENT_STATUSES, type Event, type EventStatus } from '@/types/event'
import { formatEventDateTime, formatEventDateTimeCompact } from '@/utils/dateTime'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import AttendanceButtons from '@/components/event/AttendanceButtons'
import AttendeesList from '@/components/attendees/AttendeesList'
import ReportModal from '@/components/event/ReportModal'
import EventBanner from '@/components/event/EventBanner'
import IcsExportButton from '@/components/event/IcsExportButton'
import EventStatsPanel from '@/components/event/EventStatsPanel'
import EventOrganizerTeam from '@/components/event/EventOrganizerTeam'
import DuplicateButton from '@/components/event/DuplicateButton'
import CommentSection from '@/components/event/CommentSection'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import type { LucideIcon } from 'lucide-react'
import { Ban, BarChart2, Calendar, CalendarClock, ChevronDown, ChevronUp, Flag, Globe, Mail, MapPin, Pencil, RefreshCw, Share2, Star, Tag, Trash2, Undo2, Users } from 'lucide-react'

function EventDetailFixture() {
  return (
    <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">
      {/* Main column (order-2 on mobile) */}
      <div className="flex flex-col gap-5 max-lg:order-2">
        {/* Banner h-72 mobile / h-80 desktop */}
        <div className="h-72 lg:h-80 rounded-3xl" />
        {/* Description card (h-40) */}
        <div className="h-40 rounded-3xl" />
        {/* AttendeesList compact card (90px = 2 border + 32 py-4 + 28 h2+mb + 28 summary row) */}
        <div className="h-[90px] rounded-3xl" />
        {/* "Informations complémentaires" SCRUM-117 (h-44) */}
        <div className="h-44 rounded-3xl" />
      </div>
      {/* Sidebar column (order-1 on mobile) */}
      <div className="flex flex-col gap-4 max-lg:order-1">
        {/* Card infos clés (h-72) */}
        <div className="h-72 rounded-3xl" />
        {/* Favoris/Share + AttendanceButtons card (h-44) */}
        <div className="h-44 rounded-3xl" />
        {/* IcsExportButton card (~236px = p-6 + header + 3 stacked option buttons) */}
        <div className="h-[236px] rounded-3xl" />
        {/* EventStatsPanel public card (172px = p-5 + header + 3 mini-tiles rounded-2xl) */}
        <div className="h-[172px] rounded-3xl" />
      </div>
    </div>
  )
}

// ─── Local composants non-exportés ────────────────────────────────────────────

interface InfoRowProps {
  icon: LucideIcon
  color?: string
  children: React.ReactNode
}

function InfoRow({ icon: Icon, color, children }: Readonly<InfoRowProps>) {
  return (
    <div className="flex items-start gap-3 text-sm text-foreground/60">
      <Icon
        className="w-4 h-4 shrink-0 mt-0.5"
        style={color ? { color } : undefined}
      />
      <span className="leading-snug">{children}</span>
    </div>
  )
}

function FavoriteTextButton({
  eventId,
  onAfterSuccess,
}: Readonly<{ eventId: number; onAfterSuccess?: () => void | Promise<void> }>) {
  const { favorited, loading, toggle } = useFavorite(eventId, false, { onAfterSuccess })
  return (
    <button
      type="button"
      onClick={toggle}
      disabled={loading}
      className="flex items-center justify-center gap-2 px-3 py-2 rounded-xl border border-border text-foreground text-sm font-semibold cursor-pointer bg-transparent hover:border-foreground/30 transition-colors disabled:opacity-50"
    >
      <Star
        className="w-4 h-4 shrink-0"
        fill={favorited ? '#facc15' : 'none'}
        stroke={favorited ? '#facc15' : 'currentColor'}
      />
      {favorited ? 'Retirer des favoris' : 'Ajouter aux favoris'}
    </button>
  )
}

const capacityBadgeVariants = {
  full:      'bg-error/10 border-error/30 text-error',
  low:       'bg-orange-500/10 border-orange-500/30 text-orange-400',
  available: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400',
} as const

type CapacityVariant = keyof typeof capacityBadgeVariants

function getCapacityBadge(availableSpots: number, capacity: number): { variant: CapacityVariant; label: string } {
  if (availableSpots === 0) return { variant: 'full', label: 'Complet' }
  if (availableSpots <= capacity * 0.1) return { variant: 'low', label: 'Presque complet' }
  return { variant: 'available', label: `${availableSpots} ${availableSpots === 1 ? 'place disponible' : 'places disponibles'}` }
}

interface CapacityIndicatorProps {
  capacity: number
  availableSpots: number
  waitlistedCount?: number
  isRefetching?: boolean
}

function CapacityIndicator({ capacity, availableSpots, waitlistedCount, isRefetching }: Readonly<CapacityIndicatorProps>) {
  const { variant, label } = getCapacityBadge(availableSpots, capacity)
  return (
    <div
      className={`flex flex-col gap-2 transition-opacity ${isRefetching ? 'opacity-70' : ''}`}
      aria-busy={isRefetching || undefined}
    >
      <div className="flex items-center gap-2 text-xs text-foreground/40">
        <Users className="w-4 h-4 shrink-0" />
        <span>Places disponibles</span>
      </div>
      <div className="flex flex-wrap gap-2">
        <span className={`px-2.5 py-1 rounded-lg text-xs font-semibold border ${capacityBadgeVariants[variant]}`}>
          {label}
        </span>
        {waitlistedCount != null && waitlistedCount > 0 && (
          <span className="px-2.5 py-1 rounded-lg text-xs font-semibold border bg-foreground/5 border-border/30 text-foreground/40">
            {waitlistedCount} en liste d'attente
          </span>
        )}
      </div>
    </div>
  )
}

interface ConfirmDialogProps {
  title: string
  message: React.ReactNode
  confirmLabel: string
  pending: boolean
  onConfirm: () => void
  onClose: () => void
}

function ConfirmDialog({ title, message, confirmLabel, pending, onConfirm, onClose }: Readonly<ConfirmDialogProps>) {
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
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

const occurrenceStatusVariants: Record<EventStatus, string> = {
  DRAFT:     'bg-foreground/5 border-border/40 text-foreground/55',
  PUBLISHED: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-500',
  CANCELLED: 'bg-error/10 border-error/30 text-error',
  EXPIRED:   'bg-foreground/5 border-border/40 text-foreground/40',
  BANNED:    'bg-error/10 border-error/30 text-error',
}

function OccurrenceStatusBadge({ status }: { status: EventStatus }) {
  return (
    <span className={`shrink-0 px-2 py-0.5 rounded-full border text-[10px] font-semibold uppercase tracking-wide ${occurrenceStatusVariants[status]}`}>
      {EVENT_STATUSES[status].name}
    </span>
  )
}

interface OccurrencesSectionProps {
  parentId: number
  currentEventId: number
  skeletonColor: string
}

function OccurrencesSection({ parentId, currentEventId, skeletonColor }: OccurrencesSectionProps) {
  const [open, setOpen] = useState(false)
  const { loading, error, data } = useOccurrences(parentId, { enabled: open })
  const count = data?.length ?? null

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border max-lg:order-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="w-full flex items-center justify-between gap-3 px-6 py-5 cursor-pointer bg-transparent border-0 text-left rounded-3xl hover:bg-foreground/[0.015] transition-colors"
      >
        <span className="flex items-center gap-2.5 text-foreground">
          <RefreshCw className="w-4 h-4 text-foreground/40 shrink-0" />
          <span className="text-sm font-semibold">
            Voir toutes les occurrences{count !== null ? ` (${count})` : ''}
          </span>
        </span>
        {open
          ? <ChevronUp className="w-5 h-5 text-foreground/40 shrink-0" />
          : <ChevronDown className="w-5 h-5 text-foreground/40 shrink-0" />}
      </button>

      {open && (
        <div className="border-t border-border/40 px-6 py-4">
          {loading && (
            <Skeleton name="occurrences-list" loading={true} animate="pulse" color={skeletonColor}>
              <div className="flex flex-col gap-2">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-9 rounded-lg" />
                ))}
              </div>
            </Skeleton>
          )}

          {error && <InfoMessage type="error" message={error} />}

          {!loading && !error && data && data.length === 0 && (
            <p className="text-sm text-foreground/40 py-2">Aucune occurrence à afficher.</p>
          )}

          {!loading && !error && data && data.length > 0 && (
            <ul className="flex flex-col max-h-[60vh] overflow-y-auto -mx-1">
              {data.map((occurrence: Event) => {
                const isCurrent = occurrence.id === currentEventId
                return (
                  <li
                    key={occurrence.id}
                    className={`flex items-center gap-3 px-2 py-2 rounded-lg ${isCurrent ? 'bg-accent/5 ring-1 ring-accent/30' : ''}`}
                  >
                    <span className="text-xs text-foreground/60 shrink-0 w-32 truncate">
                      {formatEventDateTimeCompact(occurrence.startDate, occurrence.allDay)}
                    </span>
                    <OccurrenceStatusBadge status={occurrence.status} />
                    <Link
                      to={`/events/${occurrence.id}`}
                      className="text-sm font-medium text-foreground hover:text-accent truncate min-w-0 flex-1 no-underline"
                    >
                      {occurrence.title}
                    </Link>
                    {isCurrent && (
                      <span className="text-[10px] font-semibold uppercase tracking-wide text-accent shrink-0">
                        Vous êtes ici
                      </span>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

// Whitelist pour le lien externe `websiteUrl` — le backend stocke l'URL avec @URL, qui
// accepte d'autres schémas (p. ex. `javascript:`). On ne rend un <a href> que si l'URL
// parse en http(s) ; sinon on affiche la chaîne brute pour éviter tout XSS/open-redirect.
function safeExternalHref(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

// ─── Page principale ───────────────────────────────────────────────────────────

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user, isAdmin } = useAuth()
  const toast = useToast()
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null
  const { event, isInitialLoad, isRefetching, error, refetch: refetchEvent } = useEvent(eventId, user?.id ?? null)
  const isAuthenticated = user !== null
  // SCRUM-137 — "isOrganizer" widens to include accepted co-organizers, who
  // share the edit/cancel/restore/stats permissions with the creator (cf.
  // EventService.update / cancel / restore). `coOrganizerOf` comes back from
  // the backend when we pass `?check-co-org-of=<caller>` on the fetch.
  const isAcceptedCoOrganizer = event !== null && event.coOrganizerOf === true
  const isCreator = user !== null && event !== null && user.id === event.creatorId
  const isOrganizer = isCreator || isAcceptedCoOrganizer
  const reportHook = useReport(eventId ?? 0)
  // SCRUM-S7: fetch for any authenticated viewer — backend handles privacy at
  // the DTO layer. Unauthenticated viewers skip the call (they only see the
  // compact avatar stack + count).
  const attendeesHook = useAttendees(eventId ?? 0, { enabled: isAuthenticated && eventId !== null })
  const refetchAttendees = attendeesHook.refetch
  const handleAttendanceSuccess = useCallback(async (): Promise<void> => {
    await Promise.all([
      refetchEvent(),
      isAuthenticated ? Promise.resolve(refetchAttendees()) : Promise.resolve(),
    ])
  }, [refetchEvent, refetchAttendees, isAuthenticated])
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const [deleting, setDeleting] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [organizer, setOrganizer] = useState<User | null>(null)
  const [bannerWarning, setBannerWarning] = useState<string | null>(null)

  useEffect(() => {
    const warning = sessionStorage.getItem(BANNER_UPLOAD_ERROR_KEY)
    if (warning) {
      sessionStorage.removeItem(BANNER_UPLOAD_ERROR_KEY)
      setBannerWarning(warning)
      const t = setTimeout(() => setBannerWarning(null), 6000)
      return () => clearTimeout(t)
    }
  }, [])

  useEffect(() => {
    // Post-V11 schema (2026-05-14) : la vue est enregistrée même pour les
    // utilisateurs anonymes, via un sessionId UUID généré côté client. La
    // dédup serveur évite le spam ((eventId, userId) pour auth, (eventId,
    // sessionId) pour anon). Cf. spec Axe 4.
    if (!eventId) return
    recordEventView(eventId).catch(() => {})
  }, [eventId])

  useEffect(() => {
    if (!event) { setOrganizer(null); return }
    let active = true
    getUserById(event.creatorId)
      .then((o) => { if (active) setOrganizer(o) })
      .catch(() => { if (active) setOrganizer(null) })
    return () => { active = false }
  }, [event])

  useEffect(() => {
    if (!event || !user) return
    if (event.status !== 'DRAFT') return
    if (isAdmin) return
    // Drafts redirect to /edit for anyone who can edit the event — both the
    // creator and any accepted co-organizer.
    const canEditDraft = user.id === event.creatorId || event.coOrganizerOf === true
    if (!canEditDraft) return
    navigate(`/events/${event.id}/edit`, { replace: true })
  }, [event, user, isAdmin, navigate])

  if (eventId === null) return <InfoMessage type='error' message="Identifiant d'événement invalide." />

  // Only the very first fetch shows the full-page skeleton. Subsequent
  // refetches (after an attendance action, etc.) keep the page visible and
  // surface progress via subtle child hints (see `isRefetching` below).
  if (isInitialLoad && event === null) return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        title={<>Détails de <mark>l'événement</mark></>}
        subtitle="Toutes les informations pour participer"
        align="left"
      />
      <Skeleton
        name="event-detail"
        loading={true}
        animate="pulse"
        color={skeletonColor}
      ><EventDetailFixture /></Skeleton>
    </SectionWrapper>
  )

  if (error && event === null) return <InfoMessage type='error' message={error} />
  if (!event) return <InfoMessage type='error' message="Événement introuvable." />

  const category = EVENT_CATEGORIES[event.category]

  function handleShare() {
    if (!event) return
    const url = globalThis.location.href
    if (typeof navigator.clipboard?.writeText !== 'function') {
      toast.showToast('error', `Copiez ce lien : ${url}`, 6000)
      return
    }
    navigator.clipboard.writeText(url)
      .then(() => toast.showToast('success', 'Lien copié !', 3000))
      .catch(() => toast.showToast('error', `Copiez ce lien : ${url}`, 6000))
  }

  async function handleDelete() {
    if (!event) return
    setDeleting(true)
    try {
      await deleteEvent(event.id)
      toast.showToast('success', 'Événement supprimé définitivement.')
      navigate('/my-events/publications?status=cancelled')
    } catch {
      setDeleting(false)
      setShowConfirm(false)
      toast.showToast('error', 'Impossible de supprimer cet événement.')
    }
  }

  async function handleCancelEvent() {
    if (!event) return
    setCancelling(true)
    try {
      await cancelEvent(event.id)
      toast.showToast('success', 'Événement annulé.')
      navigate('/my-events/publications?status=cancelled')
    } catch {
      setShowCancelConfirm(false)
      toast.showToast('error', 'Impossible d\'annuler cet événement.')
    } finally {
      setCancelling(false)
    }
  }

  async function handleRestore() {
    if (!event) return
    setRestoring(true)
    try {
      await restoreEvent(event.id)
      toast.showToast('success', 'Événement remis en brouillon.')
      navigate('/my-events/publications?status=draft')
    } catch {
      toast.showToast('error', 'Impossible de restaurer cet événement.')
    } finally {
      setRestoring(false)
    }
  }

  return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>

      <SectionHeader
        title={<>Détails de <mark>l'événement</mark></>}
        subtitle="Toutes les informations pour participer"
        align="left"
      />

      {/* Grille deux colonnes.
          Sur mobile (max-lg), les wrappers de colonne passent en `display: contents`
          afin que leurs enfants deviennent des items directs de la grille parent ;
          chaque section reçoit alors un `max-lg:order-N` pour produire l'ordre de
          lecture mobile suivant : bannière → infos clés → description → actions
          (favoris/partage/inscription) → liste des participants → infos
          complémentaires → export ICS → stats publiques → actions organisateur →
          lien vers les statistiques. Raison : sur mobile l'utilisateur a besoin
          de l'identité de l'événement et des informations clés avant la
          description, suivi de l'action immédiate (s'inscrire) avant les
          contenus secondaires. Le layout desktop reste identique. */}
      <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">

        {/* Colonne principale */}
        <div className="flex flex-col gap-5 max-lg:contents">

          {/* Bannière */}
          <EventBanner event={event} className="rounded-3xl h-72 lg:h-80 max-lg:order-1">
            <div className="absolute inset-0 bg-linear-to-t from-black/70 via-black/20 to-transparent" />
            <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: category.color }} />
            <span
              className="absolute top-4 left-4 text-white text-xs font-bold px-3 py-1.5 rounded-full tracking-wide backdrop-blur-sm"
              style={{ background: `${category.color}cc` }}
            >
              {category.name}
            </span>
            {user !== null && !isOrganizer && (
              <button
                type="button"
                onClick={reportHook.open}
                aria-label="Signaler cet événement"
                title="Signaler cet événement"
                className="group absolute top-4 right-4 inline-flex items-center justify-center h-8 px-2 rounded-full bg-black/30 backdrop-blur-sm text-white border-0 cursor-pointer overflow-hidden whitespace-nowrap transition-all duration-200 hover:bg-black/50 hover:text-error hover:px-3"
              >
                <Flag className="w-4 h-4 shrink-0" />
                <span className="max-w-0 opacity-0 ml-0 text-xs font-medium transition-all duration-200 group-hover:max-w-[80px] group-hover:opacity-100 group-hover:ml-1">
                  Signaler
                </span>
              </button>
            )}
            <div className="absolute bottom-0 left-0 right-0 p-6">
              <h1 className="text-white text-2xl lg:text-3xl font-extrabold leading-snug drop-shadow-sm wrap-anywhere">
                {event.title}
              </h1>
            </div>
          </EventBanner>

          {/* Card description */}
          {event.description && (
            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border max-lg:order-3">
              <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3">
                À propos
              </h2>
              <p className="text-foreground/70 leading-relaxed whitespace-pre-wrap text-sm wrap-anywhere">
                {event.description}
              </p>
            </div>
          )}

          {/* SCRUM-151 — collapsible list of sibling occurrences. Visible on a recurring
              parent (recurrenceRule != null) and on each of its children (parentEventId
              points back to the parent). Standalones never render the section. */}
          {(event.recurrenceRule != null || event.parentEventId != null) && (
            <OccurrencesSection
              parentId={event.parentEventId ?? event.id}
              currentEventId={event.id}
              skeletonColor={skeletonColor}
            />
          )}

          <div className="max-lg:order-5">
            <AttendeesList
              isAuthenticated={isAuthenticated}
              attendingCount={event.attendingCount}
              attendeesHook={attendeesHook}
            />
          </div>

          {/* Champs additionnels (SCRUM-117) */}
          {(event.websiteUrl || event.contactEmail || event.registrationDeadline || (event.tags && event.tags.length > 0)) && (
            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border flex flex-col gap-4 max-lg:order-6">
              <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30">
                Informations complémentaires
              </h2>

              <div className="flex flex-col gap-3">
                {event.websiteUrl && (() => {
                  const safeHref = safeExternalHref(event.websiteUrl)
                  return (
                    <InfoRow icon={Globe} color={category.color}>
                      {safeHref ? (
                        <a
                          href={safeHref}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-link hover:underline break-all"
                        >
                          {event.websiteUrl}
                        </a>
                      ) : (
                        <span className="text-foreground/70 break-all">{event.websiteUrl}</span>
                      )}
                    </InfoRow>
                  )
                })()}

                {event.contactEmail && (
                  <InfoRow icon={Mail} color={category.color}>
                    <a
                      href={`mailto:${event.contactEmail}`}
                      className="text-link hover:underline break-all"
                    >
                      {event.contactEmail}
                    </a>
                  </InfoRow>
                )}

                {event.registrationDeadline && (
                  <InfoRow icon={CalendarClock} color={category.color}>
                    <span>
                      <span className="text-foreground/40">Inscriptions jusqu'au </span>
                      <span className="text-foreground">{formatEventDateTime(event.registrationDeadline)}</span>
                    </span>
                  </InfoRow>
                )}

                {event.tags && event.tags.length > 0 && (
                  <InfoRow icon={Tag} color={category.color}>
                    <div className="flex flex-wrap gap-1.5">
                      {event.tags.map((tag) => (
                        <Link
                          key={tag}
                          to={`/events/search?q=${encodeURIComponent(tag)}`}
                          className="inline-flex items-center px-2 py-0.5 rounded-lg text-xs bg-foreground/5 border border-border/30 text-foreground/70 hover:text-foreground hover:border-foreground/30 transition-colors no-underline"
                        >
                          {tag}
                        </Link>
                      ))}
                    </div>
                  </InfoRow>
                )}
              </div>
            </div>
          )}

        </div>

        {/* Sidebar — éclatée dans la grille parent sur mobile via display: contents. */}
        <div className="flex flex-col gap-4 lg:sticky lg:top-6 max-lg:contents">

          {/* Card infos clés */}
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border flex flex-col gap-4 max-lg:order-2">

            <div className="flex flex-col gap-3">
              <InfoRow icon={Calendar} color={category.color}>
                <span>
                  {formatEventDateTime(event.startDate, event.allDay)}
                  <span className="text-foreground/30 mx-1.5">→</span>
                  {formatEventDateTime(event.endDate, event.allDay)}
                </span>
              </InfoRow>

              <InfoRow icon={MapPin} color={category.color}>
                {event.location}
              </InfoRow>

              {event.capacity !== undefined && (
                <InfoRow icon={Users} color={category.color}>
                  {event.capacity} {event.capacity === 1 ? 'place' : 'places'} au total
                </InfoRow>
              )}
            </div>

            <div className="border-t border-border" />

            {/* Organisateur — fetched in a separate effect after the event
                loads. The slot is rendered unconditionally so the sidebar
                doesn't reflow when `getUserById` resolves (otherwise the row
                pops in and pushes every card below it down ~48–56px). The
                placeholder mirrors the resolved row's dimensions (size-8
                avatar + 2-line text column) so the swap is in-place. */}
            {organizer ? (
              <Link
                to={`/profile/${organizer.username}`}
                className="flex items-center gap-3 hover:opacity-80 transition-opacity no-underline"
              >
                <UserAvatar user={organizer} className="size-8 shrink-0" />
                <div className="flex flex-col min-w-0">
                  <span className="text-xs text-foreground/40">Organisé par</span>
                  <span className="text-sm font-semibold text-foreground truncate">
                    {organizer.displayName ?? organizer.email}
                  </span>
                </div>
              </Link>
            ) : (
              <div
                className="flex items-center gap-3"
                aria-hidden="true"
                data-testid="organizer-placeholder"
              >
                <div className="size-8 rounded-full bg-foreground/5 border border-border shrink-0" />
                {/* Sized to match the resolved row's text column exactly:
                    text-xs line-height = 1rem (h-4 = 16px) +
                    text-sm line-height = 1.25rem (h-5 = 20px) → 36px stacked,
                    same as the two <span>s above. Empty divs (no text) so the
                    placeholder doesn't surface "Organisé par" in queries when
                    the fetch fails. */}
                <div className="flex flex-col min-w-0 flex-1">
                  <div className="h-4 flex items-center">
                    <div className="h-2 w-20 rounded bg-foreground/5" />
                  </div>
                  <div className="h-5 flex items-center">
                    <div className="h-3 w-32 rounded bg-foreground/5" />
                  </div>
                </div>
              </div>
            )}

            {/* Capacity indicator — S6 */}
            {event.capacity != null && event.availableSpots != null && (
              <>
                <div className="border-t border-border" />
                <CapacityIndicator
                  capacity={event.capacity}
                  availableSpots={event.availableSpots}
                  waitlistedCount={event.waitlistedCount}
                  isRefetching={isRefetching}
                />
              </>
            )}

          </div>

          {/* Card AttendanceButtons + Favoris + Participants */}
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-5 py-4 border border-border flex flex-col gap-4 max-lg:order-4">

            {/* Favoris + partager */}
            <div className="grid grid-cols-2 gap-3">
              <FavoriteTextButton eventId={event.id} onAfterSuccess={handleAttendanceSuccess} />

              <button
                type="button"
                onClick={handleShare}
                className="flex items-center justify-center gap-2 px-3 py-2 rounded-xl border border-border text-foreground text-sm font-semibold cursor-pointer bg-transparent hover:border-foreground/30 transition-colors"
              >
                <Share2 className="w-4 h-4 shrink-0" />
                Partager
              </button>
            </div>

            {/* Boutons participation */}
            <AttendanceButtons
              key={event.id}
              eventId={event.id}
              initialAttendingCount={event.attendingCount}
              initialStatus={null}
              availableSpots={event.availableSpots ?? null}
              onAfterSuccess={handleAttendanceSuccess}
            />

          </div>

          {/* IcsExportButton */}
          <div className="max-lg:order-7">
            <IcsExportButton event={event} />
          </div>

          {/* Stats publiques (review #90) — visible pour tous, pas seulement l'organisateur */}
          <div className="max-lg:order-8">
            <EventStatsPanel
              viewCount={event.viewCount}
              interestedCount={event.interestedCount}
              attendingCount={event.attendingCount}
            />
          </div>

          {/* Équipe organisatrice (SCRUM-137) — créateur + co-organisateurs ACCEPTED */}
          <div className="max-lg:order-8">
            <EventOrganizerTeam
              eventId={event.id}
              creatorId={event.creatorId}
              creatorUsername={organizer?.username ?? event.creatorId}
              creatorDisplayName={organizer?.displayName ?? null}
              creatorAvatarUrl={organizer?.avatarUrl ?? null}
            />
          </div>

          {/* Actions organisateur */}
          {isOrganizer && (
            <div className="flex flex-col gap-2 max-lg:order-9">
              {event.status !== 'CANCELLED' && (
                <>
                  <Link
                    to={`/events/${event.id}/edit`}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
                  >
                    <Pencil className="w-4 h-4 shrink-0" />
                    Modifier l'événement
                  </Link>
                  <DuplicateButton eventId={event.id} />
                  <button
                    type="button"
                    onClick={() => setShowCancelConfirm(true)}
                    disabled={cancelling}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-orange-500/40 text-orange-400 bg-transparent text-sm font-semibold cursor-pointer hover:bg-orange-500/10 transition-colors disabled:opacity-50"
                  >
                    <Ban className="w-4 h-4 shrink-0" />
                    Annuler l'événement
                  </button>
                </>
              )}
              {event.status === 'CANCELLED' && (
                <>
                  <button
                    type="button"
                    onClick={handleRestore}
                    disabled={restoring}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold cursor-pointer bg-transparent hover:border-foreground/30 transition-colors disabled:opacity-50"
                  >
                    <Undo2 className="w-4 h-4 shrink-0" />
                    Remettre en brouillon
                  </button>
                  {/* Delete stays creator-only — `EventService.delete`
                      explicitly forbids co-organizers (cf. line 428). */}
                  {isCreator && (
                    <button
                      type="button"
                      onClick={() => setShowConfirm(true)}
                      className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-error/10 border border-error/30 text-error rounded-2xl text-sm font-semibold cursor-pointer hover:bg-error/20 transition-colors"
                    >
                      <Trash2 className="w-4 h-4 shrink-0" />
                      Supprimer l'événement
                    </button>
                  )}
                </>
              )}
            </div>
          )}

          {/* Lien statistiques organisateur — S6. Visible to both the creator
              and accepted co-organizers (same authorisation set as the edit /
              cancel / restore actions ; cf. EventStatsService). */}
          {isOrganizer && (
            <div className="max-lg:order-10">
              <Link
                to={`/events/${event.id}/stats`}
                className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
              >
                <BarChart2 className="w-4 h-4 shrink-0" />
                Voir les statistiques
              </Link>
            </div>
          )}

        </div>

      </div>

      <div className="mt-10">
        <CommentSection
          eventId={event.id}
          eventCreatorId={event.creatorId}
          eventStatus={event.status}
        />
      </div>

      {bannerWarning && <InfoMessage type="error" message={bannerWarning} />}

      {showConfirm && (
        <ConfirmDialog
          title="Supprimer l'événement ?"
          message={<>Cette action supprimera définitivement l'événement <strong className="text-foreground">"{event.title}"</strong>. Elle est irréversible.</>}
          confirmLabel={deleting ? 'Suppression...' : 'Confirmer'}
          pending={deleting}
          onConfirm={handleDelete}
          onClose={() => setShowConfirm(false)}
        />
      )}

      {showCancelConfirm && (
        <ConfirmDialog
          title="Annuler l'événement ?"
          message={<>Cette action annulera l'événement <strong className="text-foreground">"{event.title}"</strong>. Vous pourrez le remettre en brouillon depuis l'onglet Annulés.</>}
          confirmLabel={cancelling ? '…' : 'Confirmer'}
          pending={cancelling}
          onConfirm={handleCancelEvent}
          onClose={() => setShowCancelConfirm(false)}
        />
      )}

      {reportHook.isOpen && (
        <ReportModal
          onClose={reportHook.close}
          onSubmit={reportHook.submit}
          submitting={reportHook.submitting}
        />
      )}

    </SectionWrapper>
  )
}
