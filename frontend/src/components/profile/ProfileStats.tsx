import { Users } from 'lucide-react'

interface ProfileStatsProps {
  followerCount: number
  followingCount: number
}

const statTileClass = 'flex flex-col items-center gap-0.5 rounded-2xl border border-border bg-foreground/5 px-5 py-3'

function formatCount(n: number): string {
  return n.toLocaleString('fr-CH')
}

/**
 * Followers / following counters for a public profile (SCRUM-141).
 * Stub-only display — the dedicated followers / following pages (and the
 * follow action button) are SCRUM-142 / SCRUM-110 follow-ups. The counters
 * are not links yet; they render as plain stat tiles to surface the data
 * without committing to a destination route that doesn't exist.
 */
export default function ProfileStats({ followerCount, followingCount }: Readonly<ProfileStatsProps>) {
  return (
    <div className="flex flex-wrap gap-3" aria-label="Compteurs de suivi">
      <div className={statTileClass}>
        <span className="text-xl font-bold text-foreground">{formatCount(followerCount)}</span>
        <span className="text-xs font-semibold uppercase tracking-widest text-foreground/40 flex items-center gap-1.5">
          <Users className="size-3.5" aria-hidden="true" />
          {followerCount === 1 ? 'follower' : 'followers'}
        </span>
      </div>
      <div className={statTileClass}>
        <span className="text-xl font-bold text-foreground">{formatCount(followingCount)}</span>
        <span className="text-xs font-semibold uppercase tracking-widest text-foreground/40 flex items-center gap-1.5">
          <Users className="size-3.5" aria-hidden="true" />
          abonnements
        </span>
      </div>
    </div>
  )
}
