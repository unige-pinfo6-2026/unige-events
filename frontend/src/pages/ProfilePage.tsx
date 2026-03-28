import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { getUserById } from '../services/userService'
import Avatar from '../components/Avatar'
import type { User } from '../types'
import './ProfilePage.css'

const FACULTY_LABELS: Record<string, string> = {
  SCIENCES: 'Sciences',
  LETTRES: 'Lettres',
  DROIT: 'Droit',
  MEDECINE: 'Médecine',
  SES: 'Sciences économiques et sociales',
  PSYCHOLOGIE: 'Psychologie',
  THEOLOGIE: 'Théologie',
  FTI: 'Traduction et interprétation',
  GSI: 'Gouvernance et sciences sociales',
}

const STUDY_LEVEL_LABELS: Record<string, string> = {
  BACHELOR: 'Bachelor',
  MASTER: 'Master',
  DOCTORAT: 'Doctorat',
  POST_DOC: 'Post-doctorat',
  STAFF: 'Personnel',
}

function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const [profile, setProfile] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const isOwnProfile =
    id === 'me' || (currentUser !== null && id === currentUser.auth0Id)

  useEffect(() => {
    if (!id) return
    if (authLoading) return

    setLoading(true)
    setError(null)

    if (isOwnProfile) {
      if (currentUser) {
        setProfile(currentUser)
        setLoading(false)
      } else {
        setError('Impossible de charger le profil.')
        setLoading(false)
      }
    } else {
      getUserById(id)
        .then((data) => {
          if (data === null) {
            setError('Profil introuvable.')
          } else {
            setProfile(data)
          }
        })
        .catch(() => setError('Impossible de charger le profil.'))
        .finally(() => setLoading(false))
    }
  }, [id, isOwnProfile, currentUser, authLoading])

  if (loading) {
    return (
      <div className="profile-loading">
        <div className="spinner" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="profile-page">
        <div className="profile-card" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <p style={{ color: '#dc2626', marginBottom: '1rem' }}>{error}</p>
          <a href="/home" style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Retour à l&apos;accueil
          </a>
        </div>
      </div>
    )
  }

  if (!profile) return null

  if (!isOwnProfile && !profile.profilePublic) {
    return (
      <div className="profile-private">
        <div className="profile-private-card">
          <span className="profile-private-icon">🔒</span>
          <h2>Ce profil est privé</h2>
          <p>Cet utilisateur a choisi de ne pas rendre son profil public.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="profile-page">
      <div className="profile-card">
        <div className="profile-header">
          <div className="profile-avatar-wrapper">
            <Avatar
              avatarUrl={profile.avatarUrl}
              displayName={profile.displayName}
              size={80}
              style={profile.avatarUrl ? { border: '3px solid var(--divider)' } : undefined}
            />
          </div>

          <div className="profile-header-info">
            <div className="profile-name-row">
              <h1 className="profile-name">{profile.displayName}</h1>
              <span
                className={`profile-visibility-badge ${profile.profilePublic ? 'badge--public' : 'badge--private'}`}
              >
                {profile.profilePublic ? 'Public' : 'Privé'}
              </span>
            </div>
            <p className="profile-email">{profile.email}</p>

            {(profile.faculty || profile.studyLevel) && (
              <div className="profile-meta">
                {profile.faculty && (
                  <span className="profile-meta-item">
                    🏛 {FACULTY_LABELS[profile.faculty] ?? profile.faculty}
                  </span>
                )}
                {profile.studyLevel && (
                  <span className="profile-meta-item">
                    📚 {STUDY_LEVEL_LABELS[profile.studyLevel] ?? profile.studyLevel}
                  </span>
                )}
              </div>
            )}
          </div>

          {isOwnProfile && (
            <Link to="/profile/me/edit" className="profile-edit-btn">
              Modifier mon profil
            </Link>
          )}
        </div>

        {profile.bio && (
          <section className="profile-section">
            <h2 className="profile-section-title">À propos</h2>
            <p className="profile-bio">{profile.bio}</p>
          </section>
        )}

        {profile.interests && profile.interests.length > 0 && (
          <section className="profile-section">
            <h2 className="profile-section-title">Centres d&apos;intérêt</h2>
            <div className="profile-interests">
              {profile.interests.map((interest) => (
                <span key={interest} className="interest-tag">
                  {interest}
                </span>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  )
}

export default ProfilePage
