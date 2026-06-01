import { useState } from 'react'
import { Skeleton } from 'boneyard-js/react'
import { Link } from 'react-router-dom'
import { Ban, Search, Star, Trash2, XCircle } from 'lucide-react'
import { useTheme } from '@/contexts/ThemeContext'
import { useAdminReports } from '@/hooks/useAdminReports'
import { useAdminFeatured } from '@/hooks/useAdminFeatured'
import { SectionHeader, SectionWrapper } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ButtonDestructive, ButtonNeutral } from '@/components/utils/Buttons'
import { formatEventDateTime, parseApiUtcDateTime } from '@/utils/dateTime'
import { REPORT_REASONS } from '@/types/report'
import type { Report } from '@/types/admin'
import type { Event } from '@/types/event'

// ─── Fixtures ────────────────────────────────────────────────────────────────

function AdminReportsFixture() {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-2">
        <div className="h-9 w-28 rounded-xl" />
        <div className="h-9 w-24 rounded-xl" />
      </div>
      <div className="rounded-2xl border border-border overflow-hidden">
        <div className="h-11" />
        {Array.from({length: 5}).map((_, i) => (
          <div key={`skeleton-report-${i}`} className="h-[68px]" />
        ))}
      </div>
    </div>
  )
}

function AdminFeaturedFixture() {
  return (
    <div className="flex flex-col gap-4">
      <div className="h-11 rounded-xl" />
      <div className="flex flex-col gap-3">
        {Array.from({length: 3}).map((_, i) => (
          <div key={`skeleton-featured-${i}`} className="h-[72px] rounded-2xl" />
        ))}
      </div>
    </div>
  )
}

// ─── Report row ───────────────────────────────────────────────────────────────

// Per-target labels for the validation action (bug ③) — validating an EVENT
// report bans the event, a COMMENT report deletes the comment. Typed const map
// rather than inline ternaries (cf. frontend/AGENTS.md).
const reviewAction = {
  EVENT: {
    icon: Ban,
    label: "Bannir l'événement",
    title: "Confirme le signalement et bannit l'événement (action destructive : irréversible côté créateur).",
    reviewedLabel: 'Banni',
  },
  COMMENT: {
    icon: Trash2,
    label: 'Supprimer le commentaire',
    title: 'Confirme le signalement et supprime le commentaire (action destructive : irréversible).',
    reviewedLabel: 'Supprimé',
  },
} as const

/** Target cell — event title (deep-linked) or reported comment body. */
function ReportTargetCell({ report }: Readonly<{ report: Report }>) {
  if (report.targetType === 'COMMENT') {
    const body = report.commentContent?.trim()
    return body
      ? <span className="block text-sm text-foreground/80 italic line-clamp-2">« {body} »</span>
      : <span className="block text-sm text-foreground/40 italic line-clamp-1">Commentaire supprimé</span>
  }
  const eventTitle = report.eventTitle ?? 'Événement supprimé'
  return report.eventId === null
    ? <span className="font-medium text-foreground/40 text-sm line-clamp-1">{eventTitle}</span>
    : (
      <Link
        to={`/events/${report.eventId}`}
        className="font-medium text-foreground hover:text-accent transition-colors text-sm line-clamp-1"
      >
        {eventTitle}
      </Link>
    )
}

