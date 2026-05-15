import { Link } from 'react-router-dom'
import { Users } from 'lucide-react'

interface ProfileStatsProps {
  followerCount: number
  followingCount: number
  /**
   * Slug used to build the destination URLs. When provided, the tiles render
   * as `<Link>`s to `/profile/{linkUsername}/followers` and
   * `/.../following` (SCRUM-142). When `undefined` — e.g. the caller doesn't
   * know which slug to use yet — the tiles render as plain stat tiles.
   */
  linkUsername?: string
}

const statTileBase =
  'flex flex-col items-center gap-0.5 rounded-2xl border border-border bg-foreground/5 px-5 py-3'

const statTileLink = `${statTileBase} no-underline transition-colors hover:border-accent/50 hover:bg-foreground/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent`

function formatCount(n: number): string {
  return n.toLocaleString('fr-CH')
}

interface StatTileProps {
  count: number
  label: string
  to?: string
  ariaLabel: string
}

function StatTile({ count, label, to, ariaLabel }: Readonly<StatTileProps>) {
  const content = (
    <>
      <span className="text-xl font-bold text-foreground">{formatCount(count)}</span>
      <span className="text-xs font-semibold uppercase tracking-widest text-foreground/40 flex items-center gap-1.5">
        <Users className="size-3.5" aria-hidden="true" />
        {label}
      </span>
    </>
  )

  if (to) {
    return (
      <Link to={to} aria-label={ariaLabel} className={statTileLink}>
        {content}
      </Link>
    )
  }
  return <div className={statTileBase}>{content}</div>
}

/**
 * Followers / following counters for a public profile (SCRUM-141).
 * Each tile links to the dedicated list page (SCRUM-142) when
 * `linkUsername` is provided — otherwise it renders as a plain stat tile.
 */
export default function ProfileStats({
  followerCount,
  followingCount,
  linkUsername,
}: Readonly<ProfileStatsProps>) {
  return (
    <div className="flex flex-wrap gap-3" aria-label="Compteurs de suivi">
      <StatTile
        count={followerCount}
        label={followerCount === 1 ? 'follower' : 'followers'}
        to={linkUsername ? `/profile/${linkUsername}/followers` : undefined}
        ariaLabel={`Voir les followers (${formatCount(followerCount)})`}
      />
      <StatTile
        count={followingCount}
        label="abonnements"
        to={linkUsername ? `/profile/${linkUsername}/following` : undefined}
        ariaLabel={`Voir les abonnements (${formatCount(followingCount)})`}
      />
    </div>
  )
}
