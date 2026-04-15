import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent, useFavorite } from '@/hooks'
import { useToast } from '@/hooks/useToast'
import { getUserById } from '@/services/userService'
import { cancelEvent, deleteEvent, restoreEvent } from '@/services/eventApi'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES } from '@/types/event'
import { formatEventDateTime } from '@/utils/dateTime'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import AttendanceButtons from '@/components/event/AttendanceButtons'
import IcsExportButton from '@/components/event/IcsExportButton'
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
        <div className="h-[104px] rounded-2xl" />
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

interface ComingSoonBlockProps {
  icon: LucideIcon
  label: string
  sprint: string
  children?: React.ReactNode
}

const comingSoonVariants = {
  container: 'rounded-2xl border border-dashed border-border/40 bg-foreground/[0.018] px-4 py-3',
  header:    'flex items-center justify-between gap-3',
  iconLabel: 'flex items-center gap-2 text-foreground/30',
  icon:      'w-4 h-4 shrink-0',
  label:     'text-sm',
  badge:     'text-[10px] font-semibold tracking-widest uppercase text-foreground/20 bg-foreground/5 px-2 py-0.5 rounded-full border border-border/30 shrink-0',
  body:      'mt-3 pointer-events-none select-none opacity-30',
} as const

function ComingSoonBlock({ icon: Icon, label, sprint, children }: Readonly<ComingSoonBlockProps>) {
  return (
    <div className={comingSoonVariants.container}>
      <div className={comingSoonVariants.header}>
        <div className={comingSoonVariants.iconLabel}>
          <Icon className={comingSoonVariants.icon} />
          <span className={comingSoonVariants.label}>{label}</span>
        </div>
        <span className={comingSoonVariants.badge}>{sprint}</span>
      </div>
      {children && (
        <div className={comingSoonVariants.body}>
          {children}
        </div>
      )}
    </div>
  )
}

