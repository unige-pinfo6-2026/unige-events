import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Avatar from '../components/Avatar'
import { useAuth } from '../hooks/useAuth'
import { useEvent } from '../hooks/useEvent'
import { deleteEvent } from '../services/eventApi'
import { getUserById } from '../services/userService'
import type { EventCategory, User } from '../types'
import { formatEventDateTime } from '../utils/dateTime'

const CATEGORY_LABELS: Record<EventCategory, string> = {
  ACADEMIC: 'Académique',
  SPORTS: 'Sports',
  CULTURAL: 'Culturel',
  SOCIAL: 'Social',
  CONFERENCE: 'Conférence',
  OTHER: 'Autre',
}

const CATEGORY_COLORS: Record<EventCategory, string> = {
  ACADEMIC: '#2563eb',
  SPORTS: '#16a34a',
  CULTURAL: '#9333ea',
  SOCIAL: '#ea580c',
  CONFERENCE: '#0891b2',
  OTHER: '#6b7280',
}

function CenteredMessage({ message }: Readonly<{ message: string }>) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
      <div style={{
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: 16,
        padding: '2rem',
        textAlign: 'center',
        maxWidth: 360,
      }}>
        <p style={{ color: '#dc2626', margin: '0 0 1rem' }}>{message}</p>
        <Link to='/home' style={{ color: '#CF0063', fontWeight: 600 }}>← Retour à l'accueil</Link>
      </div>
    </div>
  )
}

function EventDetailPage() {
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
    if (!event) {
      setOrganizer(null)
      return
    }

    let active = true

    getUserById(event.creatorId)
      .then((loadedOrganizer) => {
        if (active) {
          setOrganizer(loadedOrganizer)
        }
      })
      .catch(() => {
        if (active) {
          setOrganizer(null)
        }
      })

    return () => {
      active = false
    }
  }, [event])

  if (eventId === null) {
    return <CenteredMessage message="Identifiant d'événement invalide." />
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <div className='spinner' />
      </div>
    )
  }

  if (error) {
    return <CenteredMessage message={error} />
  }

  if (!event) {
    return <CenteredMessage message='Événement introuvable.' />
  }

  const currentEvent = event
  const isOrganizer = user !== null && user.id === currentEvent.creatorId
  const categoryColor = CATEGORY_COLORS[currentEvent.category]

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
    <div style={{ maxWidth: 760, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <div style={{
        height: 280,
        borderRadius: 16,
        overflow: 'hidden',
        background: currentEvent.bannerUrl
          ? 'url(' + currentEvent.bannerUrl + ') center/cover'
          : 'linear-gradient(135deg, ' + categoryColor + '33, ' + categoryColor + '66)',
        display: 'flex',
        alignItems: 'flex-end',
        padding: '1.25rem',
      }}>
        <span style={{
          background: categoryColor,
          color: 'white',
          fontSize: '0.8125rem',
          fontWeight: 700,
          padding: '0.3rem 0.8rem',
          borderRadius: 20,
        }}>
          {CATEGORY_LABELS[currentEvent.category]}
        </span>
      </div>

      <div style={{
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: 16,
        padding: '1.75rem 2rem',
        transition: 'background 0.3s, border-color 0.3s',
      }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.25rem' }}>
          <h1 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', flex: 1, lineHeight: 1.3 }}>
            {currentEvent.title}
          </h1>
          {isOrganizer && (
            <div style={{ display: 'flex', gap: '0.5rem', flexShrink: 0 }}>
              <Link
                to={'/events/' + currentEvent.id + '/edit'}
                style={{
                  padding: '0.45rem 1rem',
                  background: 'var(--card-bg)',
                  border: '1px solid var(--card-border)',
                  borderRadius: 8,
                  color: 'var(--text-primary)',
                  textDecoration: 'none',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  transition: 'background 0.15s',
                }}
              >
                Modifier
              </Link>
              <button
                type='button'
                onClick={() => setShowConfirm(true)}
                style={{
                  padding: '0.45rem 1rem',
                  background: '#dc2626',
                  border: 'none',
                  borderRadius: 8,
                  color: 'white',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Supprimer
              </button>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
            <span>📅</span>
            <span>{formatEventDateTime(currentEvent.startDate)} → {formatEventDateTime(currentEvent.endDate)}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
            <span>📍</span>
            <span>{currentEvent.location}</span>
          </div>
          {currentEvent.capacity !== undefined && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
              <span>👥</span>
              <span>{currentEvent.capacity} places disponibles</span>
            </div>
          )}
          {organizer && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
              <Avatar avatarUrl={organizer.avatarUrl} displayName={organizer.displayName} size={22} />
              <span>
                Organisé par <strong style={{ color: 'var(--text-primary)' }}>{organizer.displayName ?? organizer.email}</strong>
              </span>
            </div>
          )}
        </div>

        {currentEvent.description && (
          <>
            <hr style={{ border: 'none', borderTop: '1px solid var(--divider)', margin: '0 0 1.25rem' }} />
            <p
              style={{
                margin: 0,
                color: 'var(--text-primary)',
                lineHeight: 1.7,
                fontSize: '0.9375rem',
                whiteSpace: 'pre-wrap',
                overflowWrap: 'anywhere',
                wordBreak: 'break-word',
              }}
            >
              {currentEvent.description}
            </p>
          </>
        )}
      </div>

      <Link to='/home' style={{ color: '#CF0063', fontWeight: 600, fontSize: '0.875rem' }}>
        ← Retour à l'accueil
      </Link>

      {showConfirm && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 999,
        }}>
          <div style={{
            background: 'var(--card-bg)',
            borderRadius: 16,
            padding: '2rem',
            maxWidth: 380,
            width: '90%',
            boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          }}>
            <h2 style={{ margin: '0 0 0.75rem', fontSize: '1.125rem', color: 'var(--text-primary)' }}>
              Supprimer l'événement ?
            </h2>
            <p style={{ margin: '0 0 1.5rem', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
              Cette action annulera l'événement <strong>"{currentEvent.title}"</strong>. Elle est irréversible.
            </p>
            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
              <button
                type='button'
                onClick={() => setShowConfirm(false)}
                disabled={deleting}
                style={{
                  padding: '0.6rem 1rem',
                  borderRadius: 8,
                  border: '1px solid var(--card-border)',
                  background: 'var(--card-bg)',
                  color: 'var(--text-primary)',
                  cursor: deleting ? 'not-allowed' : 'pointer',
                }}
              >
                Annuler
              </button>
              <button
                type='button'
                onClick={handleDelete}
                disabled={deleting}
                style={{
                  padding: '0.6rem 1rem',
                  borderRadius: 8,
                  border: 'none',
                  background: '#dc2626',
                  color: 'white',
                  fontWeight: 600,
                  cursor: deleting ? 'not-allowed' : 'pointer',
                }}
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

export default EventDetailPage
