import { Link } from 'react-router-dom'

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
  'flex flex-col items-center justify-center gap-1 rounded-2xl border border-border bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl px-7 py-5 min-w-[7.5rem] max-[583px]:flex-1'

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
      <span className="text-3xl font-bold text-foreground leading-none">{formatCount(count)}</span>
      <span className="text-xs font-semibold uppercase tracking-widest text-foreground/40">
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
 * Gros blocs « Abonnés » / « Abonnements » de la colonne droite du profil
 * (SCRUM-141 redesign). Chaque bloc linke vers la page de liste dédiée
 * (SCRUM-142) quand `linkUsername` est fourni — sinon il s'affiche en bloc
 * statique non cliquable.
 */
export default function ProfileStats({
  followerCount,
  followingCount,
  linkUsername,
}: Readonly<ProfileStatsProps>) {
  return (
    <div
      className="flex flex-wrap gap-3 w-full"
      aria-label="Compteurs de suivi"
    >
      <StatTile
        count={followerCount}
        label={followerCount === 1 ? 'Abonné' : 'Abonnés'}
        to={linkUsername ? `/profile/${linkUsername}/followers` : undefined}
        ariaLabel={`Voir les abonnés (${formatCount(followerCount)})`}
      />
      <StatTile
        count={followingCount}
        label={followingCount === 1 ? 'Abonnement' : 'Abonnements'}
        to={linkUsername ? `/profile/${linkUsername}/following` : undefined}
        ariaLabel={`Voir les abonnements (${formatCount(followingCount)})`}
      />
    </div>
  )
}
