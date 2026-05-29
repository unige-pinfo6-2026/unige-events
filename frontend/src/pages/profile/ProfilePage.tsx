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
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import MyPublicationsPreview from '@/components/profile/MyPublicationsPreview'

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
  const studyLevelName = profile.studyLevel ? STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name ?? null : null
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

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const [profile, setProfile] = useState<UserPublicResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [redirectTarget, setRedirectTarget] = useState<string | null>(null)
  const [isNotFound, setIsNotFound] = useState(false)
  // Monotonic counter bumped by `refetch()` (SCRUM-110 — FollowButton invokes
  // it after follow/unfollow so followStatus + followerCount resync from the
  // server). Re-runs the username-fetch effect without changing the URL param.
  const [reloadKey, setReloadKey] = useState(0)
  // Tracks whether the current effect run was triggered by a refetch (not an
  // initial navigation). When true, the effect skips setLoading(true) and
  // setProfile(null) so the skeleton never flashes on follow/unfollow.
  const isRefetchRef = useRef(false)

  // SCRUM-169 — `me` alias + match by username (lowercased server-side).
  const isMeRoute = resolveIsMeRoute(username, currentUser)

  // SCRUM-169 — transient UUID v4 redirect (cf. Décision I, permanent). External
  // links / bookmarks pointing to the old `/profile/<uuid>` URLs land here, get
  // the user's username from the API, and `<Navigate replace>` to the canonical
  // `/profile/<username>` slug. AttendeeCard prefers username slugs but falls
  // back to UUID for orphan/cached rows — that fallback path triggers this.
  const isLegacyUuid = username !== undefined && UUID_V4_REGEX.test(username)

  useEffect(() => {
    if (!username || authLoading) return

    // Refetch (follow/unfollow mutation) — keep the current profile visible,
    // skip skeleton. Only a fresh navigation (username change / initial mount)
    // triggers the full loading reset.
    const wasRefetch = isRefetchRef.current
    isRefetchRef.current = false

    if (!wasRefetch) {
      setLoading(true)
      setError(null)
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
        .catch(() => setError('Impossible de charger le profil.'))
        .finally(() => setLoading(false))
      return
    }

    if (isMeRoute) {
      if (!currentUser) {
        setError('Impossible de charger le profil.')
      }
      // Don't write to `profile` here — MeProfileView reads currentUser
      // directly through useAuth. We just exit the loading state.
      setLoading(false)
      return
    }

    getUserByUsername(username)
      .then((data) => {
        if (data === null) {
          setIsNotFound(true)
        } else {
          // getUserByUsername is typed as `User | null` for backward-compat
          // with SCRUM-169 callers, but the runtime response is actually a
          // UserPublicResponse (carries follower counts / follow status —
          // verified against OpenAPI). Cast at the boundary.
          setProfile(data as unknown as UserPublicResponse)
        }
      })
      .catch(() => setError('Impossible de charger le profil.'))
      .finally(() => setLoading(false))
  }, [username, isMeRoute, isLegacyUuid, currentUser, authLoading, reloadKey])

  // SCRUM-110 — invoked by FollowButton.onMutated after a successful follow /
  // unfollow so `followStatus` + `followerCount` resync from the server.
  // Bumping `reloadKey` re-runs the effect above (cheaper than rebuilding the
  // whole hook), but only when we actually have a profile to refetch.
  const refetch = () => {
    if (!username || isMeRoute || isLegacyUuid) return
    isRefetchRef.current = true
    setReloadKey(k => k + 1)
  }

  if (loading || authLoading) {
    return (
      <Skeleton name="profile" loading={true} animate="pulse" color={skeletonColor}>
        <ProfileFixture />
      </Skeleton>
    )
  }
  if (redirectTarget) return <Navigate to={redirectTarget} replace />
  if (error) return <InfoMessage type="error" message={error} />

  if (isMeRoute) {
    if (!currentUser) return <InfoMessage type="error" message="Impossible de charger le profil." />
    return <MeProfileView user={currentUser} />
  }

  // Two paths land on the private-state card:
  //  - 404 from the backend (user does not exist) → no `profile` to display.
  //  - 200 with a restricted projection (SCRUM-169 Décision E revised) :
  //    backend returns id+username+displayName+avatarUrl+profilePublic=false
  //    for a non-owner non-admin caller of a private profile. We pass the
  //    payload through so the placeholder can render the user's banner
  //    (gradient fallback), avatar, and displayName — same visual frame
  //    as a public profile — with a centered « Compte privé » lock card
  //    replacing the content area.
  if (isNotFound || profile === null) {
    return <ProfilePrivateState />
  }
  if (!profile.profilePublic) {
    // Si le visiteur est déjà abonné (ACCEPTED), le backend lui renvoie quand
    // même profilePublic=false dans la projection restreinte, mais il a le
    // droit de voir le profil complet — on lui affiche donc PublicProfileView
    // comme pour n'importe quel profil public. Sans ce check, il verrait la
    // carte « Compte privé » et son clic sur "Suivre" se solderait par un 409.
    if (profile.followStatus === 'ACCEPTED') {
      const canFollow = currentUser !== null && currentUser.id !== profile.id
      return (
        <PublicProfileView
          profile={profile}
          isMeRoute={false}
          canFollow={canFollow}
          onProfileMutated={refetch}
        />
      )
    }

    // La projection restreinte contient `id` + `followStatus` — suffisant pour
    // afficher un FollowButton et permettre à un visiteur authentifié d'envoyer
    // une demande de suivi même sur un compte privé.
    const canFollowPrivate = currentUser !== null && currentUser.id !== profile.id
    return (
      <ProfilePrivateState
        profile={profile}
        canFollow={canFollowPrivate}
        onProfileMutated={refetch}
      />
    )
  }

  // canFollow: authenticated viewer AND looking at someone else's UUID.
  // /profile/<own-uuid> is rendered as a regular public profile per SCRUM-141,
  // but the viewer is the owner, so no FollowButton (you can't follow yourself).
  const canFollow = currentUser !== null && currentUser.id !== profile.id
  return (
    <PublicProfileView
      profile={profile}
      isMeRoute={false}
      canFollow={canFollow}
      onProfileMutated={refetch}
    />
  )
}
