import { useEvents } from '@/hooks/useEvents'
import EventCard from './EventCard'
import { ErrorMessage } from '../utils/ErrorMessage'

export default function EventCards() {
  const { events, loading, error, hasMore, loadMore } = useEvents()

  return (
    <div className="flex flex-col gap-8">
      {loading && events.length === 0 && (
        <div className="flex justify-center py-16">
          <div className="w-8 h-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
        </div>
      )}

      {error && (
        <ErrorMessage message={error}/>
      )}

      {!loading && !error && events.length === 0 && (
        <div className="border border-border rounded-2xl p-10 text-center text-foreground/40">
          Aucun événement publié pour le moment.
        </div>
      )}

      {events.length > 0 && (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
          {events.map(event => (
            <EventCard key={event.id} event={event} />
          ))}
        </div>
      )}

      {!error && events.length > 0 && (hasMore || loading) && (
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
