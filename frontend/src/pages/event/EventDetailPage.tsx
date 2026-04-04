import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent } from '@/hooks'
import { getUserById } from '@/services/userService'
import { deleteEvent } from '@/services/eventApi'
import UserAvatar from '@/components/user/UserAvatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES } from '@/types/event'
import { Calendar, MapPin, Users, User as UserIcon, ArrowLeft } from 'lucide-react'

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('fr-CH', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function CenteredMessage({ message }: Readonly<{ message: string }>) {
  return (
    <div className="flex justify-center items-center min-h-60">
      <div className="bg-background border border-border rounded-3xl p-8 text-center max-w-sm">
        <p className="text-foreground/50 mb-4">{message}</p>
        <Link to="/" className="text-accent font-semibold text-sm">Retour à l'accueil</Link>
      </div>
    </div>
  )
}

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null
  const { event, loading, error } = useEvent(eventId)
  const [deleting, setDeleting] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [organizer, setOrganizer] = useState<User | null>(null)

  useEffect(() => {
    if (!event) { setOrganizer(null); return }
    let active = true
    getUserById(event.creatorId)
      .then((o) => { if (active) setOrganizer(o) })
      .catch(() => { if (active) setOrganizer(null) })
    return () => { active = false }
  }, [event])

  if (eventId === null) return <CenteredMessage message="Identifiant d'événement invalide." />

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-60">
        <div className="w-8 h-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
      </div>
    )
  }

  if (error) return <CenteredMessage message={error} />
  if (!event) return <CenteredMessage message="Événement introuvable." />

  const isOrganizer = user !== null && user.id === event.creatorId
  const category = EVENT_CATEGORIES[event.category]

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteEvent(event.id)
      navigate('/')
    } catch {
      setDeleting(false)
      setShowConfirm(false)
    }
  }

  return (
    <div className="max-w-3xl mx-auto flex flex-col gap-5">

      {/* Banner */}
      <div
        className="relative h-72 rounded-3xl overflow-hidden"
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
          <h1 className="text-white text-2xl font-extrabold leading-snug drop-shadow-sm">
            {event.title}
          </h1>
        </div>
      </div>

      {/* Main card */}
      <div className="bg-background border border-border rounded-3xl p-7 flex flex-col gap-6">

        {/* Organizer actions row */}
        {isOrganizer && (
          <div className="flex gap-2 justify-end">
            <Link
              to={`/events/${event.id}/edit`}
              className="px-4 py-2 rounded-xl border border-border text-foreground text-sm font-semibold no-underline hover:border-foreground/30 transition-colors"
            >
              Modifier
            </Link>
            <button
              type="button"
              onClick={() => setShowConfirm(true)}
              className="px-4 py-2 bg-red-500/10 border border-red-400/30 text-red-400 rounded-xl text-sm font-semibold cursor-pointer hover:bg-red-500/20 transition-colors"
            >
              Supprimer
            </button>
          </div>
        )}

        {/* Meta */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3 text-sm text-foreground/60">
            <Calendar className="w-4 h-4 shrink-0" style={{ color: category.color }} />
            <span>
              {formatDate(event.startDate)}
              <span className="text-foreground/30 mx-2">→</span>
              {formatDate(event.endDate)}
            </span>
          </div>
          <div className="flex items-center gap-3 text-sm text-foreground/60">
            <MapPin className="w-4 h-4 shrink-0" style={{ color: category.color }} />
            <span>{event.location}</span>
          </div>
          {event.capacity !== undefined && (
            <div className="flex items-center gap-3 text-sm text-foreground/60">
              <Users className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span>{event.capacity} places disponibles</span>
            </div>
          )}
          {organizer && (
            <div className="flex items-center gap-3 text-sm text-foreground/60">
              <UserIcon className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <div className="flex items-center gap-2">
                <UserAvatar user={organizer} size={20} />
                <span>
                  Organisé par{' '}
                  <strong className="text-foreground font-semibold">
                    {organizer.displayName ?? organizer.email}
                  </strong>
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Description */}
        {event.description && (
          <>
            <div className="border-t border-border" />
            <p className="text-foreground/70 leading-relaxed whitespace-pre-wrap text-sm">
              {event.description}
            </p>
          </>
        )}
      </div>

      <Link to="/" className="flex items-center gap-1.5 text-sm text-foreground/40 hover:text-foreground/60 transition-colors no-underline w-fit">
        <ArrowLeft className="w-4 h-4" />
        Retour à l'accueil
      </Link>

      {/* Confirm delete modal */}
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
                className="px-4 py-2.5 rounded-xl bg-red-500 text-white text-sm font-semibold disabled:opacity-50 hover:bg-red-600 transition-colors cursor-pointer border-0"
              >
                {deleting ? 'Suppression...' : 'Confirmer'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
