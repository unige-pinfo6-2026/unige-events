import { useState, useCallback } from 'react'
import type { View } from 'react-big-calendar'
import { useNavigate } from 'react-router-dom'
import { Calendar, dateFnsLocalizer, Views } from 'react-big-calendar'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { fr } from 'date-fns/locale'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { useCalendarEvents, type CalendarEvent } from '../hooks/useCalendarEvents'

const locales = { fr }

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek: (date: Date) => startOfWeek(date, { weekStartsOn: 1 }),
  getDay,
  locales,
})

const CATEGORY_COLORS: Record<string, string> = {
  ACADEMIC: '#3b82f6',
  SPORTS: '#22c55e',
  CULTURAL: '#a855f7',
  SOCIAL: '#fb923c',
  CONFERENCE: '#14b8a6',
  OTHER: '#6b7280',
}

const MESSAGES = {
  today: "Aujourd'hui",
  previous: '‹ Précédent',
  next: 'Suivant ›',
  month: 'Mois',
  week: 'Semaine',
  day: 'Jour',
  agenda: 'Agenda',
  date: 'Date',
  time: 'Heure',
  event: 'Événement',
  noEventsInRange: 'Aucun événement sur cette période.',
}

export default function CalendarPage() {
  const navigate = useNavigate()
  const [currentDate, setCurrentDate] = useState(new Date())
  const [currentView, setCurrentView] = useState<View>(Views.MONTH)
  const { events, loading, error } = useCalendarEvents(currentDate)

  const eventPropGetter = useCallback((event: CalendarEvent) => {
    const color = CATEGORY_COLORS[event.resource.category] ?? CATEGORY_COLORS.OTHER
    return { style: { backgroundColor: color, borderColor: color, color: '#fff' } }
  }, [])

  const handleSelectEvent = useCallback(
    (event: CalendarEvent) => navigate(`/events/${event.resource.id}`),
    [navigate],
  )

  return (
    <div className="max-w-6xl mx-auto px-4 py-6">
      {loading && (
        <div className="flex justify-center py-16">
          <div className="spinner" />
        </div>
      )}
      {!loading && error && (
        <div className="rounded-xl border border-red-200 bg-red-50 dark:bg-red-900/20 dark:border-red-800 p-4 text-center text-red-600 dark:text-red-400 text-sm">
          {error}
        </div>
      )}
      {!loading && !error && (
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
        />
      )}
    </div>
  )
}
