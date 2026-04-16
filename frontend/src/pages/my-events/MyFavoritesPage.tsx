import { useCallback, useEffect, useState } from 'react'
import { Star } from 'lucide-react'
import { getFavorites } from '@/services/favoriteApi'
import EventCard from '@/components/event/EventCard'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { EventGridFixture } from './shared'
import type { Event } from '@/types/event'

export default function MyFavoritesPage() {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const fetchFavorites = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getFavorites()
      setEvents(data)
    } catch {
      setError('Impossible de charger vos favoris.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchFavorites() }, [fetchFavorites])

  function handleRemove(eventId: number) {
    setEvents(prev => prev.filter(e => e.id !== eventId))
  }

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Favoris</mark></>}
        subtitle="Les événements que vous avez mis en favoris."
      />

      {loading && (
        <Skeleton name="my-events" loading animate="pulse" color={skeletonColor}>
          <EventGridFixture />
        </Skeleton>
      )}
      {!loading && error && <InfoMessage type="error" message={error} />}
      {!loading && !error && events.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
          <Star className="w-16 h-16 text-foreground/20" />
          <p className="text-foreground/50 text-lg font-medium">Aucun favori pour le moment</p>
          <p className="text-foreground/35 text-sm">Explorez des événements et ajoutez-les à vos favoris.</p>
        </div>
      )}
      {!loading && !error && events.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {events.map(event => (
            <EventCard
              key={event.id}
              event={event}
              favorited
              onFavoriteRemove={() => handleRemove(event.id)}
            />
          ))}
        </div>
      )}
    </SectionWrapper>
  )
}
