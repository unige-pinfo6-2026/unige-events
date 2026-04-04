import { Link } from 'react-router-dom'
import Avatar from '@/components/user/Avatar'
import EventCard from '@/components/event/EventCard'
import { useAuth } from '@/hooks/useAuth'
import { useEvents } from '@/hooks/useEvents'

export default function DashboardPage() {
  const { user } = useAuth()
  const { events, loading, error, hasMore, loadMore } = useEvents()

  const firstName = user?.displayName ? user.displayName.split(' ')[0] : ''

  return (
    <div className="max-w-4xl mx-auto flex flex-col gap-6 py-12">
      {/* Welcome card */}
      <div className="bg-card border border-white/10 rounded-2xl p-7 flex items-center gap-5">
        <Avatar user={user} size={56} />
        <div>
          <div className="font-bold text-lg text-foreground">
            {'Bienvenue' + (firstName ? ', ' + firstName : '') + ' !'}
          </div>
          <div className="text-sm text-white/50 mt-1">
            Découvrez les événements du campus et rejoignez la communauté UNIGE.
          </div>
        </div>
        <div className="ml-auto flex gap-3 items-center flex-wrap justify-end">
          <Link
            to="/dashboard/events/new"
            className="px-4 py-2 bg-white/5 text-white/60 border border-white/10 rounded-lg text-sm font-semibold shrink-0 hover:bg-white/10 transition-colors no-underline"
          >
            Créer un événement
          </Link>
          <Link
            to="/dashboard/profile/me"
            className="px-4 py-2 bg-accent text-white rounded-lg text-sm font-semibold shrink-0 hover:bg-accent/90 transition-colors no-underline"
          >
            Mon profil
          </Link>
        </div>
      </div>

      {/* Events card */}
      <div className="bg-card border border-white/5 rounded-2xl p-6">
        <div className="text-xs font-bold uppercase tracking-widest text-white/40 mb-4">
          Événements à venir
        </div>

        {/* Loading */}
        {loading && events.length === 0 && (
          <div className="flex justify-center py-12">
            <div className="w-8 h-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="bg-card border border-white/10 rounded-xl p-8 text-center text-red-500">
            {error}
          </div>
        )}

        {/* Empty */}
        {!loading && !error && events.length === 0 && (
          <div className="bg-card border border-white/10 rounded-xl p-8 text-center text-white/40 text-base">
            Aucun événement publié pour le moment.
          </div>
        )}

        {/* Grid */}
        {events.length > 0 && (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
            {events.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        )}

        {/* Load more */}
        {!error && events.length > 0 && (hasMore || loading) && (
          <div className="flex justify-center mt-6">
            <button
              type="button"
              onClick={loadMore}
              disabled={loading}
              className={`px-8 py-2.5 rounded-lg font-semibold text-sm border border-white/10 transition-colors
                ${loading
                  ? 'bg-card text-white/40 cursor-not-allowed'
                  : 'bg-accent text-white cursor-pointer hover:bg-accent/90'
                }`}
            >
              {loading ? 'Chargement…' : 'Charger plus'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}