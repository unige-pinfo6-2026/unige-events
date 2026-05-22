import { useEffect, useRef, useState } from 'react'
import { Rss } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useFeed } from '@/hooks/useFeed'
import { useAuth } from '@/hooks/useAuth'
import { useTheme } from '@/contexts/ThemeContext'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import Timeline from '@/components/feed/Timeline'

// ─── Skeleton fixture ──────────────────────────────────────────────────────────
// Reproduit EXACTEMENT la structure CSS de `Timeline` : 3 groupes × (DateMarker +
// 2 EventFeedCard). La fixture est cachée au runtime — seule sa hauteur compte :
// elle doit égaler le `height` de `feed-timeline.bones.json` par breakpoint
// (sinon scaleY déforme les bones). Groupe = `gap-4 pb-8` ; DateMarker = conteneur
// 28/40px contenant un petit dot 12px ; carte = `flex-col md:flex-row` → bannière
// empilée + infos en mobile (`h-56` = 224px) / rangée `h-28` = 112px en desktop.

function FeedFixture() {
  return (
    <div className="flex flex-col">
      {([0, 1, 2] as const).map(i => (
        <div key={i} className="flex flex-col gap-4 pb-8 last:pb-0">
          <div className="flex items-center gap-4">
            <div className="w-7 h-7 md:w-10 md:h-10 shrink-0 flex items-center justify-center">
              <div className="w-3 h-3 rounded-full" />
            </div>
            <div className="h-5 w-40 rounded-lg" />
          </div>
          <div className="pl-11 md:pl-14 flex flex-col gap-3">
            <div className="h-56 md:h-28 rounded-2xl" />
            <div className="h-56 md:h-28 rounded-2xl" />
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── FeedPage ──────────────────────────────────────────────────────────────────

export default function FeedPage() {
  const { user } = useAuth()
  // Filtre « Mes abonnements » (SCRUM-168). Réservé aux utilisateurs connectés —
  // le toggle n'est rendu que si `user` (le filtre exige un token côté backend).
  const [followedOnly, setFollowedOnly] = useState(false)
  // Si l'utilisateur se déconnecte / sa session expire alors que le filtre est
  // actif, on retombe sur le fil public « Tous » (le toggle est masqué de toute
  // façon) — sinon useFeed continuerait d'émettre des 401 sans retour possible.
  const effectiveFollowedOnly = Boolean(user) && followedOnly
  const { groups, loading, error, hasMore, loadMore } = useFeed({ followedOnly: effectiveFollowedOnly })
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const sentinelRef = useRef<HTMLDivElement>(null)

  // Infinite scroll via IntersectionObserver sur le sentinel en bas de page
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasMore) return

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0]?.isIntersecting && !loading) {
          loadMore()
        }
      },
      { threshold: 0.1 },
    )

    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, loading, loadMore])

  const showSkeleton = loading && groups.length === 0

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <div className="flex flex-col gap-8">

        {/* ── En-tête ── */}
        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-6">
          <SectionHeader
            align="left"
            title={<>Fil <mark>d'événements</mark></>}
            subtitle="Tous les événements à venir, dans l'ordre chronologique."
          />

          {/* Toggle segmenté Tous / Mes abonnements — uniquement pour les
              utilisateurs connectés (le filtre followedOnly exige un token). */}
          {user && (
            <div
              className="flex shrink-0 rounded-xl bg-foreground/5 p-1 border border-border self-start"
              role="group"
              aria-label="Filtrer le fil"
            >
              <button
                type="button"
                onClick={() => setFollowedOnly(false)}
                aria-pressed={!followedOnly}
                className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors cursor-pointer ${
                  followedOnly
                    ? 'text-foreground/50 hover:text-foreground'
                    : 'bg-background text-foreground border border-border shadow-sm'
                }`}
              >
                Tous
              </button>
              <button
                type="button"
                onClick={() => setFollowedOnly(true)}
                aria-pressed={followedOnly}
                className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors cursor-pointer ${
                  followedOnly
                    ? 'bg-background text-foreground border border-border shadow-sm'
                    : 'text-foreground/50 hover:text-foreground'
                }`}
              >
                Mes abonnements
              </button>
            </div>
          )}
        </div>

        {/* ── Contenu ── */}
        {showSkeleton ? (
          <Skeleton name="feed-timeline" loading animate="pulse" color={skeletonColor}>
            <FeedFixture />
          </Skeleton>
        ) : error ? (
          <InfoMessage type="error" message={error} />
        ) : groups.length === 0 ? (
          /* État vide — message dédié quand le filtre abonnements est actif */
          <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
            <Rss className="w-10 h-10 text-foreground/20" aria-hidden="true" />
            <p className="text-foreground/50 text-sm max-w-sm">
              {effectiveFollowedOnly
                ? 'Aucun événement à venir de vos abonnements. Suivez des organisateurs pour voir leurs événements ici.'
                : 'Aucun événement à venir pour le moment.'}
            </p>
          </div>
        ) : (
          <>
            <Timeline groups={groups} />

            {/* Sentinel pour l'infinite scroll */}
            <div ref={sentinelRef} className="h-1" aria-hidden="true" data-testid="scroll-sentinel" />

            {/* Spinner de chargement de la page suivante */}
            {loading && (
              <div className="flex justify-center py-6" aria-label="Chargement en cours">
                <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
              </div>
            )}
          </>
        )}
      </div>
    </SectionWrapper>
  )
}
