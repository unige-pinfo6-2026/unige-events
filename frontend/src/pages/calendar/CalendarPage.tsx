import { useState, useCallback } from 'react'
import type { View } from 'react-big-calendar'
import { useNavigate } from 'react-router-dom'
import { Calendar, dateFnsLocalizer, Views } from 'react-big-calendar'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { fr } from 'date-fns/locale'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { useCalendarEvents, type CalendarEvent } from '@/hooks/useCalendarEvents'
import { EVENT_CATEGORIES } from '@/types/event'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'

const locales = { fr }

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek: (date: Date) => startOfWeek(date, { weekStartsOn: 1 }),
  getDay,
  locales,
})

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

export default function CalendarPage() {
  const navigate = useNavigate()
  const [currentDate, setCurrentDate] = useState(new Date())
  const [currentView, setCurrentView] = useState<View>(Views.MONTH)
  const { events, loading, error } = useCalendarEvents(currentDate)

  const eventPropGetter = useCallback((event: CalendarEvent) => {
    const category = EVENT_CATEGORIES[event.resource.category ?? "OTHER"]
    return { style: { backgroundColor: category.color, borderColor: category.color, color: '#fff' } }
  }, [])

  const handleSelectEvent = useCallback(
    (event: CalendarEvent) => navigate(`/events/${event.resource.id}`),
    [navigate],
  )

  if(loading) return <LoadingSpinner/>

  if(error) return <InfoMessage type='error' message={error}/>

  return (
    <div className="max-w-6xl mx-auto">
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
    </div>
  )
}
