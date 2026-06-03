import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useOrganizerEvents } from '@/hooks/useOrganizerEvents'
import { useUserParticipations } from '@/hooks/useUserParticipations'
import { getUserById, getUserByUsername } from '@/services/userService'
import FollowButton from '@/components/user/FollowButton'
import ProfileHeader from '@/components/profile/ProfileHeader'
import ProfileStats from '@/components/profile/ProfileStats'
import ProfileEventsList from '@/components/profile/ProfileEventsList'
import ProfileParticipations from '@/components/profile/ProfileParticipations'
import ProfilePrivateState from '@/components/profile/ProfilePrivateState'
import ProfileTabs, { type ProfileTab } from '@/components/profile/ProfileTabs'
import { type User, type UserPublicResponse, STUDY_LEVELS, type StudyLevel } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'
import { CalendarDays, GraduationCap, LayoutGrid, Tags, Ticket, type LucideIcon } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import MyPublicationsPreview from '@/components/profile/MyPublicationsPreview'
import NotFoundPage from '@/pages/NotFoundPage'
import ErrorPage from '@/pages/ErrorPage'

/**
 * UUID v4 regex — used to detect legacy `/profile/<uuid>` URLs still in
 * external caches, emails, bookmarks, etc. They are redirected to
 * `/profile/<username>` (cf. SCRUM-169 spec Décision I — redirect permanent).
 */
const UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function ProfileFixture() {
  return (
    <div>
      <div className="relative h-52 overflow-hidden bg-foreground/10" />

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">
        <div className="relative -mt-14">
          {/* Top row : identity + actions (flex-wrap) */}
          <div className="flex flex-wrap items-start gap-x-8 gap-y-6">
            {/* Identity */}
            <div className="flex flex-col">
              <div className="w-28 h-28 rounded-full bg-foreground/10 ring-4 ring-background" />
              <div className="mt-4 flex flex-col gap-2">
                <div className="h-9 w-52 rounded bg-foreground/10" />
                <div className="h-4 w-36 rounded bg-foreground/10" />
                <div className="h-5 w-16 rounded-full bg-foreground/10" />
              </div>
            </div>

            {/* Actions */}
            <div className="flex flex-col gap-4 items-end shrink-0 ml-auto min-[521px]:pt-2 max-[520px]:w-full max-[520px]:ml-0">
              <div className="flex gap-3 max-[520px]:w-full max-[520px]:justify-center">
                <div className="h-24 w-32 rounded-2xl bg-foreground/10 max-[520px]:flex-1" />
                <div className="h-24 w-32 rounded-2xl bg-foreground/10 max-[520px]:flex-1" />
              </div>
              <div className="flex justify-end max-[520px]:absolute max-[520px]:right-0 max-[520px]:top-16 max-[520px]:z-20">
                <div className="h-10 w-28 rounded-xl bg-foreground/10" />
              </div>
            </div>
          </div>

          {/* Extras : full-width below */}
          <div className="mt-6 flex flex-col">
            <div className="flex flex-col gap-3">
              <div className="h-5 w-48 rounded bg-foreground/10" />
              <div className="h-5 w-40 rounded bg-foreground/10" />
            </div>
            <div className="mt-3 h-12 w-full max-w-md rounded bg-foreground/10" />
          </div>
        </div>

        {/* Tabs */}
        <div className="mt-10 border-t border-border flex justify-center gap-6 pt-4">
          <div className="h-5 w-40 rounded bg-foreground/10" />
          <div className="h-5 w-40 rounded bg-foreground/10" />
        </div>
        <div className="mt-6 bg-background border border-border rounded-3xl p-6 h-48" />
      </div>
    </div>
  )
}

