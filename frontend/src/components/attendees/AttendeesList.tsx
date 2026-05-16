import { useMemo, useState } from 'react'
import { type UseAttendeesResult } from '@/hooks/useAttendees'
import type { Attendance, AttendanceStatus } from '@/types/attendance'
import AttendeeCard from './AttendeeCard'

interface AttendeesListProps {
  /** Unauthenticated viewers see only the compact avatar stack + count. */
  isAuthenticated: boolean
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
  { key: 'ATTENDING' as const, label: 'Participants' },
  { key: 'WAITLISTED' as const, label: "Liste d'attente" },
]

const emptyMessages: Record<AttendanceStatus, string> = {
  ATTENDING: 'Aucun participant pour le moment.',
  WAITLISTED: 'Personne en liste d\'attente.',
}

// Compact = unauthenticated summary (single inline row). Full = authenticated.
const sectionVariants = {
  compact: 'bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl px-6 py-4 border border-border',
  full:    'bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border',
} as const

const headingClass = 'text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3'

function buildSummaryLabel(attendingCount: number): string {
  if (attendingCount === 0) return 'Aucun participant pour le moment.'
  const noun = attendingCount === 1 ? 'participant' : 'participants'
  return `${attendingCount} ${noun}`
}

function CompactSummary({ attendingCount }: Readonly<{ attendingCount: number }>) {
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

function AuthenticatedView({ attendeesHook }: Readonly<{ attendeesHook: UseAttendeesResult }>) {
  const { attendees, isLoading, error, hasMore, loadMore, refetch } = attendeesHook
  const [activeTab, setActiveTab] = useState<AttendanceStatus>('ATTENDING')

  const counts = useMemo(() => {
    return attendees.reduce<Record<AttendanceStatus, number>>(
      (acc, a) => {
        acc[a.status] += 1
        return acc
      },
      { ATTENDING: 0, WAITLISTED: 0 },
    )
  }, [attendees])

  const filtered = useMemo<Attendance[]>(
    () => attendees.filter((a) => a.status === activeTab),
    [attendees, activeTab],
  )

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
          {filtered.map((attendance) => (
            <AttendeeCard key={attendance.id} attendance={attendance} />
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
  isAuthenticated,
  attendingCount,
  attendeesHook,
}: Readonly<AttendeesListProps>) {
  // Compact for unauthenticated (only the avatar stack + count). Full list for
  // every authenticated caller — the backend privacy filter handles
  // anonymization of private profiles inside the response (SCRUM-S7).
  const variant = isAuthenticated ? 'full' : 'compact'
  return (
    <section className={sectionVariants[variant]}>
      <h2 className={headingClass}>Participants</h2>
      {isAuthenticated ? (
        <AuthenticatedView attendeesHook={attendeesHook} />
      ) : (
        <CompactSummary attendingCount={attendingCount} />
      )}
    </section>
  )
}
