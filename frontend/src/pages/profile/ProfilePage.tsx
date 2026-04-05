import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { getUserById } from '@/services/userService'
import UserAvatar from '@/components/user/UserAvatar'
import { STUDY_LEVELS, type StudyLevel, type User } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'
import { GraduationCap, Building2, Lock, Pencil } from 'lucide-react'
import { ErrorMessage } from '@/components/utils/ErrorMessage'

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const [profile, setProfile] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const isOwnProfile = id === 'me' || (currentUser !== null && id === currentUser.auth0Id)

  useEffect(() => {
    if (!id || authLoading) return

    setLoading(true)
    setError(null)

    if (isOwnProfile) {
      if (currentUser) {
        setProfile(currentUser)
      } else {
        setError('Impossible de charger le profil.')
      }
      setLoading(false)
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
      <div className="flex items-center justify-center min-h-60">
        <div className="w-8 h-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
      </div>
    )
  }

  if (error) {
    return (
      <ErrorMessage message={error}/>
    )
  }

  if (!profile) return null

  if (!isOwnProfile && !profile.profilePublic) {
    return (
      <div className="flex items-center justify-center min-h-60">
        <div className="bg-background border border-border rounded-3xl text-center p-12 max-w-sm flex flex-col items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-foreground/5 flex items-center justify-center">
            <Lock className="w-6 h-6 text-foreground/30" />
          </div>
          <h2 className="font-bold text-foreground text-lg">Ce profil est privé</h2>
          <p className="text-foreground/40 text-sm">
            Cet utilisateur a choisi de ne pas rendre son profil public.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto flex flex-col gap-4">

      {/* Header card */}
      <div className="bg-background border border-border rounded-3xl p-7">
        <div className="flex items-start gap-5 flex-wrap">
          <UserAvatar user={profile} size={72} />

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2.5 flex-wrap mb-1">
              <h1 className="text-2xl font-bold text-foreground">{profile.displayName}</h1>
              <span className={[
                'text-[0.7rem] font-bold px-2.5 py-0.5 rounded-full uppercase tracking-wide',
                profile.profilePublic
                  ? 'bg-emerald-500/10 text-emerald-500'
                  : 'bg-foreground/5 text-foreground/40',
              ].join(' ')}>
                {profile.profilePublic ? 'Public' : 'Privé'}
              </span>
            </div>

            <p className="text-sm text-foreground/40 mb-3">{profile.email}</p>

            <div className="flex flex-wrap gap-3">
              {profile.faculty && (
                <div className="flex items-center gap-1.5 text-sm text-foreground/50">
                  <Building2 className="w-3.5 h-3.5" />
                  <span>{FACULTIES[profile.faculty as Faculty]?.name}</span>
                </div>
              )}
              {profile.studyLevel && (
                <div className="flex items-center gap-1.5 text-sm text-foreground/50">
                  <GraduationCap className="w-3.5 h-3.5" />
                  <span>{STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name}</span>
                </div>
              )}
            </div>
          </div>

          {isOwnProfile && (
            <Link
              to="/profile/me/edit"
              className="flex items-center gap-1.5 self-start px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground no-underline hover:border-accent/50 transition-colors"
            >
              <Pencil className="w-3.5 h-3.5" />
              Modifier
            </Link>
          )}
        </div>
      </div>

      {/* Bio */}
      {profile.bio && (
        <div className="bg-background border border-border rounded-3xl p-7">
          <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3">À propos</h2>
          <p className="text-foreground/70 leading-relaxed whitespace-pre-wrap text-sm">{profile.bio}</p>
        </div>
      )}

      {/* Interests */}
      {profile.interests && profile.interests.length > 0 && (
        <div className="bg-background border border-border rounded-3xl p-7">
          <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-4">Centres d&apos;intérêt</h2>
          <div className="flex flex-wrap gap-2">
            {profile.interests.map((interest) => (
              <span
                key={interest}
                className="bg-foreground/5 border border-border text-foreground/60 rounded-full px-3 py-1 text-sm font-medium"
              >
                {interest}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
