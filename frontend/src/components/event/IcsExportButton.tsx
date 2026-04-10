import { Calendar, Download, ExternalLink } from 'lucide-react'
import type { Event } from '@/types/event'
import { buildGoogleCalendarUrl, generateIcs } from '@/utils/icsGenerator'

interface IcsExportButtonProps {
  event: Event
}

export default function IcsExportButton({ event }: Readonly<IcsExportButtonProps>) {
  function handleDownload() {
    const content = generateIcs(event)
    const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `event-${event.id}.ics`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const googleCalendarUrl = buildGoogleCalendarUrl(event)

  return (
    <div className="bg-background border border-border rounded-3xl p-6 flex flex-col gap-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Calendar className="w-4 h-4 text-foreground/50" />
        Ajouter au calendrier
      </div>
      <p className="text-sm text-foreground/50 leading-relaxed">
        Compatible avec Apple Calendar (double-cliquez le fichier), Outlook et autres applications calendrier.
      </p>
      <div className="flex gap-4 items-start">
        <button
          type="button"
          onClick={handleDownload}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold bg-transparent hover:border-foreground/30 hover:text-foreground transition-colors cursor-pointer"
        >
          <Download className="w-4 h-4" />
          Télécharger .ics
        </button>
        <a
          href={googleCalendarUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold no-underline hover:border-accent/50 hover:text-accent transition-colors"
        >
          <ExternalLink className="w-4 h-4" />
          Google Calendar
        </a>
      </div>
    </div>
  )
}
