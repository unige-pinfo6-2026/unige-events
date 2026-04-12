import { useState, useCallback } from 'react'
import type { View, ToolbarProps } from 'react-big-calendar'
import { useNavigate } from 'react-router-dom'
import { Calendar, dateFnsLocalizer, Views } from 'react-big-calendar'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { fr } from 'date-fns/locale'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { Skeleton } from 'boneyard-js/react'
import { useCalendarEvents, type CalendarEvent } from '@/hooks/useCalendarEvents'
import { useTheme } from '@/contexts/ThemeContext'
import { EVENT_CATEGORIES } from '@/types/event'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ChevronLeft, ChevronRight } from 'lucide-react'

// Cells (row*7+col) that show 1 event bar, and those that show 2
const CALENDAR_EVENTS_1 = new Set([1, 3, 8, 11, 13, 18, 22, 25, 27, 31])
const CALENDAR_EVENTS_2 = new Set([4, 9, 16, 23, 29])

function EventCalendarFixture() {
  return (
    <div className="h-full bg-background rounded-3xl border border-border overflow-hidden flex flex-col">
      {/* Toolbar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 px-6 py-5 border-b border-border shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-9 h-9 rounded-xl bg-foreground/10" />
          <div className="w-24 h-9 rounded-xl bg-foreground/10" />
          <div className="w-9 h-9 rounded-xl bg-foreground/10" />
          <div className="h-6 w-32 rounded bg-foreground/10 ml-3" />
        </div>
        <div className="flex bg-foreground/5 rounded-2xl p-1 gap-1">
          <div className="w-14 h-9 rounded-xl bg-foreground/10" />
          <div className="w-20 h-9 rounded-xl bg-foreground/10" />
          <div className="w-12 h-9 rounded-xl bg-foreground/10" />
          <div className="w-[72px] h-9 rounded-xl bg-foreground/10" />
        </div>
      </div>
      {/* Day headers */}
      <div className="grid grid-cols-7 border-b border-border shrink-0">
        {Array.from({ length: 7 }).map((_, i) => (
          <div key={i} className="py-3 flex justify-center border-r border-border/50 last:border-r-0">
            <div className="h-4 w-6 rounded bg-foreground/10" />
          </div>
        ))}
      </div>
      {/* Calendar grid: 5 rows × 7 cols, with realistic event bars */}
      <div className="flex-1 grid grid-rows-5">
        {Array.from({ length: 5 }).map((_, row) => (
          <div key={row} className="grid grid-cols-7 border-b border-border/50 last:border-b-0">
            {Array.from({ length: 7 }).map((_, col) => {
              const idx = row * 7 + col
              const has2 = CALENDAR_EVENTS_2.has(idx)
              const has1 = has2 || CALENDAR_EVENTS_1.has(idx)
              return (
                <div key={col} className="border-r border-border/50 last:border-r-0 p-1.5 flex flex-col gap-0.5">
                  <div className="h-5 w-5 rounded-full bg-foreground/10 mb-0.5 shrink-0" />
                  {has1 && <div className="h-[18px] w-full rounded-sm bg-foreground/15 shrink-0" />}
                  {has2 && <div className="h-[18px] w-full rounded-sm bg-foreground/10 shrink-0" />}
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

const locales = { fr }

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek: (date: Date) => startOfWeek(date, { weekStartsOn: 1 }),
  getDay,
  locales,
})

const VIEW_LABELS: Record<string, string> = {
  month: 'Mois',
  week: 'Semaine',
  day: 'Jour',
  agenda: 'Agenda',
}

const MESSAGES = {
  today: "Aujourd'hui",
  previous: 'Précédent',
  next: 'Suivant',
  month: 'Mois',
  week: 'Semaine',
  day: 'Jour',
  agenda: 'Agenda',
  date: 'Date',
  time: 'Heure',
  event: 'Événement',
  noEventsInRange: 'Aucun événement sur cette période.',
}

function CustomToolbar({ label, onNavigate, onView, view, views }: Readonly<ToolbarProps<CalendarEvent>>) {
  const viewList = Array.isArray(views) ? views : (Object.keys(views) as View[])

  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 px-6 py-5 border-b border-border">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onNavigate('PREV')}
          className="p-2 rounded-xl border border-border hover:border-foreground/30 hover:bg-foreground/5 transition-colors cursor-pointer"
          aria-label="Précédent"
        >
          <ChevronLeft className="w-5 h-5 text-foreground/70" />
        </button>
        <button
          type="button"
          onClick={() => onNavigate('TODAY')}
          className="px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground/70 hover:border-foreground/30 hover:bg-foreground/5 transition-colors cursor-pointer"
        >
          Aujourd'hui
        </button>
        <button
          type="button"
          onClick={() => onNavigate('NEXT')}
          className="p-2 rounded-xl border border-border hover:border-foreground/30 hover:bg-foreground/5 transition-colors cursor-pointer"
          aria-label="Suivant"
        >
          <ChevronRight className="w-5 h-5 text-foreground/70" />
        </button>
        <span className="text-xl font-bold capitalize ml-3 text-foreground">{label}</span>
      </div>

      <div className="flex bg-foreground/5 rounded-2xl p-1 gap-1">
        {viewList.map((v) => (
          <button
            key={v}
            type="button"
            onClick={() => onView(v)}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition-colors cursor-pointer ${
              view === v
                ? 'bg-accent text-white shadow-sm'
                : 'text-foreground/60 hover:text-foreground hover:bg-foreground/5'
            }`}
          >
            {VIEW_LABELS[v] ?? v}
          </button>
        ))}
      </div>
    </div>
  )
}

export default function EventCalendar() {
  const navigate = useNavigate()
  const [currentDate, setCurrentDate] = useState(new Date())
  const [currentView, setCurrentView] = useState<View>(Views.MONTH)
  const { events, loading, error } = useCalendarEvents(currentDate)
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.08)'

  const eventPropGetter = useCallback((event: CalendarEvent) => {
    const category = EVENT_CATEGORIES[event.resource.category ?? 'OTHER']
    return { style: { backgroundColor: category.color, borderColor: category.color, color: '#fff' } }
  }, [])

  const handleSelectEvent = useCallback(
    (event: CalendarEvent) => navigate(`/events/${event.resource.id}`),
    [navigate],
  )

  if (loading) return (
    <Skeleton
      name="event-calendar"
      loading={true}
      animate="pulse"
      color={skeletonColor}
      className="h-full"
    ><EventCalendarFixture /></Skeleton>
  )
  if (error) return <InfoMessage type="error" message={error} />

  return (
    <div className="h-full bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border overflow-hidden">
      <Calendar<CalendarEvent> 
        localizer={localizer}
        events={events}
        date={currentDate}
        onNavigate={setCurrentDate}
        view={currentView}
        onView={setCurrentView}
        views={[Views.MONTH, Views.WEEK, Views.DAY, Views.AGENDA]}
        messages={MESSAGES}
        culture="fr"
        eventPropGetter={eventPropGetter}
        onSelectEvent={handleSelectEvent}
        tooltipAccessor={(e) => e.resource.location}
        components={{ toolbar: CustomToolbar }}
      />
    </div>
  )
}
