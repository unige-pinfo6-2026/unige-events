import { BlobsSubtle } from '@/components/utils/Blobs'
import { EVENT_CATEGORIES } from '@/types/event'
import EventCalendar from '@/components/calendar/EventCalendar'

export default function CalendarPage() {
  return (
    <div>
      {/* Header */}
      <div className="relative overflow-hidden py-12 lg:py-16">
        <BlobsSubtle />
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          <div className="flex flex-col lg:flex-row lg:items-end lg:justify-between gap-8">
            <div>
              <h1 className="text-5xl lg:text-7xl font-bold tracking-tight leading-[0.95]">
                Calendrier{' '}
                <span className="text-accent-gradient">du campus</span>
              </h1>
              <p className="mt-4 text-xl text-foreground/60 font-light leading-relaxed max-w-xl">
                Visualisez et explorez tous les événements de la communauté.
              </p>
            </div>

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
        </div>
      </div>

      {/* Calendar */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-20">
        <EventCalendar />
      </div>
    </div>
  )
}