/** Ligne « propriété » de la colonne gauche : icône + contenu (faculté, tags). */
function PropertyRow({ icon: Icon, children }: Readonly<{ icon: LucideIcon; children: React.ReactNode }>) {
  return (
    <div className="flex items-center gap-3 text-sm text-foreground/70">
      <Icon className="w-5 h-5 shrink-0 text-accent" />
      <div className="min-w-0 flex flex-wrap items-center gap-2">{children}</div>
    </div>
  )
}

interface PublicProfileViewProps {
  profile: UserPublicResponse
  isMeRoute: boolean
  /**
   * `true` when the viewer is logged in AND looking at someone else's UUID
   * profile (not their own UUID, not the /me route). Drives the
   * FollowButton render — SCRUM-110.
   */
  canFollow: boolean
  /**
   * Triggers a refetch of the parent useUserProfile so the FollowButton
   * mutation flips followStatus and bumps followerCount in place.
   */
  onProfileMutated?: () => void
  /**
   * Quand `true` (MeProfileView uniquement, pendant le background-fetch des
   * compteurs), les tuiles Abonnés/Abonnements sont remplacées par des
   * placeholders animés — évite le flash « 0 → vrai chiffre » sur /me.
   */
  statsLoading?: boolean
}

/**
 * Renders the full public profile (SCRUM-141 + SCRUM-169): banner, overlapping
 * avatar, displayName + faculté/niveau, bio, follower/following counters,
 * events organised, public participations. Reused for both
 * `/profile/<username>` and `/profile/me` (the `/me` route adds owner-only
 * widgets — edit button, calendar subscription, MyPublicationsPreview,
 * co-organizer invitations).
 */
function PublicProfileView({ profile, isMeRoute, canFollow, onProfileMutated, statsLoading = false }: Readonly<PublicProfileViewProps>) {
  const { events, loading: eventsLoading, error: eventsError } = useOrganizerEvents(profile.id)
  const {
    events: participations,
    loading: participationsLoading,
    error: participationsError,
  } = useUserParticipations(profile.id)

  const facultyName = profile.faculty ? FACULTIES[profile.faculty as Faculty]?.name ?? null : null
  const studyLevelName = profile.studyLevel ? STUDY_LEVELS[profile.studyLevel as StudyLevel].name : null
  const interests = profile.interests ?? []

  // Colonne droite : gros blocs Abonnés/Abonnements + bouton Suivre (rose) /
  // Modifier, alignés à droite. Les compteurs restent cliquables (pages de
  // liste SCRUM-142). Pour /me les compteurs sont peuplés par le background
  // fetch de MeProfileView ; `statsLoading=true` remplace les tuiles par des
  // placeholders animés le temps que ce fetch arrive.
  const headerStats = statsLoading ? (
    <div className="flex gap-3 w-full justify-center">
      <div className="h-24 w-32 rounded-2xl bg-foreground/10 animate-pulse flex-1" />
      <div className="h-24 w-32 rounded-2xl bg-foreground/10 animate-pulse flex-1" />
    </div>
  ) : (
    <ProfileStats
      followerCount={profile.followerCount}
      followingCount={profile.followingCount}
      linkUsername={profile.username}
    />
  )

  const headerAction = isMeRoute ? (
    <Link
      to="/profile/me/edit"
      className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border-2 border-border text-sm font-semibold text-foreground no-underline hover:border-accent/50 hover:bg-foreground/5 transition-all shrink-0"
    >
      Modifier
    </Link>
  ) : canFollow ? (
    <FollowButton
      targetId={profile.id}
      followStatus={profile.followStatus}
      onMutated={onProfileMutated}
    />
  ) : null

  // Extras de la colonne gauche, sous le bloc identité : propriétés
  // (Faculté + Tags, chacune préfixée d'une icône) puis bio longue.
  const leftExtras = (
    <>
      {(facultyName || studyLevelName || interests.length > 0) && (
        <div className="flex flex-col gap-3">
          {(studyLevelName || facultyName) && (
            <PropertyRow icon={GraduationCap}>
              {studyLevelName && <span>{studyLevelName}</span>}
              {studyLevelName && facultyName && <span className="text-foreground/30">·</span>}
              {facultyName && <span>{facultyName}</span>}
            </PropertyRow>
          )}
          {interests.length > 0 && (
            <PropertyRow icon={Tags}>
              {interests.map((interest) => (
                <span
                  key={interest}
                  className="bg-accent/5 border border-accent/20 text-foreground/70 rounded-full px-3 py-1 text-sm font-medium capitalize hover:border-accent/50 hover:text-foreground transition-colors"
                >
                  {interest}
                </span>
              ))}
            </PropertyRow>
          )}
        </div>
      )}

      {profile.bio && (
        <p className="mt-3 text-foreground/60 leading-relaxed whitespace-pre-wrap wrap-anywhere">
          {profile.bio}
        </p>
      )}
    </>
  )

  // Onglets switchables (style Instagram) en bas de page. « Mes publications »
  // n'apparaît que sur /me (owner view).
  const tabs: ProfileTab[] = [
    {
      key: 'organized',
      label: 'Événements organisés',
      icon: CalendarDays,
      content: <ProfileEventsList events={events} loading={eventsLoading} error={eventsError} hideHeading />,
    },
    {
      key: 'participations',
      label: 'Participations publiques',
      icon: Ticket,
      content: (
        <ProfileParticipations
          events={participations}
          loading={participationsLoading}
          error={participationsError}
          hideHeading
        />
      ),
    },
  ]
  if (isMeRoute) {
    tabs.push({
      key: 'publications',
      label: 'Mes publications',
      icon: LayoutGrid,
      content: <MyPublicationsPreview hideHeading />,
    })
  }

  return (
    <div>
      <ProfileHeader profile={profile} stats={headerStats} action={headerAction}>
        {leftExtras}
      </ProfileHeader>

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20 mt-10">
        <ProfileTabs tabs={tabs} />
      </div>
    </div>
  )
}

