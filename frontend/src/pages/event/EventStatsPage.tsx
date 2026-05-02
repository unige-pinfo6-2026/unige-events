import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth, useEvent } from '@/hooks'
import { useEventStats } from '@/hooks/useEventStats'
import { getEventAttendees } from '@/services/statsApi'
import { getUserById } from '@/services/userService'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import StatsChart from '@/components/event/StatsChart'
import UserAvatar from '@/components/user/UserAvatar'
import type { Attendance } from '@/types/attendance'
import type { User } from '@/types/user'
import type { LucideIcon } from 'lucide-react'
import { ArrowLeft, CheckCircle2, ChevronDown, ChevronUp, Eye, Star, Users } from 'lucide-react'

// ─── Fixture ──────────────────────────────────────────────────────────────────

function EventStatsFixture() {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-3 gap-4 max-sm:grid-cols-1">
        <div className="h-[88px] rounded-2xl" />
        <div className="h-[88px] rounded-2xl" />
        <div className="h-[88px] rounded-2xl" />
      </div>
      <div className="h-[260px] rounded-3xl" />
      <div className="h-[100px] rounded-3xl" />
      <div className="h-12 rounded-2xl" />
    </div>
  )
}

// ─── KPI card ─────────────────────────────────────────────────────────────────

const kpiGradients = {
  green:  'from-emerald-500/20 to-emerald-500/5',
  blue:   'from-blue-500/20 to-blue-500/5',
  purple: 'from-purple-500/20 to-purple-500/5',
} as const

const kpiIconColors = {
  green:  'text-emerald-400',
  blue:   'text-blue-400',
  purple: 'text-purple-400',
} as const

type KpiColor = keyof typeof kpiGradients

interface KpiCardProps {
  icon: LucideIcon
  label: string
  value: number
  color: KpiColor
}

function KpiCard({ icon: Icon, label, value, color }: Readonly<KpiCardProps>) {
  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-2xl p-4 border border-border flex items-center gap-4">
      <div className={`w-10 h-10 rounded-xl bg-linear-to-br ${kpiGradients[color]} flex items-center justify-center shrink-0`}>
        <Icon className={`w-5 h-5 ${kpiIconColors[color]}`} />
      </div>
      <div className="flex flex-col min-w-0">
        <span className="text-xs text-foreground/50 leading-none mb-1">{label}</span>
        <span className="text-2xl font-bold text-foreground leading-none">{value.toLocaleString('fr-CH')}</span>
      </div>
    </div>
  )
}

// ─── Capacity bar ─────────────────────────────────────────────────────────────

interface CapacityBarProps {
  attending: number
  capacity: number
}

function CapacityBar({ attending, capacity }: Readonly<CapacityBarProps>) {
  const pct = capacity > 0 ? Math.min(100, Math.round((attending / capacity) * 100)) : 0

  const fillColor =
    pct >= 90 ? 'bg-error' :
    pct >= 70 ? 'bg-orange-400' :
    'bg-emerald-400'

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-bold uppercase tracking-widest text-foreground/40">
          Taux de remplissage
        </span>
        <span className="text-sm font-bold text-foreground">{pct}%</span>
      </div>
      <div className="h-4 rounded-full bg-foreground/10 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${fillColor}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-xs text-foreground/40">
        {attending.toLocaleString('fr-CH')} / {capacity.toLocaleString('fr-CH')} places
      </span>
    </div>
  )
}

// ─── Attendees section ────────────────────────────────────────────────────────

interface AttendeeRowProps {
  attendance: Attendance
  user: User | null
}

function AttendeeRow({ attendance, user }: Readonly<AttendeeRowProps>) {
  const name = user?.displayName ?? user?.email ?? attendance.userId
  return (
    <div className="flex items-center gap-3 py-2">
      <UserAvatar user={user} className="size-8 shrink-0" />
      <span className="text-sm text-foreground/80 truncate flex-1">{name}</span>
      <span className="text-[10px] font-semibold uppercase tracking-wider text-foreground/30 shrink-0">
        {attendance.status}
      </span>
    </div>
  )
}

interface AttendeesSectionProps {
  eventId: number
}

