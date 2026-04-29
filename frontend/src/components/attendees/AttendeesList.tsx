import { useMemo, useState } from 'react'
import { type AttendeeWithProfile, type UseAttendeesResult } from '@/hooks/useAttendees'
import type { AttendanceStatus } from '@/types/attendance'
import AttendeeCard from './AttendeeCard'

interface AttendeesListProps {
  isOrganizer: boolean
  attendingCount: number
  attendeesHook: UseAttendeesResult
}

const tabBase =
  'px-4 py-2 rounded-xl text-sm font-semibold transition-colors cursor-pointer border focus:outline-none focus-visible:ring-2 focus-visible:ring-accent'

const tabVariants = {
  active:   `${tabBase} bg-foreground/10 border-border text-foreground`,
  inactive: `${tabBase} bg-transparent border-transparent text-foreground/50 hover:text-foreground hover:bg-foreground/5`,
} as const

const TABS = [
  { key: 'ATTENDING' as const, label: 'Participent' },
  { key: 'WAITLISTED' as const, label: "Liste d'attente" },
]

const emptyMessages: Record<AttendanceStatus, string> = {
  ATTENDING: 'Aucun participant pour le moment.',
  WAITLISTED: 'Personne en liste d\'attente.',
}

// Compact = non-organizer summary (single inline row). Organizer = full list.
const sectionVariants = {
  compact: 'bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-6 py-4 border border-border',
  full:    'bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border',
} as const

const headingClass = 'text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3'

function buildSummaryLabel(attendingCount: number): string {
  if (attendingCount === 0) return 'Aucun participant pour le moment.'
  const verb = attendingCount === 1 ? 'personne participe' : 'personnes participent'
  return `${attendingCount} ${verb}`
}

function NonOrganizerSummary({ attendingCount }: Readonly<{ attendingCount: number }>) {
  const placeholders = Math.min(5, Math.max(1, attendingCount))
  const label = buildSummaryLabel(attendingCount)
  return (
    <div className="flex items-center gap-3">
      <div className="flex -space-x-2 shrink-0" aria-hidden="true">
        {Array.from({ length: placeholders }, (_, i) => (
          <div
            key={i}
            className="w-7 h-7 rounded-full bg-foreground/10 border-2 border-background"
          />
        ))}
      </div>
      <span className="text-sm text-foreground/60 truncate">{label}</span>
    </div>
  )
}

function LoadingSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true">
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          className="h-16 rounded-2xl bg-foreground/5 border border-border animate-pulse"
        />
      ))}
    </div>
  )
}

interface OrganizerViewProps {
  attendingCount: number
  attendeesHook: UseAttendeesResult
}

function OrganizerView({ attendingCount, attendeesHook }: Readonly<OrganizerViewProps>) {
  const { attendees, isLoading, error, hasMore, loadMore, refetch, isForbidden } = attendeesHook
  const [activeTab, setActiveTab] = useState<AttendanceStatus>('ATTENDING')

  const counts = useMemo(() => {
    return attendees.reduce<Record<AttendanceStatus, number>>(
      (acc, a) => {
        acc[a.attendance.status] += 1
        return acc
      },
      { ATTENDING: 0, WAITLISTED: 0 },
    )
  }, [attendees])

  const filtered = useMemo<AttendeeWithProfile[]>(
    () => attendees.filter((a) => a.attendance.status === activeTab),
    [attendees, activeTab],
  )

  if (isForbidden) {
    return <NonOrganizerSummary attendingCount={attendingCount} />
  }

  if (error) {
    return (
      <div className="flex flex-col gap-3 items-start">
        <p className="text-sm text-error">
          Impossible de charger la liste des participants.
        </p>
        <button
          type="button"
          onClick={refetch}
          className="px-3 py-1.5 rounded-xl border border-border text-sm text-foreground hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
        >
          Réessayer
        </button>
      </div>
    )
  }

  if (isLoading && attendees.length === 0) {
    return <LoadingSkeleton />
  }

  return (
    <div className="flex flex-col gap-4">
      <div role="tablist" aria-label="Filtrer les participants" className="flex flex-wrap gap-2">
        {TABS.map(({ key, label }) => {
          const isActive = activeTab === key
          return (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => setActiveTab(key)}
              className={isActive ? tabVariants.active : tabVariants.inactive}
            >
              {label} ({counts[key]})
            </button>
          )
        })}
      </div>

      {filtered.length === 0 ? (
        <p className="text-sm text-foreground/50">{emptyMessages[activeTab]}</p>
      ) : (
        <div className="flex flex-col gap-3" role="tabpanel">
          {filtered.map(({ attendance, profile }) => (
            <AttendeeCard key={attendance.id} attendance={attendance} profile={profile} />
          ))}
        </div>
      )}

      {hasMore && (
        <button
          type="button"
          onClick={loadMore}
          disabled={isLoading}
          className="self-center mt-2 px-4 py-2 rounded-xl border border-border text-sm font-semibold text-foreground hover:border-foreground/30 transition-colors disabled:opacity-50 cursor-pointer bg-transparent"
        >
          {isLoading ? 'Chargement…' : 'Charger plus'}
        </button>
      )}
    </div>
  )
}

export default function AttendeesList({
  isOrganizer,
  attendingCount,
  attendeesHook,
}: Readonly<AttendeesListProps>) {
  // Compactness derived from isOrganizer: a non-organizer only sees a counter,
  // so the section uses tighter vertical padding to feel like an info row
  // alongside the other narrow-column blocks.
  const variant = isOrganizer ? 'full' : 'compact'
  return (
    <section className={sectionVariants[variant]}>
      <h2 className={headingClass}>Participants</h2>
      {isOrganizer ? (
        <OrganizerView attendingCount={attendingCount} attendeesHook={attendeesHook} />
      ) : (
        <NonOrganizerSummary attendingCount={attendingCount} />
      )}
    </section>
  )
}
