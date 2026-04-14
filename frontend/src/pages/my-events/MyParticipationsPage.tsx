import { Calendar } from 'lucide-react'
import EventCard from '@/components/event/EventCard'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { useMyParticipations } from '@/hooks/useMyParticipations'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import { EventGridFixture } from './shared'

export default function MyParticipationsPage() {
  const { events, loading, error } = useMyParticipations()
  const { favoritedIds } = useFavoritesContext()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Participations</mark></>}
        subtitle="Tous les événements auxquels vous êtes inscrit(e)."
      />

      {loading && (
        <Skeleton name="my-events" loading animate="pulse" color={skeletonColor}>
          <EventGridFixture />
        </Skeleton>
      )}
      {!loading && error && <InfoMessage type="error" message={error} />}
      {!loading && !error && events.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
          <Calendar className="w-16 h-16 text-foreground/20" />
          <p className="text-foreground/50 text-lg font-medium">Vous ne participez à aucun événement</p>
          <p className="text-foreground/35 text-sm">Inscrivez-vous à un événement pour le retrouver ici.</p>
        </div>
      )}
      {!loading && !error && events.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {events.map(event => (
            <div key={event.id} className="relative">
              <span className="absolute top-4 left-1/2 -translate-x-1/2 z-10 text-[10px] font-bold uppercase tracking-widest px-2.5 py-1 rounded-full bg-emerald-500/90 text-white shadow-md">
                Inscrit
              </span>
              <EventCard event={event} favorited={favoritedIds.has(event.id)} />
            </div>
          ))}
        </div>
      )}
    </SectionWrapper>
  )
}
