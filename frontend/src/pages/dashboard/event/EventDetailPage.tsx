import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth, useEvent } from '@/hooks'
import { getUserById } from '@/services/userService'
import { deleteEvent } from '@/services/eventApi'
import Avatar from '@/components/user/Avatar'
import type { User } from '@/types/user'
import { EVENT_CATEGORIES } from '@/types/event'

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
    <div className="flex justify-center items-center min-h-[60vh]">
      <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl p-8 text-center max-w-sm">
        <p className="text-red-600 mb-4">{message}</p>
        <Link to="/dashboard" className="text-[#CF0063] font-semibold">← Retour à l'accueil</Link>
      </div>
    </div>
  )
}

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const parsedId = id !== undefined ? Number(id) : Number.NaN
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
      <div className="flex justify-center items-center min-h-[60vh]">
        <div className="spinner" />
      </div>
    )
  }
  if (error) return <CenteredMessage message={error} />
  if (!event) return <CenteredMessage message="Événement introuvable." />

  const currentEvent = event
  const isOrganizer = user !== null && user.id === currentEvent.creatorId
  const category = EVENT_CATEGORIES[currentEvent.category]

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteEvent(currentEvent.id)
      navigate('/home')
    } catch {
      setDeleting(false)
      setShowConfirm(false)
    }
  }

  return (
    <div className="max-w-[760px] mx-auto flex flex-col gap-5">

      {/* Banner */}
      <div
        className="h-[280px] rounded-2xl overflow-hidden flex items-end p-5"
        style={{
          background: currentEvent.bannerUrl
            ? `url(${currentEvent.bannerUrl}) center/cover`
            : `linear-gradient(135deg, ${category.color}33, ${category.color}66)`,
        }}
      >
        <span
          className="text-white text-[0.8125rem] font-bold px-3 py-1 rounded-full"
          style={{ background: category.color }}
        >
          {category.name}
        </span>
      </div>

      {/* Main card */}
      <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl px-8 py-7">

        {/* Title + actions */}
        <div className="flex items-start gap-4 mb-5">
          <h1 className="flex-1 text-2xl font-extrabold text-[var(--text-primary)] leading-snug m-0">
            {currentEvent.title}
          </h1>
          {isOrganizer && (
            <div className="flex gap-2 flex-shrink-0">
              <Link
                to={`/events/${currentEvent.id}/edit`}
                className="px-4 py-1.5 bg-[var(--card-bg)] border border-[var(--card-border)] rounded-lg text-[var(--text-primary)] text-sm font-semibold no-underline hover:bg-[var(--btn-secondary-hover)] transition-colors"
              >
                Modifier
              </Link>
              <button
                type="button"
                onClick={() => setShowConfirm(true)}
                className="px-4 py-1.5 bg-red-600 text-white rounded-lg text-sm font-semibold cursor-pointer border-none hover:opacity-90 transition-opacity"
              >
                Supprimer
              </button>
            </div>
          )}
        </div>

        {/* Meta */}
        <div className="flex flex-col gap-2.5 mb-6 text-[var(--text-secondary)] text-[0.9375rem]">
          <div className="flex items-center gap-2">
            <span>📅</span>
            <span>{formatDate(currentEvent.startDate)} → {formatDate(currentEvent.endDate)}</span>
          </div>
          <div className="flex items-center gap-2">
            <span>📍</span>
            <span>{currentEvent.location}</span>
          </div>
          {currentEvent.capacity !== undefined && (
            <div className="flex items-center gap-2">
              <span>👥</span>
              <span>{currentEvent.capacity} places disponibles</span>
            </div>
          )}
          {organizer && (
            <div className="flex items-center gap-2">
              <Avatar user={organizer} size={22} />
              <span>
                Organisé par{' '}
                <strong className="text-[var(--text-primary)]">
                  {organizer.displayName ?? organizer.email}
                </strong>
              </span>
            </div>
          )}
        </div>

        {/* Description */}
        {currentEvent.description && (
          <>
            <hr className="border-none border-t border-[var(--divider)] mb-5" />
            <p className="m-0 text-[var(--text-primary)] leading-[1.7] text-[0.9375rem] whitespace-pre-wrap">
              {currentEvent.description}
            </p>
          </>
        )}
      </div>

      <Link to="/dashboard" className="text-[#CF0063] font-semibold text-sm">
        ← Retour à l'accueil
      </Link>

      {/* Confirm modal */}
      {showConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[999]">
          <div className="bg-[var(--card-bg)] rounded-2xl p-8 max-w-sm w-[90%] shadow-[0_20px_60px_rgba(0,0,0,0.3)]">
            <h2 className="text-[1.125rem] font-semibold text-[var(--text-primary)] mb-3">
              Supprimer l'événement ?
            </h2>
            <p className="text-[var(--text-secondary)] text-[0.9rem] mb-6">
              Cette action annulera l'événement <strong>"{currentEvent.title}"</strong>. Elle est irréversible.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                type="button"
                onClick={() => setShowConfirm(false)}
                disabled={deleting}
                className="px-4 py-2.5 rounded-lg border border-[var(--card-border)] bg-[var(--card-bg)] text-[var(--text-primary)] disabled:opacity-50 disabled:cursor-not-allowed hover:bg-[var(--btn-secondary-hover)] transition-colors"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="px-4 py-2.5 rounded-lg border-none bg-red-600 text-white font-semibold disabled:opacity-50 disabled:cursor-not-allowed hover:opacity-90 transition-opacity"
              >
                {deleting ? 'Suppression…' : 'Confirmer'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}