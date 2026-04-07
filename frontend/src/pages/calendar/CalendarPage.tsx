import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { EVENT_CATEGORIES } from '@/types/event'
import EventCalendar from '@/components/calendar/EventCalendar'

export default function CalendarPage() {
  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <div className="flex flex-col lg:flex-row lg:justify-between gap-8">
        <SectionHeader
          align="left"
          title={<>Calendrier <mark>du campus</mark></>}
          subtitle="Visualisez et explorez tous les événements de la communauté."
        />

        {/* Category legend */}
        <div className="flex flex-wrap gap-2 lg:max-w-xs">
          {Object.entries(EVENT_CATEGORIES).map(([key, cat]) => (
            <div
            key={key}
            className="flex items-center gap-2 bg-background/60 backdrop-blur-sm border border-border rounded-full px-3 py-1.5"
            >
            <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: cat.color }} />
            <span className="text-sm text-foreground/70 font-medium">{cat.name}</span>
            </div>
          ))}
        </div>
      </div>
      
      <EventCalendar />
    </SectionWrapper>
  )
}