function FavoriteTextButton({ eventId }: Readonly<{ eventId: number }>) {
  const { favorited, loading, toggle } = useFavorite(eventId)
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

// ─── Page principale ───────────────────────────────────────────────────────────

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const toast = useToast()
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null
  const { event, loading, error } = useEvent(eventId)
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const [deleting, setDeleting] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
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
    if (!event) { setOrganizer(null); return }
    let active = true
    getUserById(event.creatorId)
      .then((o) => { if (active) setOrganizer(o) })
      .catch(() => { if (active) setOrganizer(null) })
    return () => { active = false }
  }, [event])

  if (eventId === null) return <InfoMessage type='error' message="Identifiant d'événement invalide." />

  if (loading) return (
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

  if (error) return <InfoMessage type='error' message={error} />
  if (!event) return <InfoMessage type='error' message="Événement introuvable." />

  const isOrganizer = user !== null && user.id === event.creatorId
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
          <div
            className="relative rounded-3xl overflow-hidden h-72 lg:h-80"
            style={{
              background: event.bannerUrl
                ? `url(${event.bannerUrl}) center/cover`
                : `linear-gradient(135deg, ${category.color}55, ${category.color}cc)`,
            }}
          >
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
          </div>

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

          {/* Shells champs additionnels — S5 */}
          <div className="flex flex-col gap-3">

            <ComingSoonBlock icon={Globe} label="Site web de l'événement" sprint="S5">
              <div className="flex items-center gap-2">
                <Globe className="w-4 h-4 text-foreground/30 shrink-0" />
                <span className="text-xs text-foreground/20 truncate">https://unige.ch/evenement…</span>
              </div>
            </ComingSoonBlock>

            <ComingSoonBlock icon={Mail} label="Email de contact" sprint="S5">
              <div className="flex items-center gap-2">
                <Mail className="w-4 h-4 text-foreground/30 shrink-0" />
                <span className="text-xs text-foreground/20">contact@unige.ch</span>
              </div>
            </ComingSoonBlock>

            <ComingSoonBlock icon={CalendarClock} label="Date limite d'inscription" sprint="S5">
              <span className="text-xs text-foreground/20">jj/mm/aaaa à HH:MM</span>
            </ComingSoonBlock>

            <ComingSoonBlock icon={Tag} label="Mots-clés" sprint="S5">
              <div className="flex flex-wrap gap-1.5">
                {(['conférence', 'réseau', 'emploi'] as const).map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center px-2 py-0.5 rounded-lg text-xs bg-foreground/5 border border-border/30 text-foreground/20"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            </ComingSoonBlock>

          </div>

        </div>

        {/* Sidebar — order-1 sur mobile (remonte au-dessus) */}
        <div className="flex flex-col gap-4 lg:sticky lg:top-6 max-lg:order-1">

          {/* Card infos clés */}
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border flex flex-col gap-4">

            <div className="flex flex-col gap-3">
              <InfoRow icon={Calendar} color={category.color}>
                <span>
                  {formatEventDateTime(event.startDate)}
                  <span className="text-foreground/30 mx-1.5">→</span>
                  {formatEventDateTime(event.endDate)}
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
                <UserAvatar user={organizer} size='sm' className="shrink-0" />
                <div className="flex flex-col min-w-0">
                  <span className="text-xs text-foreground/40">Organisé par</span>
                  <span className="text-sm font-semibold text-foreground truncate">
                    {organizer.displayName ?? organizer.email}
                  </span>
                </div>
              </Link>
            )}

            {/* Shell places disponibles — S5 (SCRUM-130) */}
            <div className="border-t border-border" />
            <ComingSoonBlock icon={Users} label="Places disponibles" sprint="S5">
              <div className="flex flex-wrap gap-2 mt-1">
                <span className={`px-2.5 py-1 rounded-lg text-xs font-semibold border ${capacityBadgeVariants.available}`}>
                  8 places disponibles
                </span>
                <span className="px-2.5 py-1 rounded-lg text-xs font-semibold border bg-foreground/5 border-border/30 text-foreground/20">
                  2 en liste d'attente
                </span>
              </div>
            </ComingSoonBlock>

          </div>

          {/* Card AttendanceButtons + Favoris + Participants */}
          <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-5 py-4 border border-border flex flex-col gap-4">

            {/* Favoris + partager */}
            <div className="grid grid-cols-2 gap-3">
              <FavoriteTextButton eventId={event.id} />

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
            />

            <div className="border-t border-border" />

            {/* Shell liste des participants — S6 */}
            <ComingSoonBlock icon={Users} label="Liste des participants" sprint="S6">
              <div className="flex items-center gap-3 mt-1">
                <div className="flex -space-x-2">
                  {[1, 2, 3].map((i) => (
                    <div
                      key={i}
                      className="w-8 h-8 rounded-full bg-foreground/10 border-2 border-background"
                    />
                  ))}
                </div>
                <span className="text-xs text-foreground/20">12 participants · 4 intéressés</span>
              </div>
            </ComingSoonBlock>

          </div>

          {/* IcsExportButton */}
          <IcsExportButton event={event} />

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
                    onClick={handleCancelEvent}
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

          {/* Shell statistiques organisateur — S6 (SCRUM-92) */}
          {isOrganizer && (
            <ComingSoonBlock icon={BarChart2} label="Statistiques de participation" sprint="S6">
              <div className="grid grid-cols-3 gap-2 mt-1">
                {[
                  { label: 'Vues', value: '—' },
                  { label: 'Inscrits', value: '—' },
                  { label: 'Intéressés', value: '—' },
                ].map(({ label, value }) => (
                  <div key={label} className="flex flex-col items-center rounded-xl border border-border/30 bg-foreground/5 py-2">
                    <span className="text-sm font-bold text-foreground/20">{value}</span>
                    <span className="text-[10px] text-foreground/20 mt-0.5">{label}</span>
                  </div>
                ))}
              </div>
            </ComingSoonBlock>
          )}

        </div>

      </div>

      {bannerWarning && <InfoMessage type="error" message={bannerWarning} />}

      {/* Modale confirmation suppression — inchangée */}
      {showConfirm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">
            <h2 className="text-lg font-bold text-foreground mb-2">Supprimer l'événement ?</h2>
            <p className="text-sm text-foreground/50 mb-6">
              Cette action annulera l'événement <strong className="text-foreground">"{event.title}"</strong>. Elle est irréversible.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                type="button"
                onClick={() => setShowConfirm(false)}
                disabled={deleting}
                className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
              >
                {deleting ? 'Suppression...' : 'Confirmer'}
              </button>
            </div>
          </div>
        </div>
      )}

    </SectionWrapper>
  )
}
