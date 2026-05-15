import { Skeleton } from 'boneyard-js/react'
import { useFeaturedEvents } from '@/hooks/useFeaturedEvents'
import { useTheme } from '@/contexts/ThemeContext'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import EventCard from './EventCard'

const MAX_FEATURED = 6

// Local fixture mirrors the skeleton shape of EventCard so boneyard can scale it.
function FeaturedEventsFixture() {
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
      {Array.from({ length: MAX_FEATURED }).map((_, i) => (
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

export default function FeaturedEventsSection() {
  const { events, loading, error } = useFeaturedEvents()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  if (loading) return (
    <SectionWrapper id="events">
      <SectionHeader heading="xl" title="À la une" />
      <Skeleton name="event-cards" loading animate="pulse" color={skeletonColor}>
        <FeaturedEventsFixture />
      </Skeleton>
    </SectionWrapper>
  )

  if (error) return (
    <SectionWrapper id="events">
      <SectionHeader heading="xl" title="À la une" />
      <InfoMessage type="error" message={error} />
    </SectionWrapper>
  )

  if (events.length === 0) return null

  return (
    <SectionWrapper id="events">
      <SectionHeader heading="xl" title="À la une" />
      <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
        {events.slice(0, MAX_FEATURED).map(event => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>
    </SectionWrapper>
  )
}
