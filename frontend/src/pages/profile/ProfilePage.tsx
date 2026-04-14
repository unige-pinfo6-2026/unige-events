import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { getUserById } from '@/services/userService'
import UserAvatar from '@/components/user/UserAvatar'
import { STUDY_LEVELS, type StudyLevel, type User } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'
import { GraduationCap, Lock, Mail, type LucideIcon } from 'lucide-react'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { BlobsCta } from '@/components/utils/Blobs'
import CalendarSubscribeButton from '@/components/calendar/CalendarSubscribeButton'

function ProfileFixture() {
  return (
    <div>
      {/* Banner */}
      <div className="relative h-52 overflow-hidden bg-foreground/10" />

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">
        {/* Header: avatar + name + edit button */}
        <div className="relative -mt-14 flex flex-wrap items-end justify-between gap-4 mb-8">
          <div className="flex items-end gap-5">
            {/* Avatar 112px */}
            <div className="relative shrink-0 w-28 h-28 rounded-full bg-foreground/10 ring-4 ring-background" />
            <div className="pb-2 flex flex-col gap-2">
              <div className="h-9 w-52 rounded bg-foreground/10" />
              <div className="h-4 w-36 rounded bg-foreground/10" />
            </div>
          </div>
        </div>

        {/* Content grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* About card */}
          <div className="flex flex-col gap-6">
            <div className="bg-background border border-border rounded-3xl p-6">
              <div className="h-3 w-16 rounded bg-foreground/10 mb-5" />
              <div className="flex flex-col gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-5 h-5 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-52 rounded bg-foreground/10" />
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-5 h-5 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-40 rounded bg-foreground/10" />
                </div>
                <div className="h-12 w-32 rounded bg-foreground/10" />
              </div>
            </div>
          </div>

          {/* Calendar card */}
          <div className="bg-background border border-border rounded-3xl p-6 h-48" />
        </div>
      </div>
    </div>
  )
}

function AboutRow({ icon: Icon, children }: Readonly<{ icon: LucideIcon; children: React.ReactNode }>) {
  return (
    <div className="flex items-center gap-3 text-sm text-foreground/60">
      <Icon className="w-5 h-5 shrink-0 text-accent" />
      <span className="truncate">{children}</span>
    </div>
  )
}

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
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

  if (loading) return (
    <Skeleton
      name="profile"
      loading={true}
      animate="pulse"
      color={skeletonColor}
    ><ProfileFixture /></Skeleton>
  )
  if (error) return <InfoMessage type="error" message={error} />
  if (!profile) return null

  const studyLevelName = profile.studyLevel
    ? STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name
    : null
  const facultyEntry = profile.faculty ? FACULTIES[profile.faculty as Faculty] : null
  const facultyName = facultyEntry?.name ?? null
  const FacultyLogo = facultyEntry?.logo ?? null
  const profileSubtitle = [studyLevelName, facultyName].filter(Boolean).join(' · ')

  if (!isOwnProfile && !profile.profilePublic) {
    return (
      <div>
        <div className="relative h-52 overflow-hidden bg-linear-to-br from-foreground/5 via-foreground/3 to-foreground/5">
          <BlobsCta />
          <div className="absolute inset-0 bg-linear-to-t from-background/60 to-transparent" />
        </div>
        <div className="max-w-5xl mx-auto px-6 lg:px-8 -mt-10 flex justify-center pb-20">
          <div className="bg-linear-to-br from-background/90 to-background/60 backdrop-blur-xl border border-border rounded-3xl p-10 max-w-sm w-full flex flex-col items-center gap-4 text-center">
            <div className="w-16 h-16 rounded-2xl bg-foreground/5 border border-border flex items-center justify-center">
              <Lock className="w-7 h-7 text-foreground/30" />
            </div>
            <div>
              <h2 className="font-bold text-xl mb-1">Ce profil est privé</h2>
              <p className="text-foreground/40 text-sm leading-relaxed">
                Cet utilisateur a choisi de ne pas rendre son profil public.
              </p>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div>
      {/* Banner */}
      <div className="relative h-52 overflow-hidden bg-linear-to-br from-accent/20 via-pink-600/15 to-purple-600/20">
        <BlobsCta />
        <div className="absolute inset-0 bg-linear-to-t from-background/50 to-transparent" />
      </div>

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">

        {/* Header: avatar + name/subtitle + edit button */}
        <div className="relative z-10 -mt-14 flex flex-wrap items-end justify-between gap-4 mb-8">
          <div className="flex items-end gap-5">
            <div className="relative shrink-0">
              <div className="absolute inset-0 rounded-full bg-linear-to-br from-accent to-pink-600 blur-xl opacity-40 scale-110 pointer-events-none" />
              <UserAvatar user={profile} size={"profile"} className="relative ring-4 ring-background shadow-2xl" />
            </div>
            <div className="pb-2">
              <h1 className="text-3xl lg:text-4xl font-bold tracking-tight leading-tight">
                {profile.displayName}
              </h1>
              {profileSubtitle && (
                <p className="text-sm text-foreground/40 mt-1 font-medium">{profileSubtitle}</p>
              )}
            </div>
          </div>

          {isOwnProfile && (
            <Link
              to="/profile/me/edit"
              className="inline-flex items-center gap-2 self-end px-4 py-2 rounded-xl border-2 border-border text-sm font-semibold text-foreground no-underline hover:border-accent/50 hover:bg-foreground/5 transition-all shrink-0"
            >
              Modifier
            </Link>
          )}
        </div>

        {/* Content: about card (left) + calendar card (right on desktop) */}
        <div className={`grid grid-cols-1 gap-6 ${isOwnProfile ? 'lg:grid-cols-2' : ''}`}>

          {/* Left column: bio + about + interests */}
          <div className="flex flex-col gap-6">
            {profile.bio && (
              <p className="text-foreground/60 leading-relaxed whitespace-pre-wrap">
                {profile.bio}
              </p>
            )}

            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6">
              <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-5">À propos</h2>
              <div className="flex flex-col gap-4">
                <AboutRow icon={Mail}>{profile.email}</AboutRow>
                {studyLevelName && (
                  <AboutRow icon={GraduationCap}>{studyLevelName}</AboutRow>
                )}
                {FacultyLogo && (
                  <div className="pt-1" role="img" aria-label={facultyName ?? undefined}>
                    <FacultyLogo className="h-12 w-auto" />
                  </div>
                )}
              </div>

              {profile.interests && profile.interests.length > 0 && (
                <>
                  <div className="border-t border-border my-6" />
                  <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-4">Centres d&apos;intérêt</h2>
                  <div className="flex flex-wrap gap-2">
                    {profile.interests.map((interest) => (
                      <span
                        key={interest}
                        className="bg-accent/5 border border-accent/20 text-foreground/70 rounded-full px-3 py-1 text-sm font-medium capitalize hover:border-accent/50 hover:text-foreground transition-colors"
                      >
                        {interest}
                      </span>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Right column: calendar subscription (own profile only) */}
          {isOwnProfile && <CalendarSubscribeButton />}
        </div>

      </div>
    </div>
  )
}