/**
 * `/profile/me` — owner view. Falls back to the public view above with
 * `isMeRoute=true`, using the authenticated `User` from `useAuth` so the
 * page renders immediately without a round-trip. We then refetch the public
 * profile by username in the background to surface the real
 * follower / following counts (the /users/me payload doesn't include them
 * so a freshly-rendered /me would show 0/0 until this resolves). The fetch
 * is best-effort: if it fails the tiles stay at 0/0 — no toast, no skeleton.
 * followStatus is always null when caller = target (API contract).
 */
function MeProfileView({ user }: Readonly<{ user: User }>) {
  const [counts, setCounts] = useState<{ followerCount: number; followingCount: number } | null>(null)
  const [countsLoading, setCountsLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setCountsLoading(true)
    getUserByUsername(user.username)
      .then((data) => {
        if (cancelled) return
        if (data !== null) {
          // userService.getUserByUsername is typed `User | null` but the endpoint
          // returns the richer `UserPublicResponse` shape (followerCount etc.).
          // Same boundary cast as the visitor-view fetch path below.
          const publicData = data as unknown as UserPublicResponse
          setCounts({
            followerCount: publicData.followerCount,
            followingCount: publicData.followingCount,
          })
        }
        setCountsLoading(false)
      })
      .catch(() => {
        if (!cancelled) setCountsLoading(false)
        // Best-effort: keep the 0/0 placeholder. The list pages still work.
      })
    return () => {
      cancelled = true
    }
  }, [user.username])

  const profile: UserPublicResponse = {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    faculty: user.faculty,
    studyLevel: user.studyLevel,
    bio: user.bio,
    interests: user.interests,
    avatarUrl: user.avatarUrl,
    bannerUrl: user.bannerUrl,
    profilePublic: user.profilePublic,
    followerCount: counts?.followerCount ?? 0,
    followingCount: counts?.followingCount ?? 0,
    followStatus: null,
    // Propagate the owner's roles so `ProfileHeader` can render the Staff
    // badge on /me too (mirrors what /profile/<other-admin-username> does).
    roles: user.roles ?? [],
  }
  // /me never renders the FollowButton — owner of the page.
  return <PublicProfileView profile={profile} isMeRoute={true} canFollow={false} statsLoading={countsLoading} />
}

