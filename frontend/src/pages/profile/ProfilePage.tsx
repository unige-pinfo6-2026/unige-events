import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useOrganizerEvents } from '@/hooks/useOrganizerEvents'
import { getUserById, getUserByUsername } from '@/services/userService'
import FollowButton from '@/components/user/FollowButton'
import ProfileHeader from '@/components/profile/ProfileHeader'
import ProfileStats from '@/components/profile/ProfileStats'
import ProfileEventsList from '@/components/profile/ProfileEventsList'
import ProfileParticipations from '@/components/profile/ProfileParticipations'
import ProfilePrivateState from '@/components/profile/ProfilePrivateState'
import FollowRequestsPanel from '@/components/profile/FollowRequestsPanel'
import CoOrganizerInvitationsList from '@/components/user/CoOrganizerInvitationsList'
import { STUDY_LEVELS, type StudyLevel, type User, type UserPublicResponse } from '@/types/user'
import { FACULTIES, type Faculty } from '@/types/faculty'
import { GraduationCap, type LucideIcon } from 'lucide-react'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import CalendarSubscribeButton from '@/components/calendar/CalendarSubscribeButton'
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
        <div className="relative -mt-14 flex flex-wrap items-end justify-between gap-4 mb-8">
          <div className="flex items-end gap-5">
            <div className="relative shrink-0 w-28 h-28 rounded-full bg-foreground/10 ring-4 ring-background" />
            <div className="pb-2 flex flex-col gap-2">
              <div className="h-9 w-52 rounded bg-foreground/10" />
              <div className="h-4 w-36 rounded bg-foreground/10" />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
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
}

/**
 * Renders the full public profile (SCRUM-141 + SCRUM-169): banner, overlapping
 * avatar, displayName + faculté/niveau, bio, follower/following counters,
 * events organised, public participations. Reused for both
 * `/profile/<username>` and `/profile/me` (the `/me` route adds owner-only
 * widgets — edit button, calendar subscription, MyPublicationsPreview,
 * co-organizer invitations).
 */
function PublicProfileView({ profile, isMeRoute, canFollow, onProfileMutated }: Readonly<PublicProfileViewProps>) {
  const { events, loading: eventsLoading, error: eventsError } = useOrganizerEvents(profile.id)

  const studyLevelName = profile.studyLevel
    ? STUDY_LEVELS[profile.studyLevel as StudyLevel]?.name
    : null
  const facultyEntry = profile.faculty ? FACULTIES[profile.faculty as Faculty] : null
  const facultyName = facultyEntry?.name ?? null
  const FacultyLogo = facultyEntry?.logo ?? null

  const headerActions = (
    <>
      {isMeRoute && (
        <Link
          to="/profile/me/edit"
          className="inline-flex items-center gap-2 self-end px-4 py-2 rounded-xl border-2 border-border text-sm font-semibold text-foreground no-underline hover:border-accent/50 hover:bg-foreground/5 transition-all shrink-0"
        >
          Modifier
        </Link>
      )}
      {canFollow && (
        <FollowButton
          targetId={profile.id}
          followStatus={profile.followStatus}
          onMutated={onProfileMutated}
        />
      )}
    </>
  )

  return (
    <div>
      <ProfileHeader profile={profile} actions={headerActions} />

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">

        {/* Stats row directly below the header.
            Hidden on /profile/me — the self payload (UserProfileResponse)
            doesn't carry followerCount / followingCount, so rendering the
            tiles would display 0/0 for accounts that actually have real
            follows. Owners get their counters via the dedicated followers /
            following pages (SCRUM-142 / SCRUM-110 follow-ups) instead. */}
        {!isMeRoute && (
          <div className="mb-8">
            <ProfileStats
              followerCount={profile.followerCount}
              followingCount={profile.followingCount}
              linkUsername={profile.username}
            />
          </div>
        )}

        {/* Content grid: about/bio (left) + calendar+invitations (right, /me only).
            When not on /me the layout collapses to a single column so the events
            and participations sections below get the full width. */}
        <div className={`grid grid-cols-1 items-start gap-6 ${isMeRoute ? 'lg:grid-cols-2' : ''}`}>

          <div className="flex flex-col gap-6">
            {profile.bio && (
              <p className="text-foreground/60 leading-relaxed whitespace-pre-wrap wrap-anywhere">
                {profile.bio}
              </p>
            )}

            <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6">
              <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-5">À propos</h2>
              <div className="flex flex-col gap-4">
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

            {isMeRoute && <MyPublicationsPreview />}
            {isMeRoute && <FollowRequestsPanel />}
          </div>

          {/* Right column: calendar + co-organizer invitations (own profile only) */}
          {isMeRoute && (
            <div className="flex flex-col gap-6">
              <CalendarSubscribeButton />
              <CoOrganizerInvitationsList />
            </div>
          )}
        </div>

        {/* Events organised + public participations, full-width below the grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
          <ProfileEventsList events={events} loading={eventsLoading} error={eventsError} />
          <ProfileParticipations />
        </div>

      </div>
    </div>
  )
}

/**
 * `/profile/me` — owner view. Falls back to the public view above with
 * `isMeRoute=true`, using the authenticated `User` from `useAuth` so the
 * page renders without a round-trip to `/users/{id}`. Counters and
 * followStatus are not in the self payload (UserProfileResponse) so we
 * default to 0 / null — coherent with the API contract for the caller's
 * own profile (followStatus is always null when caller = target).
 */
function MeProfileView({ user }: Readonly<{ user: User }>) {
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
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }
  // /me never renders the FollowButton — owner of the page.
  return <PublicProfileView profile={profile} isMeRoute={true} canFollow={false} />
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

  // SCRUM-169 — `me` alias + match by username (lowercased server-side).
  const isMeRoute =
    username === 'me' ||
    (currentUser !== null && username !== undefined && username === currentUser.username)

  // SCRUM-169 — transient UUID v4 redirect (cf. Décision I, permanent). External
  // links / bookmarks pointing to the old `/profile/<uuid>` URLs land here, get
  // the user's username from the API, and `<Navigate replace>` to the canonical
  // `/profile/<username>` slug. AttendeeCard prefers username slugs but falls
  // back to UUID for orphan/cached rows — that fallback path triggers this.
  const isLegacyUuid = username !== undefined && UUID_V4_REGEX.test(username)

  useEffect(() => {
    if (!username || authLoading) return

    setLoading(true)
    setError(null)
    setRedirectTarget(null)
    setIsNotFound(false)
    setProfile(null)

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
    return <ProfilePrivateState profile={profile} />
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
