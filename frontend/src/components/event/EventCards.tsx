import { useEvents } from '@/hooks/useEvents'
import EventCard from './EventCard'
import { InfoMessage } from '../utils/InfoMessage'
import { LoadingSpinner } from '../utils/LoadingSpinner'

export default function EventCards() {
  const { events, loading, error, hasMore, loadMore } = useEvents()

  if(loading) return <LoadingSpinner/>
  if(error) return <InfoMessage type='error' message={error}/>

  if(events.length == 0) return <InfoMessage type='info' message={"Aucun événement publié pour le moment."}/>

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
