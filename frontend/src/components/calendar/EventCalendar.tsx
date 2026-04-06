import { useState, useCallback } from 'react'
import type { View, ToolbarProps } from 'react-big-calendar'
import { useNavigate } from 'react-router-dom'
import { Calendar, dateFnsLocalizer, Views } from 'react-big-calendar'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { fr } from 'date-fns/locale'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { useCalendarEvents, type CalendarEvent } from '@/hooks/useCalendarEvents'
import { EVENT_CATEGORIES } from '@/types/event'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { ChevronLeft, ChevronRight } from 'lucide-react'

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

  const eventPropGetter = useCallback((event: CalendarEvent) => {
    const category = EVENT_CATEGORIES[event.resource.category ?? 'OTHER']
    return { style: { backgroundColor: category.color, borderColor: category.color, color: '#fff' } }
  }, [])

  const handleSelectEvent = useCallback(
    (event: CalendarEvent) => navigate(`/events/${event.resource.id}`),
    [navigate],
  )

  if (loading) return <LoadingSpinner />  
  if (error) return <InfoMessage type="error" message={error} />

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border overflow-hidden">
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
        style={{ height: 680 }}
        components={{ toolbar: CustomToolbar }}
      />
    </div>
  )
}
