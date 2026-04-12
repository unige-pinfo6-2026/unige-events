import { Apple, Calendar, Download, ExternalLink, type LucideIcon } from 'lucide-react'
import type { Event } from '@/types/event'
import { buildGoogleCalendarUrl, generateIcs } from '@/utils/icsGenerator'

interface IcsExportButtonProps {
  event: Event
}

const calendarOptionVariants = {
  download: 'hover:border-foreground/30 hover:text-foreground',
  external: 'no-underline hover:border-accent/50 hover:text-accent',
} as const

const buttonBase =
  'inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold bg-transparent transition-colors cursor-pointer'

function CalendarOption({
  icon: Icon,
  label,
  variant,
  onClick,
  href,
}: Readonly<{
  icon: LucideIcon
  label: string
  variant: keyof typeof calendarOptionVariants
  onClick?: () => void
  href?: string
}>) {
  const className = `${buttonBase} ${calendarOptionVariants[variant]}`
  return (
    <div className="flex flex-col gap-1.5">
      {href === undefined ? (
        <button type="button" onClick={onClick} className={className}>
          <Icon className="w-4 h-4" />
          {label}
        </button>
      ) : (
        <a href={href} target="_blank" rel="noopener noreferrer" className={className}>
          <Icon className="w-4 h-4" />
          {label}
        </a>
      )}
    </div>
  )
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
      <div className="flex flex-col gap-4">
        <CalendarOption
          icon={Apple}
          label="Apple Calendar"
          variant="download"
          onClick={handleDownload}
        />
        <CalendarOption
          icon={Download}
          label="Outlook"
          variant="download"
          onClick={handleDownload}
        />
        <CalendarOption
          icon={ExternalLink}
          label="Google Calendar"
          variant="external"
          href={googleCalendarUrl}
        />
      </div>
    </div>
  )
}