function AttendeesSection({ eventId }: Readonly<AttendeesSectionProps>) {
  const [open, setOpen] = useState(false)
  const [attendees, setAttendees] = useState<Attendance[]>([])
  const [userMap, setUserMap] = useState<Map<string, User | null>>(new Map())
  const [fetching, setFetching] = useState(false)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [fetched, setFetched] = useState(false)

  async function handleToggle() {
    const next = !open
    setOpen(next)

    if (!next || fetched) return

    setFetching(true)
    try {
      const data = await getEventAttendees(eventId)
      setAttendees(data)

      const map = new Map<string, User | null>()
      await Promise.all(
        data.map(async (a) => {
          try {
            const u = await getUserById(a.userId)
            map.set(a.userId, u)
          } catch {
            map.set(a.userId, null)
          }
        }),
      )
      setUserMap(map)
      setFetched(true)
    } catch {
      setFetchError('Impossible de charger les participants.')
    } finally {
      setFetching(false)
    }
  }

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border overflow-hidden">
      <button
        type="button"
        onClick={handleToggle}
        className="w-full flex items-center gap-3 px-5 py-3.5 text-sm font-semibold text-foreground hover:bg-foreground/5 transition-colors cursor-pointer bg-transparent"
      >
        <Users className="w-4 h-4 shrink-0 text-foreground/50" />
        <span className="flex-1 text-left">Voir les participants</span>
        {open ? <ChevronUp className="w-4 h-4 shrink-0 text-foreground/40" /> : <ChevronDown className="w-4 h-4 shrink-0 text-foreground/40" />}
      </button>

      {open && (
        <div className="border-t border-border px-5 pb-4">
          {fetching && (
            <p className="text-xs text-foreground/40 py-4 text-center">Chargement…</p>
          )}
          {fetchError && (
            <InfoMessage type="error" message={fetchError} />
          )}
          {!fetching && !fetchError && attendees.length === 0 && (
            <p className="text-xs text-foreground/40 py-4 text-center">Aucun participant pour le moment.</p>
          )}
          {!fetching && attendees.map((a) => (
            <AttendeeRow key={a.id} attendance={a} user={userMap.get(a.userId) ?? null} />
          ))}
        </div>
      )}
    </div>
  )
}

// ─── Page principale ───────────────────────────────────────────────────────────

export default function EventStatsPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const { theme } = useTheme()

  const parsedId = id !== undefined ? Number(id) : Number.NaN
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null

  const { event, loading: eventLoading, error: eventError } = useEvent(eventId)

  // Confirm organizer before fetching stats (avoids 403 noise for non-organizers)
  const isConfirmedOrganizer = event !== null && user !== null && user.id === event.creatorId
  const { stats, loading: statsLoading, error: statsError } = useEventStats(
    isConfirmedOrganizer ? eventId : null,
  )

  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  if (eventId === null) return <InfoMessage type="error" message="Identifiant d'événement invalide." />

  const loading = eventLoading || (isConfirmedOrganizer && statsLoading)

  if (loading) {
    return (
      <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
        <SectionHeader
          title={<>Statistiques de <mark>l'événement</mark></>}
          subtitle="Suivi en temps réel"
          align="left"
        />
        <Skeleton name="event-stats" loading animate="pulse" color={skeletonColor}>
          <EventStatsFixture />
        </Skeleton>
      </SectionWrapper>
    )
  }

  if (eventError) return <InfoMessage type="error" message={eventError} />
  if (!event) return <InfoMessage type="error" message="Événement introuvable." />

  if (!isConfirmedOrganizer) {
    return <InfoMessage type="error" message="Accès réservé à l'organisateur de l'événement." />
  }

  if (statsError) return <InfoMessage type="error" message={statsError} />
  if (!stats) return <InfoMessage type="error" message="Statistiques indisponibles." />

  return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>

      {/* Back link */}
      <Link
        to={`/events/${event.id}`}
        className="inline-flex items-center gap-2 text-sm text-foreground/50 hover:text-foreground transition-colors no-underline w-fit -mb-6"
      >
        <ArrowLeft className="w-4 h-4" />
        Retour à l'événement
      </Link>

      <SectionHeader
        title={<>Statistiques de <mark>l'événement</mark></>}
        subtitle={`« ${event.title} »`}
        align="left"
      />

      <div className="flex flex-col gap-6">

        {/* KPI cards */}
        <div className="grid grid-cols-3 gap-4 max-sm:grid-cols-1">
          <KpiCard icon={Eye}           label="Vues totales"  value={stats.viewCount}       color="blue"   />
          <KpiCard icon={Star}          label="Intéressés"    value={stats.interestedCount} color="purple" />
          <KpiCard icon={CheckCircle2}  label="Inscrits"      value={stats.attendingCount}  color="green"  />
        </div>

        {/* Chart */}
        <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border">
          <h3 className="text-xs font-bold uppercase tracking-widest text-foreground/40 mb-4">
            Répartition
          </h3>
          <StatsChart
            views={stats.viewCount}
            attending={stats.attendingCount}
            interested={stats.interestedCount}
          />
        </div>

        {/* Capacity fill rate */}
        {event.capacity != null && event.capacity > 0 && (
          <CapacityBar attending={stats.attendingCount} capacity={event.capacity} />
        )}

        {/* Attendees collapsible */}
        <AttendeesSection eventId={event.id} />

      </div>

    </SectionWrapper>
  )
}
