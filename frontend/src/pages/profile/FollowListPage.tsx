import { useEffect, useState } from 'react'
import { Link, NavLink, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useAuth } from '@/hooks/useAuth'
import { getUserByUsername } from '@/services/userService'
import { useFollowList, type FollowListMode } from '@/hooks/useFollowList'
import FollowListRow from '@/components/profile/FollowListRow'
import ProfilePrivateState from '@/components/profile/ProfilePrivateState'
import { ButtonSecondary } from '@/components/utils/Buttons'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { useTheme } from '@/contexts/ThemeContext'
import type { UserPublicResponse } from '@/types/user'

interface FollowListPageProps {
  mode: FollowListMode
}

const FIXTURE_ROW_KEYS = ['r1', 'r2', 'r3', 'r4', 'r5', 'r6'] as const

function FollowListFixture() {
  return (
    <div>
      <div className="h-4 w-32 rounded bg-foreground/10 mb-6" />
      <div className="h-9 w-64 rounded bg-foreground/10 mb-4" />
      <div className="flex gap-3 mb-8">
        <div className="h-9 w-32 rounded-xl bg-foreground/10" />
        <div className="h-9 w-32 rounded-xl bg-foreground/10" />
      </div>
      <div className="flex flex-col">
        {FIXTURE_ROW_KEYS.map(key => (
          <div key={key} className="flex items-center gap-3 py-3 border-t border-border/40 first:border-t-0">
            <div className="size-12 rounded-full bg-foreground/10 shrink-0" />
            <div className="flex-1 flex flex-col gap-2">
              <div className="h-4 w-40 rounded bg-foreground/10" />
              <div className="h-3 w-56 rounded bg-foreground/10" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

interface ResolvedTarget {
  uuid: string
  username: string
  displayName: string
  followerCount: number
  followingCount: number
}

interface FollowListBodyProps {
  target: ResolvedTarget
  mode: FollowListMode
}

const tabBaseClass =
  'inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold no-underline transition-colors'
const tabVariants = {
  active: 'bg-accent/10 border border-accent/40 text-foreground',
  inactive: 'border border-border text-foreground/60 hover:text-foreground hover:border-foreground/30',
} as const

const headings = {
  followers: 'Followers',
  following: 'Abonnements',
} as const

const emptyMessages = {
  followers: "Aucun follower pour le moment.",
  following: "Aucun abonnement pour le moment.",
} as const

function FollowListBody({ target, mode }: Readonly<FollowListBodyProps>) {
  const { users, loading, loadingMore, isNotFound, error, hasMore, loadMore } =
    useFollowList(target.uuid, mode)

  if (isNotFound) {
    // Owner sees their own private list fine (200), so this only fires when
    // the target became private between the username resolve and the list
    // fetch (e.g. concurrent PATCH on `/users/me`). Fall back to the
    // private-state card to stay consistent with `/profile/:username`.
    return <ProfilePrivateState />
  }

  const count = mode === 'followers' ? target.followerCount : target.followingCount
  const otherCount = mode === 'followers' ? target.followingCount : target.followerCount

  return (
    <div className="max-w-3xl mx-auto px-6 lg:px-8 py-12 lg:py-16">
      <Link
        to={`/profile/${target.username}`}
        className="inline-flex items-center gap-1.5 text-sm text-foreground/50 hover:text-foreground no-underline mb-6 transition-colors"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Retour au profil de {target.displayName}
      </Link>

      <h1 className="text-3xl lg:text-4xl font-bold tracking-tight mb-6 wrap-anywhere">
        {headings[mode]} de {target.displayName}
      </h1>

      <nav aria-label="Onglets followers / abonnements" className="flex flex-wrap gap-3 mb-8">
        <NavLink
          to={`/profile/${target.username}/followers`}
          end
          className={() =>
            `${tabBaseClass} ${mode === 'followers' ? tabVariants.active : tabVariants.inactive}`
          }
        >
          Followers <span className="text-foreground/40">· {mode === 'followers' ? count : otherCount}</span>
        </NavLink>
        <NavLink
          to={`/profile/${target.username}/following`}
          end
          className={() =>
            `${tabBaseClass} ${mode === 'following' ? tabVariants.active : tabVariants.inactive}`
          }
        >
          Abonnements <span className="text-foreground/40">· {mode === 'following' ? count : otherCount}</span>
        </NavLink>
      </nav>

      {loading && (
        <ul className="flex flex-col gap-2" aria-busy="true">
          {FIXTURE_ROW_KEYS.map(key => (
            <li key={key} className="flex items-center gap-3 py-3">
              <div className="size-12 rounded-full bg-foreground/10 shrink-0 animate-pulse" />
              <div className="flex-1 flex flex-col gap-2">
                <div className="h-4 w-40 rounded bg-foreground/10 animate-pulse" />
                <div className="h-3 w-56 rounded bg-foreground/10 animate-pulse" />
              </div>
            </li>
          ))}
        </ul>
      )}

      {!loading && error && <InfoMessage type="error" message={error} />}

      {!loading && !error && users.length === 0 && (
        <p className="text-foreground/50 text-sm py-6 text-center">
          {emptyMessages[mode]}
        </p>
      )}

      {!loading && !error && users.length > 0 && (
        <>
          <ul className="flex flex-col divide-y divide-border/40">
            {users.map(user => (
              <FollowListRow key={user.id} user={user} />
            ))}
          </ul>

          {hasMore && (
            <div className="flex justify-center mt-8">
              <ButtonSecondary
                onClick={loadMore}
                disabled={loadingMore}
              >
                {loadingMore ? 'Chargement…' : 'Charger plus'}
              </ButtonSecondary>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/**
 * `/profile/:username/followers` and `/profile/:username/following`
 * (SCRUM-142). Lists the target user's followers or follows with paginated
 * "Charger plus" pagination. `:username = 'me'` (or the caller's own
 * username) routes to the caller's own list via `useAuth` — no detour
 * through `getUserByUsername`.
 *
 * Resolution order:
 * 1. `useParams` → `username`
 * 2. If `me` or matches `currentUser.username`, use `currentUser` (no fetch).
 * 3. Else `getUserByUsername(username)` → resolve uuid, displayName, counters.
 *    Backend 404 (target missing or private + caller not owner) →
 *    `ProfilePrivateState`. Other errors → toast-style error message.
 * 4. Hand uuid + mode to `useFollowList`, render rows.
 */
export default function FollowListPage({ mode }: Readonly<FollowListPageProps>) {
  const { username } = useParams<{ username: string }>()
  const { user: currentUser, isLoading: authLoading } = useAuth()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [target, setTarget] = useState<ResolvedTarget | null>(null)
  const [loading, setLoading] = useState(true)
  const [isNotFound, setIsNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isMeRoute =
    username === 'me' ||
    (currentUser !== null && username !== undefined && username === currentUser.username)

  useEffect(() => {
    if (!username || authLoading) return

    setLoading(true)
    setIsNotFound(false)
    setError(null)
    setTarget(null)

    if (isMeRoute) {
      if (!currentUser) {
        setError('Impossible de charger le profil.')
        setLoading(false)
        return
      }
      // Self payload doesn't carry follower / following counts (cf.
      // ProfilePage MeProfileView note). The tab labels render `0` here
      // until SCRUM-110 follow-up exposes them on `useAuth`.
      setTarget({
        uuid: currentUser.id,
        username: currentUser.username,
        displayName: currentUser.displayName ?? currentUser.username,
        followerCount: 0,
        followingCount: 0,
      })
      setLoading(false)
      return
    }

    let cancelled = false
    getUserByUsername(username)
      .then((data) => {
        if (cancelled) return
        if (data === null) {
          setIsNotFound(true)
          setLoading(false)
          return
        }
        // Runtime payload of `/users/by-username/{u}` is actually a
        // `UserPublicResponse` carrying follower counts (cf. ProfilePage
        // note). Cast at the boundary.
        const profile = data as unknown as UserPublicResponse
        setTarget({
          uuid: profile.id,
          username: profile.username,
          displayName: profile.displayName ?? profile.username,
          followerCount: profile.followerCount,
          followingCount: profile.followingCount,
        })
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setError('Impossible de charger le profil.')
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [username, isMeRoute, currentUser, authLoading])

  if (loading || authLoading) {
    return (
      <div className="max-w-3xl mx-auto px-6 lg:px-8 py-12 lg:py-16">
        <Skeleton name="follow-list" loading={true} animate="pulse" color={skeletonColor}>
          <FollowListFixture />
        </Skeleton>
      </div>
    )
  }
  if (error) return <InfoMessage type="error" message={error} />
  if (isNotFound || target === null) {
    return <ProfilePrivateState />
  }

  return <FollowListBody target={target} mode={mode} />
}