/** Pure helper — extracted to keep ProfilePage's cognitive complexity within limits. */
function resolveIsMeRoute(username: string | undefined, currentUser: User | null): boolean {
  return username === 'me' || (currentUser !== null && username !== undefined && username === currentUser.username)
}

function useProfilePageState(
  username: string | undefined,
  currentUser: User | null,
  authLoading: boolean,
  isMeRoute: boolean,
  isLegacyUuid: boolean
) {
  const [profile, setProfile] = useState<UserPublicResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [redirectTarget, setRedirectTarget] = useState<string | null>(null)
  const [isNotFound, setIsNotFound] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const isRefetchRef = useRef(false)

  useEffect(() => {
    if (!username || authLoading) return

    const wasRefetch = isRefetchRef.current
    isRefetchRef.current = false

    if (!wasRefetch) {
      setLoading(true)
      setError(false)
      setRedirectTarget(null)
      setIsNotFound(false)
      setProfile(null)
    }

    if (isLegacyUuid) {
      getUserById(username)
        .then((data) => {
          if (data === null) {
            setIsNotFound(true)
          } else {
            setRedirectTarget(`/profile/${data.username}`)
          }
        })
        .catch(() => setError(true))
        .finally(() => setLoading(false))
      return
    }

    if (isMeRoute) {
      if (!currentUser) {
        setError(true)
      }
      setLoading(false)
      return
    }

    getUserByUsername(username)
      .then((data) => {
        if (data === null) {
          setIsNotFound(true)
        } else {
          setProfile(data as unknown as UserPublicResponse)
        }
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [username, isMeRoute, isLegacyUuid, currentUser, authLoading, reloadKey])

  const refetch = () => {
    isRefetchRef.current = true
    setReloadKey(k => k + 1)
  }

  return {
    profile,
    loading,
    error,
    redirectTarget,
    isNotFound,
    refetch,
    setReloadKey,
  }
}

function ProfileViewSelector({
  profile,
  currentUser,
  refetch,
}: Readonly<{
  profile: UserPublicResponse
  currentUser: User | null
  refetch: () => void
}>) {
  const canFollow = currentUser !== null && currentUser.id !== profile.id

  if (!profile.profilePublic && profile.followStatus !== 'ACCEPTED') {
    return (
      <ProfilePrivateState
        profile={profile}
        canFollow={canFollow}
        onProfileMutated={refetch}
      />
    )
  }

  return (
    <PublicProfileView
      profile={profile}
      isMeRoute={false}
      canFollow={canFollow}
      onProfileMutated={refetch}
    />
  )
}

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const isMeRoute = resolveIsMeRoute(username, currentUser)
  const isLegacyUuid = username !== undefined && UUID_V4_REGEX.test(username)

  const {
    profile,
    loading,
    error,
    redirectTarget,
    isNotFound,
    refetch,
    setReloadKey,
  } = useProfilePageState(username, currentUser, authLoading, isMeRoute, isLegacyUuid)

  if (loading || authLoading) {
    return (
      <Skeleton name="profile" loading={true} animate="pulse" color={skeletonColor}>
        <ProfileFixture />
      </Skeleton>
    )
  }
  if (redirectTarget) return <Navigate to={redirectTarget} replace />
  if (error) return <ErrorPage onRetry={isMeRoute ? undefined : () => setReloadKey(k => k + 1)} />

  if (isMeRoute) {
    return <MeProfileView user={currentUser!} />
  }

  if (isNotFound || profile === null) {
    return <NotFoundPage />
  }

  return (
    <ProfileViewSelector
      profile={profile}
      currentUser={currentUser}
      refetch={refetch}
    />
  )
}
