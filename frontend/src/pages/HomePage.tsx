import { Link } from 'react-router-dom'
import Avatar from '../components/Avatar'
import EventCard from '../components/EventCard'
import { useAuth } from '../hooks/useAuth'
import { useEvents } from '../hooks/useEvents'

function HomePage() {
  const { user } = useAuth()
  const { events, loading, error, hasMore, loadMore } = useEvents()

  const firstName = user?.displayName ? user.displayName.split(' ')[0] : ''

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div style={{
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: 16,
        padding: '1.75rem 2rem',
        display: 'flex',
        alignItems: 'center',
        gap: '1.25rem',
        transition: 'background 0.3s, border-color 0.3s',
      }}>
        <Avatar avatarUrl={user?.avatarUrl} displayName={user?.displayName} size={56} />
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.125rem', color: 'var(--text-primary)', transition: 'color 0.3s' }}>
            {'Bienvenue' + (firstName ? ', ' + firstName : '') + ' !'}
          </div>
          <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginTop: 3, transition: 'color 0.3s' }}>
            Découvrez les événements du campus et rejoignez la communauté UNIGE.
          </div>
        </div>
        <div style={{
          marginLeft: 'auto',
          display: 'flex',
          gap: '0.75rem',
          alignItems: 'center',
          flexWrap: 'wrap',
          justifyContent: 'flex-end',
        }}>
          <Link
            to='/events/new'
            style={{
              padding: '0.5rem 1rem',
              background: 'var(--btn-secondary-bg)',
              color: 'var(--text-secondary)',
              border: '1px solid var(--input-border)',
              borderRadius: 8,
              textDecoration: 'none',
              fontSize: '0.875rem',
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            Créer un événement
          </Link>
          <Link
            to='/profile/me'
            style={{
              padding: '0.5rem 1rem',
              background: '#CF0063',
              color: 'white',
              borderRadius: 8,
              textDecoration: 'none',
              fontSize: '0.875rem',
              fontWeight: 600,
              flexShrink: 0,
            }}
          >
            Mon profil
          </Link>
        </div>
      </div>

      <div style={{
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border-subtle)',
        borderRadius: 16,
        padding: '1.5rem 2rem',
        transition: 'background 0.3s, border-color 0.3s',
      }}>
        <div style={{
          fontSize: '0.8rem',
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: 'var(--text-muted)',
          marginBottom: '1rem',
          transition: 'color 0.3s',
        }}>
          Événements à venir
        </div>

        {loading && events.length === 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem 0' }}>
            <div className='spinner' />
          </div>
        )}

        {error && (
          <div style={{
            background: 'var(--card-bg)',
            border: '1px solid var(--card-border)',
            borderRadius: 12,
            padding: '2rem',
            textAlign: 'center',
            color: '#dc2626',
          }}>
            {error}
          </div>
        )}

        {!loading && !error && events.length === 0 && (
          <div style={{
            background: 'var(--card-bg)',
            border: '1px solid var(--card-border)',
            borderRadius: 12,
            padding: '2rem',
            textAlign: 'center',
            color: 'var(--text-muted)',
            fontSize: '0.9375rem',
          }}>
            Aucun événement publié pour le moment.
          </div>
        )}

        {events.length > 0 && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: '1rem',
          }}>
            {events.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        )}

        {!error && events.length > 0 && (hasMore || loading) && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: '1.5rem' }}>
            <button
              type='button'
              onClick={loadMore}
              disabled={loading}
              style={{
                padding: '0.65rem 2rem',
                background: loading ? 'var(--card-bg)' : '#CF0063',
                color: loading ? 'var(--text-muted)' : 'white',
                border: '1px solid var(--card-border)',
                borderRadius: 8,
                fontWeight: 600,
                fontSize: '0.9rem',
                cursor: loading ? 'not-allowed' : 'pointer',
                transition: 'background 0.15s',
              }}
            >
              {loading ? 'Chargement…' : 'Charger plus'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default HomePage