function ReportRow({
  report,
  onReview,
  onDismiss,
}: Readonly<{ report: Report; onReview: (id: number) => void; onDismiss: (id: number) => void }>) {
  const reporterLabel = report.reporterDisplayName ?? 'Compte supprimé'
  const reasonLabel = REPORT_REASONS[report.reason]
  const description = report.description?.trim()
  const action = reviewAction[report.targetType]
  const ReviewIcon = action.icon

  return (
    <tr className="border-t border-border hover:bg-foreground/2 transition-colors align-top">
      <td className="px-4 py-4 max-w-[280px]">
        <ReportTargetCell report={report} />
      </td>
      <td className="px-4 py-4 text-sm max-w-[280px]">
        <span className="block font-medium text-foreground/80">{reasonLabel}</span>
        {description && (
          <span className="block mt-1 text-xs text-foreground/50 line-clamp-2">{description}</span>
        )}
      </td>
      <td className="px-4 py-4 text-sm text-foreground/60 whitespace-nowrap">
        {reporterLabel}
      </td>
      <td className="px-4 py-4 text-sm text-foreground/50 whitespace-nowrap">
        {parseApiUtcDateTime(report.createdAt).toLocaleDateString('fr-FR')}
      </td>
      <td className="px-4 py-4">
        {report.status === 'PENDING' ? (
          <div className="flex items-center gap-2 justify-end">
            <button
              type="button"
              onClick={() => onReview(report.id)}
              title={action.title}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-error/10 text-error hover:bg-error/20 transition-colors cursor-pointer border-0"
            >
              <ReviewIcon className="size-3.5 shrink-0" />
              {action.label}
            </button>
            <button
              type="button"
              onClick={() => onDismiss(report.id)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-foreground/5 text-foreground/60 hover:bg-foreground/10 transition-colors cursor-pointer border-0"
            >
              <XCircle className="size-3.5 shrink-0" />
              Ignorer
            </button>
          </div>
        ) : (
          <div className="flex justify-end">
            <span className={`inline-flex items-center px-2.5 py-1 text-xs font-medium rounded-lg ${
              report.status === 'REVIEWED'
                ? 'bg-error/10 text-error'
                : 'bg-foreground/5 text-foreground/50'
            }`}>
              {report.status === 'REVIEWED' ? action.reviewedLabel : 'Ignoré'}
            </span>
          </div>
        )}
      </td>
    </tr>
  )
}

// ─── Reports section ──────────────────────────────────────────────────────────

// Target-type filter on top of the status tabs — lets an admin focus on event
// vs comment reports, with a pending count per type.
const TARGET_FILTERS = [
  { key: 'ALL', label: 'Tous' },
  { key: 'EVENT', label: 'Événements' },
  { key: 'COMMENT', label: 'Commentaires' },
] as const
type TargetFilter = (typeof TARGET_FILTERS)[number]['key']

function AdminReportsSection() {
  const { reports, loading, error, activeTab, setActiveTab, reviewReport, dismissReport } = useAdminReports()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const [typeFilter, setTypeFilter] = useState<TargetFilter>('ALL')

  // Counts reflect the current status tab (e.g. how many comment vs event
  // reports are pending). The filter then narrows the table to one target type.
  const targetCounts = {
    ALL: reports.length,
    EVENT: reports.filter(r => r.targetType === 'EVENT').length,
    COMMENT: reports.filter(r => r.targetType === 'COMMENT').length,
  }
  const visibleReports = typeFilter === 'ALL'
    ? reports
    : reports.filter(r => r.targetType === typeFilter)

  if (loading) {
    return (
      <Skeleton name="admin-reports" loading animate="pulse" color={skeletonColor}>
        <AdminReportsFixture />
      </Skeleton>
    )
  }

  if (error) return <InfoMessage type="error" message={error} />

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('PENDING')}
          className={`h-9 px-4 rounded-xl text-sm font-medium transition-colors cursor-pointer border-0 ${
            activeTab === 'PENDING'
              ? 'bg-accent text-white'
              : 'bg-foreground/5 text-foreground/60 hover:text-foreground hover:bg-foreground/10'
          }`}
        >
          En attente
          {activeTab === 'PENDING' && reports.length > 0 && (
            <span className="ml-1.5 bg-white/20 text-white text-xs rounded-full px-1.5 py-0.5">
              {reports.length}
            </span>
          )}
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('PROCESSED')}
          className={`h-9 px-4 rounded-xl text-sm font-medium transition-colors cursor-pointer border-0 ${
            activeTab === 'PROCESSED'
              ? 'bg-accent text-white'
              : 'bg-foreground/5 text-foreground/60 hover:text-foreground hover:bg-foreground/10'
          }`}
        >
          Traités
        </button>
      </div>

      {reports.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {TARGET_FILTERS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => setTypeFilter(key)}
              aria-pressed={typeFilter === key}
              className={`inline-flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-medium transition-colors cursor-pointer border bg-transparent ${
                typeFilter === key
                  ? 'border-accent/40 bg-accent/10 text-foreground'
                  : 'border-border text-foreground/50 hover:text-foreground hover:border-foreground/30'
              }`}
            >
              {label}
              <span className="rounded-full bg-foreground/10 text-foreground/60 px-1.5 py-0.5 text-[11px] leading-none">
                {targetCounts[key]}
              </span>
            </button>
          ))}
        </div>
      )}

      {visibleReports.length === 0 ? (
        <div className="py-12 text-center rounded-2xl border border-border text-foreground/40 text-sm">
          {reports.length === 0
            ? (activeTab === 'PENDING' ? 'Aucun signalement en attente.' : 'Aucun signalement traité.')
            : 'Aucun signalement de ce type.'}
        </div>
      ) : (
        <div className="rounded-2xl border border-border overflow-hidden overflow-x-auto">
          <table className="w-full min-w-[600px]">
            <thead>
              <tr className="bg-foreground/3">
                <th className="px-4 py-3 text-left text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Cible du signalement
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Raison
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Signalé par
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Date
                </th>
                <th className="px-4 py-3 text-right text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {visibleReports.map(report => (
                <ReportRow
                  key={report.id}
                  report={report}
                  onReview={reviewReport}
                  onDismiss={dismissReport}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

// ─── Featured event card ──────────────────────────────────────────────────────

function FeaturedEventCard({
  event,
  onUnfeature,
  actionLabel = 'Retirer',
  variant = 'remove',
}: Readonly<{
  event: Event
  onUnfeature: (id: number) => void
  actionLabel?: string
  variant?: 'remove' | 'add'
}>) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-2xl border border-border bg-background/50 hover:bg-background/80 transition-colors">
      {event.bannerUrl ? (
        <img
          src={event.bannerUrl}
          alt=""
          className="size-10 rounded-lg object-cover shrink-0"
        />
      ) : (
        <div className="size-10 rounded-lg bg-foreground/10 flex items-center justify-center shrink-0">
          <Star className="size-4 text-foreground/30" />
        </div>
      )}

      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">{event.title}</p>
        <p className="text-xs text-foreground/50 truncate">
          {formatEventDateTime(event.startDate, event.allDay)}
        </p>
      </div>

      {variant === 'remove' ? (
        <ButtonDestructive size="sm" onClick={() => onUnfeature(event.id)}>
          {actionLabel}
        </ButtonDestructive>
      ) : (
        <ButtonNeutral size="sm" onClick={() => onUnfeature(event.id)}>
          {actionLabel}
        </ButtonNeutral>
      )}
    </div>
  )
}

