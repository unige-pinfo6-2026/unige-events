import { useState } from 'react'
import { Calendar, Hourglass, type LucideIcon } from 'lucide-react'
import EventCard from '@/components/event/EventCard'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'
import { useMyParticipations } from '@/hooks/useMyParticipations'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import type { Event } from '@/types/event'
import { EventGridFixture } from './shared'

// ─── Const map des sous-tabs ──────────────────────────────────────────────────

const PARTICIPATION_TABS = {
  ATTENDING:  { label: "J'y participe",   emptyTitle: 'Vous ne participez à aucun événement pour le moment.' },
  WAITLISTED: { label: "Liste d'attente", emptyTitle: "Vous n'êtes sur aucune liste d'attente." },
} as const

type ParticipationTab = keyof typeof PARTICIPATION_TABS

const TAB_KEYS = Object.keys(PARTICIPATION_TABS) as ParticipationTab[]

// ─── Sous-tabs ────────────────────────────────────────────────────────────────

interface ParticipationTabsProps {
  active: ParticipationTab
  counts: Record<ParticipationTab, number>
  onChange: (tab: ParticipationTab) => void
}

function ParticipationTabs({ active, counts, onChange }: Readonly<ParticipationTabsProps>) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      {TAB_KEYS.map(key => {
        const { label } = PARTICIPATION_TABS[key]
        const isActive = key === active
        const base = 'px-4 py-1.5 rounded-full text-xs font-semibold border cursor-pointer bg-transparent transition-colors'
        const state = isActive
          ? 'border-accent text-accent bg-accent/10'
          : 'border-border text-foreground/60 hover:text-foreground hover:border-foreground/30'
        return (
          <button key={key} type="button" onClick={() => onChange(key)} className={`${base} ${state}`}>
            {label} ({counts[key]})
          </button>
        )
      })}
    </div>
  )
}

// ─── Empty state ──────────────────────────────────────────────────────────────

function EmptyState({ icon: Icon, title }: Readonly<{ icon: LucideIcon; title: string }>) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <Icon className="w-16 h-16 text-foreground/20" />
      <p className="text-foreground/50 text-lg font-medium">{title}</p>
    </div>
  )
}

// ─── Grille de cards (pas d'actions organisateur) ─────────────────────────────

function ParticipationGrid({ events, favoritedIds }: Readonly<{ events: Event[]; favoritedIds: Set<number> }>) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {events.map(event => (
        <EventCard key={event.id} event={event} favorited={favoritedIds.has(event.id)} />
      ))}
    </div>
  )
}

// ─── Page principale ──────────────────────────────────────────────────────────

export default function MyParticipationsPage() {
  const { attending, waitlisted, loading, error } = useMyParticipations()
  const { favoritedIds } = useFavoritesContext()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [active, setActive] = useState<ParticipationTab>('ATTENDING')
  const events = active === 'ATTENDING' ? attending : waitlisted
  const counts: Record<ParticipationTab, number> = {
    ATTENDING:  attending.length,
    WAITLISTED: waitlisted.length,
  }
  const tabIcon: Record<ParticipationTab, LucideIcon> = {
    ATTENDING:  Calendar,
    WAITLISTED: Hourglass,
  }

  return (
    <SectionWrapper padding="sm" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        title={<>Mes <mark>Participations</mark></>}
        subtitle="Tous les événements auxquels vous êtes inscrit(e)."
      />

      <ParticipationTabs active={active} counts={counts} onChange={setActive} />

      {loading && (
        <Skeleton name="event-cards" loading animate="pulse" color={skeletonColor}>
          <EventGridFixture />
        </Skeleton>
      )}
      {!loading && error && <InfoMessage type="error" message={error} />}
      {!loading && !error && events.length === 0 && (
        <EmptyState icon={tabIcon[active]} title={PARTICIPATION_TABS[active].emptyTitle} />
      )}
      {!loading && !error && events.length > 0 && (
        <ParticipationGrid events={events} favoritedIds={favoritedIds} />
      )}
    </SectionWrapper>
  )
}
