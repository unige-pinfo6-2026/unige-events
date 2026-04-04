import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { getUserById } from '@/services/userService'
import Avatar from '@/components/user/Avatar'
import { STUDY_LEVELS, type StudyLevel, type User } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'

export default function ProfilePage() {
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
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="spinner" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl text-center p-12 max-w-sm">
          <p className="text-red-600 mb-4">{error}</p>
          <a href="/home" className="text-[var(--text-secondary)] text-sm">
            Retour à l&apos;accueil
          </a>
        </div>
      </div>
    )
  }

  if (!profile) return null

  if (!isOwnProfile && !profile.profilePublic) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl text-center p-12 max-w-sm">
          <span className="text-4xl block mb-4">🔒</span>
          <h2 className="text-[var(--text-primary)] font-semibold text-lg mb-2">
            Ce profil est privé
          </h2>
          <p className="text-[var(--text-secondary)] text-sm">
            Cet utilisateur a choisi de ne pas rendre son profil public.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-[700px] mx-auto">
      <div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-2xl overflow-hidden">

        {/* Header */}
        <div className="flex items-start gap-6 p-8 border-b border-[var(--divider)] flex-wrap">
          <div className="flex-none">
            <Avatar user={profile} size={80} />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-3 flex-wrap mb-1">
              <h1 className="text-2xl font-bold text-[var(--text-primary)] m-0">
                {profile.displayName}
              </h1>
              <span
                className={`text-[0.7rem] font-semibold px-2.5 py-0.5 rounded-full uppercase tracking-wide ${
                  profile.profilePublic
                    ? 'bg-[var(--badge-public-bg)] text-[var(--badge-public-color)]'
                    : 'bg-[var(--badge-private-bg)] text-[var(--badge-private-color)]'
                }`}
              >
                {profile.profilePublic ? 'Public' : 'Privé'}
              </span>
            </div>

            <p className="text-sm text-[var(--text-secondary)] mb-3">
              {profile.email}
            </p>

            {(profile.faculty || profile.studyLevel) && (
              <div className="flex gap-4 flex-wrap">
                {profile.faculty && (
                  <span className="text-sm text-[var(--text-secondary)]">
                    🏛 {FACULTIES[profile.faculty as Faculty]?.name}
                  </span>
                )}
                {profile.studyLevel && (
                  <span className="text-sm text-[var(--text-secondary)]">
                    📚 {STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name}
                  </span>
                )}
              </div>
            )}
          </div>

          {isOwnProfile && (
            <Link
              to="/dashboard/profile/me/edit"
              className="flex-none self-start px-4 py-2 bg-[#CF0063] text-white rounded-lg text-sm font-semibold hover:opacity-85 transition-opacity"
            >
              Modifier mon profil
            </Link>
          )}
        </div>

        {/* Bio */}
        {profile.bio && (
          <section className="px-8 py-6 border-b border-[var(--divider)] last:border-b-0">
            <h2 className="text-[0.8rem] font-bold uppercase tracking-widest text-[var(--text-muted)] mb-3">
              À propos
            </h2>
            <p className="text-[var(--text-secondary)] leading-relaxed whitespace-pre-wrap">
              {profile.bio}
            </p>
          </section>
        )}

        {/* Interests */}
        {profile.interests && profile.interests.length > 0 && (
          <section className="px-8 py-6 last:border-b-0">
            <h2 className="text-[0.8rem] font-bold uppercase tracking-widest text-[var(--text-muted)] mb-3">
              Centres d&apos;intérêt
            </h2>
            <div className="flex flex-wrap gap-2">
              {profile.interests.map((interest) => (
                <span
                  key={interest}
                  className="bg-[var(--tag-bg)] text-[var(--tag-color)] border border-[var(--tag-border)] rounded-full px-3 py-1 text-[0.8125rem] font-medium"
                >
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