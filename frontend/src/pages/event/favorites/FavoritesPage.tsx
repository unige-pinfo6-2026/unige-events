import { useCallback, useEffect, useState } from 'react'
import { Star } from 'lucide-react'
import { getFavorites } from '@/services/favoriteApi'
import EventCard from '@/components/event/EventCard'
import LoadingPage from '@/pages/LoadingPage'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import type { Event } from '@/types/event'
import ErrorPage from '@/pages/ErrorPage'

export default function FavoritesPage() {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const fetchFavorites = useCallback(async () => {
    setLoading(true)
    setError(false)
    try {
      const data = await getFavorites()
      setEvents(data)
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchFavorites()
  }, [fetchFavorites])

  function handleRemove(eventId: number) {
    setEvents(prev => prev.filter(e => e.id !== eventId))
  }

  if (loading) return <LoadingPage />
  if (error) return <ErrorPage onRetry={fetchFavorites} />

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Favoris</mark></>}
        subtitle="Les événements que vous avez mis en favoris."
      />

      {events.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
          <Star className="w-16 h-16 text-foreground/20" />
          <p className="text-foreground/50 text-lg font-medium">Vous n'avez aucun favori pour le moment</p>
          <p className="text-foreground/35 text-sm">Explorez des événements et ajoutez-les à vos favoris</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {events.map(event => (
            <EventCard
              key={event.id}
              event={event}
              favorited={true}
              onFavoriteRemove={() => handleRemove(event.id)}
            />
          ))}
        </div>
      )}
    </SectionWrapper>
  )
}