// ─── Featured section ─────────────────────────────────────────────────────────

function AdminFeaturedSection() {
  const {
    featuredEvents,
    loading,
    error,
    searchQuery,
    setSearchQuery,
    searchResults,
    searchLoading,
    featureEvent,
    unfeatureEvent,
  } = useAdminFeatured()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  if (loading) {
    return (
      <Skeleton name="admin-featured" loading animate="pulse" color={skeletonColor}>
        <AdminFeaturedFixture />
      </Skeleton>
    )
  }

  if (error) return <InfoMessage type="error" message={error} />

  const featuredIds = new Set(featuredEvents.map(e => e.id))
  const filteredSearchResults = searchResults.filter(e => !featuredIds.has(e.id))

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <p className="text-sm font-medium text-foreground/60">
          Rechercher un événement à mettre en avant
        </p>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-foreground/40 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Titre de l'événement…"
            className="w-full h-11 pl-10 pr-4 rounded-xl border border-border bg-background text-sm text-foreground placeholder:text-foreground/40 focus:outline-none focus:ring-2 focus:ring-accent/50 focus:border-accent transition-colors"
          />
        </div>

        {searchQuery.trim() && (
          <div className="flex flex-col gap-2">
            {searchLoading && (
              <p className="text-xs text-foreground/40 px-1">Recherche en cours…</p>
            )}
            {!searchLoading && filteredSearchResults.length === 0 && searchQuery.trim() && (
              <p className="text-xs text-foreground/40 px-1">Aucun résultat.</p>
            )}
            {filteredSearchResults.map(event => (
              <FeaturedEventCard
                key={event.id}
                event={event}
                onUnfeature={featureEvent}
                actionLabel="Mettre en avant"
                variant="add"
              />
            ))}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3">
        <p className="text-sm font-medium text-foreground/60">
          Événements actuellement mis en avant
          {featuredEvents.length > 0 && (
            <span className="ml-1.5 text-xs bg-accent/10 text-accent px-1.5 py-0.5 rounded-full">
              {featuredEvents.length}
            </span>
          )}
        </p>

        {featuredEvents.length === 0 ? (
          <div className="py-10 text-center rounded-2xl border border-border text-foreground/40 text-sm">
            Aucun événement mis en avant.
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {featuredEvents.map(event => (
              <FeaturedEventCard
                key={event.id}
                event={event}
                onUnfeature={unfeatureEvent}
                actionLabel="Retirer"
                variant="remove"
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AdminPage() {
  return (
    <SectionWrapper padding="sm">
      <SectionHeader
        title={<>Espace <mark>Administration</mark></>}
        subtitle="Modération des signalements et gestion des événements mis en avant"
        align="center"
      />

      <div className="flex flex-col gap-12">
        <section className="flex flex-col gap-4">
          <h2 className="text-xl font-semibold text-foreground">Modération des signalements</h2>
          <AdminReportsSection />
        </section>

        <section className="flex flex-col gap-4">
          <h2 className="text-xl font-semibold text-foreground">Événements mis en avant</h2>
          <AdminFeaturedSection />
        </section>
      </div>
    </SectionWrapper>
  )
}
