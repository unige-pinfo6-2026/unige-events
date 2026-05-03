import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent, useFavorite } from '@/hooks'
import { useAttendees } from '@/hooks/useAttendees'
import { useToast } from '@/hooks/useToast'
import { getUserById } from '@/services/userService'
import { cancelEvent, deleteEvent, restoreEvent } from '@/services/eventApi'
import { recordEventView } from '@/services/statsApi'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES } from '@/types/event'
import { formatEventDateTime } from '@/utils/dateTime'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import AttendanceButtons from '@/components/event/AttendanceButtons'
import AttendeesList from '@/components/attendees/AttendeesList'
import EventBanner from '@/components/event/EventBanner'
import IcsExportButton from '@/components/event/IcsExportButton'
import EventStatsPanel from '@/components/event/EventStatsPanel'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import type { LucideIcon } from 'lucide-react'
import { Ban, BarChart2, Calendar, CalendarClock, Globe, Mail, MapPin, Pencil, Share2, Star, Tag, Trash2, Undo2, Users } from 'lucide-react'

function EventDetailFixture() {
  return (
    <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">
      {/* Main column (order-2 on mobile) */}
      <div className="flex flex-col gap-5 max-lg:order-2">
        <div className="h-72 lg:h-80 rounded-3xl" />
        <div className="h-40 rounded-3xl" />
        <div className="flex flex-col gap-3">
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
        </div>
      </div>
      {/* Sidebar column (order-1 on mobile) */}
      <div className="flex flex-col gap-4 max-lg:order-1">
        <div className="h-72 rounded-3xl" />
        <div className="h-44 rounded-3xl" />
        <div className="h-[236px] rounded-3xl" />
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
  return { variant: 'available', label: `${availableSpots} places disponibles` }
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
  const { user } = useAuth()
  const toast = useToast()
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null
  const { event, isInitialLoad, isRefetching, error, refetch: refetchEvent } = useEvent(eventId)
  const isOrganizer = user !== null && event !== null && user.id === event.creatorId
  const attendeesHook = useAttendees(eventId ?? 0, { enabled: isOrganizer && eventId !== null })
  const refetchAttendees = attendeesHook.refetch
  const handleAttendanceSuccess = useCallback(async (): Promise<void> => {
    await Promise.all([
      refetchEvent(),
      isOrganizer ? Promise.resolve(refetchAttendees()) : Promise.resolve(),
    ])
  }, [refetchEvent, refetchAttendees, isOrganizer])
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
    if (!eventId || !user) return
    recordEventView(eventId).catch(() => {})
  }, [eventId, user])

  useEffect(() => {
    if (!event) { setOrganizer(null); return }
    let active = true
    getUserById(event.creatorId)
      .then((o) => { if (active) setOrganizer(o) })
      .catch(() => { if (active) setOrganizer(null) })
    return () => { active = false }
  }, [event])

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

      {/* Grille deux colonnes */}
      <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">

        {/* Colonne principale — order-2 sur mobile */}
        <div className="flex flex-col gap-5 max-lg:order-2">

          {/* Bannière */}
          <EventBanner event={event} className="rounded-3xl h-72 lg:h-80">
            <div className="absolute inset-0 bg-linear-to-t from-black/70 via-black/20 to-transparent" />
            <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: category.color }} />
            <span
              className="absolute top-4 left-4 text-white text-xs font-bold px-3 py-1.5 rounded-full tracking-wide backdrop-blur-sm"
              style={{ background: `${category.color}cc` }}
            >
              {category.name}
            </span>
            <div className="absolute bottom-0 left-0 right-0 p-6">
              <h1 className="text-white text-2xl lg:text-3xl font-extrabold leading-snug drop-shadow-sm">
                {event.title}
              </h1>
            </div>
          </EventBanner>

          {/* Card description */}
          {event.description && (
            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border">
              <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3">
                À propos
              </h2>
              <p className="text-foreground/70 leading-relaxed whitespace-pre-wrap text-sm">
                {event.description}
              </p>
            </div>
          )}

          <AttendeesList
            isOrganizer={isOrganizer}
            attendingCount={event.attendingCount}
            attendeesHook={attendeesHook}
          />

          {/* Champs additionnels (SCRUM-117) */}
          {(event.websiteUrl || event.contactEmail || event.registrationDeadline || (event.tags && event.tags.length > 0)) && (
            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border flex flex-col gap-4">
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
                          className="text-foreground hover:underline break-all"
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
                      className="text-foreground hover:underline break-all"
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

        {/* Sidebar — order-1 sur mobile (remonte au-dessus) */}
        <div className="flex flex-col gap-4 lg:sticky lg:top-6 max-lg:order-1">

          {/* Card infos clés */}
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border flex flex-col gap-4">

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
                  {event.capacity} places au total
                </InfoRow>
              )}
            </div>

            <div className="border-t border-border" />

            {/* Organisateur */}
            {organizer && (
              <Link
                to={`/profile/${organizer.id}`}
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
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-5 py-4 border border-border flex flex-col gap-4">

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
          <IcsExportButton event={event} />

          {/* Stats publiques (review #90) — visible pour tous, pas seulement l'organisateur */}
          <EventStatsPanel
            viewCount={event.viewCount}
            interestedCount={event.interestedCount}
            attendingCount={event.attendingCount}
          />

          {/* Actions organisateur */}
          {isOrganizer && (
            <div className="flex flex-col gap-2">
              {event.status !== 'CANCELLED' && (
                <>
                  <Link
                    to={`/events/${event.id}/edit`}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
                  >
                    <Pencil className="w-4 h-4 shrink-0" />
                    Modifier l'événement
                  </Link>
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
                  <button
                    type="button"
                    onClick={() => setShowConfirm(true)}
                    className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-error/10 border border-error/30 text-error rounded-2xl text-sm font-semibold cursor-pointer hover:bg-error/20 transition-colors"
                  >
                    <Trash2 className="w-4 h-4 shrink-0" />
                    Supprimer l'événement
                  </button>
                </>
              )}
            </div>
          )}

          {/* Lien statistiques organisateur — S6 */}
          {isOrganizer && (
            <Link
              to={`/events/${event.id}/stats`}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
            >
              <BarChart2 className="w-4 h-4 shrink-0" />
              Voir les statistiques
            </Link>
          )}

        </div>

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

    </SectionWrapper>
  )
}
