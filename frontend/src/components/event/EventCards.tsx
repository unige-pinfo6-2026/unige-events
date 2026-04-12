import { Skeleton } from 'boneyard-js/react'
import { useEvents } from '@/hooks/useEvents'
import { useTheme } from '@/contexts/ThemeContext'
import EventCard from './EventCard'
import { InfoMessage } from '../utils/InfoMessage'

function EventCardsFixture() {
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
      {Array.from({ length: 6 }).map((_, i) => (
        <article key={i} className="relative bg-background border border-border rounded-3xl overflow-hidden">
          <div className="relative h-52 bg-foreground/10">
            <span className="absolute top-4 left-4 h-6 w-24 rounded-full bg-foreground/20" />
            <div className="absolute bottom-0 left-0 right-0 px-5 pb-5 flex flex-col gap-2">
              <div className="h-6 w-4/5 rounded-md bg-foreground/25" />
              <div className="h-4 w-1/2 rounded-md bg-foreground/20" />
            </div>
          </div>
          <div className="p-5 flex flex-col gap-3">
            <div className="flex flex-col gap-2.5">
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-40 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-32 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-24 rounded bg-foreground/10" />
              </div>
            </div>
            <div className="border-t border-border" />
            <div className="flex flex-col gap-1.5">
              <div className="h-3.5 w-full rounded bg-foreground/10" />
              <div className="h-3.5 w-5/6 rounded bg-foreground/10" />
            </div>
          </div>
        </article>
      ))}
    </div>
  )
}

export default function EventCards() {
  const { events, loading, error, hasMore, loadMore } = useEvents()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.08)'

  if (loading && events.length === 0) return (
    <Skeleton
      name="event-cards"
      loading={true}
      animate="pulse"
      color={skeletonColor}
    ><EventCardsFixture /></Skeleton>
  )

  if (error) return <InfoMessage type='error' message={error} />

  if (events.length === 0) return <InfoMessage type='info' message={"Aucun événement publié pour le moment."} />

  return (
    <div className="flex flex-col gap-8">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
        {events.map(event => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>

      {(hasMore || loading) && (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={loadMore}
            disabled={loading}
            className={[
              'px-8 py-3 rounded-xl font-semibold text-sm border transition-all',
              loading
                ? 'border-border text-foreground/40 cursor-not-allowed'
                : 'border-transparent bg-linear-to-r from-accent to-pink-600 text-white shadow-lg shadow-accent/20 hover:shadow-accent/30 cursor-pointer',
            ].join(' ')}
          >
            {loading ? 'Chargement…' : 'Charger plus'}
          </button>
        </div>
      )}
    </div>
  )
}
