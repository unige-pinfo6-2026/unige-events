import { useEffect, useRef } from 'react'
import { Rss } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import { useFeed } from '@/hooks/useFeed'
import { useTheme } from '@/contexts/ThemeContext'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import Timeline from '@/components/feed/Timeline'

// ─── Skeleton fixture ──────────────────────────────────────────────────────────
// Reproduit la structure CSS de la timeline : 3 groupes × 2 cartes.
// Dimensions fixes (non responsives) pour que le bones.height soit constant.

function FeedFixture() {
  return (
    <div className="flex flex-col gap-8">
      {([0, 1, 2] as const).map(i => (
        <div key={i} className="flex flex-col gap-3">
          <div className="flex items-center gap-4">
            <div className="w-7 h-7 md:w-10 md:h-10 rounded-full shrink-0" />
            <div className="h-5 w-40 rounded-lg" />
          </div>
          <div className="pl-11 md:pl-14 flex flex-col gap-3">
            <div className="h-40 md:h-28 rounded-2xl" />
            <div className="h-40 md:h-28 rounded-2xl" />
          </div>
        </div>
      ))}
    </div>
  )
}

// ─── FeedPage ──────────────────────────────────────────────────────────────────

export default function FeedPage() {
  const { groups, loading, error, hasMore, loadMore } = useFeed()
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

          {/* Toggle segmenté Tous / Mes abonnements */}
          <div
            className="flex shrink-0 rounded-xl bg-foreground/5 p-1 border border-border self-start"
            role="group"
            aria-label="Filtrer le fil"
          >
            <button
              type="button"
              className="px-4 py-1.5 rounded-lg text-sm font-medium bg-background text-foreground border border-border shadow-sm transition-colors cursor-pointer"
              aria-pressed="true"
            >
              Tous
            </button>
            <button
              type="button"
              disabled
              title="Bientôt disponible"
              aria-disabled="true"
              aria-pressed="false"
              className="px-4 py-1.5 rounded-lg text-sm font-medium text-foreground/30 cursor-not-allowed select-none"
            >
              Mes abonnements
            </button>
          </div>
        </div>

        {/* ── Contenu ── */}
        {showSkeleton ? (
          <Skeleton name="feed-timeline" loading animate="pulse" color={skeletonColor}>
            <FeedFixture />
          </Skeleton>
        ) : error ? (
          <InfoMessage type="error" message={error} />
        ) : groups.length === 0 ? (
          /* État vide */
          <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
            <Rss className="w-10 h-10 text-foreground/20" aria-hidden="true" />
            <p className="text-foreground/50 text-sm">
              Aucun événement à venir pour le moment.
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
