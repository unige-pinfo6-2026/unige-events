import { Skeleton } from 'boneyard-js/react'
import { CheckCircle, Search, Shield, Star, XCircle } from 'lucide-react'
import { useTheme } from '@/contexts/ThemeContext'
import { useAdminReports } from '@/hooks/useAdminReports'
import { useAdminFeatured } from '@/hooks/useAdminFeatured'
import { SectionHeader, SectionWrapper } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ButtonDestructive, ButtonNeutral } from '@/components/utils/Buttons'
import { formatEventDateTime } from '@/utils/dateTime'
import { REPORT_REASONS } from '@/types/admin'
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

function ReportRow({
  report,
  onReview,
  onDismiss,
}: Readonly<{ report: Report; onReview: (id: number) => void; onDismiss: (id: number) => void }>) {
  const eventTitle = report.eventTitle ?? 'Événement supprimé'
  const reporterLabel = report.reporterDisplayName ?? 'Compte supprimé'
  const reasonLabel = REPORT_REASONS[report.reason].label
  const description = report.description?.trim()

  return (
    <tr className="border-t border-border hover:bg-foreground/2 transition-colors align-top">
      <td className="px-4 py-4">
        {report.eventId !== null ? (
          <a
            href={`/events/${report.eventId}`}
            className="font-medium text-foreground hover:text-accent transition-colors text-sm line-clamp-1"
          >
            {eventTitle}
          </a>
        ) : (
          <span className="font-medium text-foreground/40 text-sm line-clamp-1">{eventTitle}</span>
        )}
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
        {new Date(report.createdAt).toLocaleDateString('fr-FR')}
      </td>
      <td className="px-4 py-4">
        {report.status === 'PENDING' ? (
          <div className="flex items-center gap-2 justify-end">
            <button
              type="button"
              onClick={() => onReview(report.id)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-success/10 text-success hover:bg-success/20 transition-colors cursor-pointer border-0"
            >
              <CheckCircle className="size-3.5 shrink-0" />
              Valider
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
          <span className={`inline-flex items-center px-2.5 py-1 text-xs font-medium rounded-lg ${
            report.status === 'REVIEWED'
              ? 'bg-success/10 text-success'
              : 'bg-foreground/5 text-foreground/50'
          }`}>
            {report.status === 'REVIEWED' ? 'Validé' : 'Ignoré'}
          </span>
        )}
      </td>
    </tr>
  )
}

// ─── Reports section ──────────────────────────────────────────────────────────

function AdminReportsSection() {
  const { reports, loading, error, activeTab, setActiveTab, reviewReport, dismissReport } = useAdminReports()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

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

      {reports.length === 0 ? (
        <div className="py-12 text-center rounded-2xl border border-border text-foreground/40 text-sm">
          {activeTab === 'PENDING' ? 'Aucun signalement en attente.' : 'Aucun signalement traité.'}
        </div>
      ) : (
        <div className="rounded-2xl border border-border overflow-hidden overflow-x-auto">
          <table className="w-full min-w-[600px]">
            <thead>
              <tr className="bg-foreground/3">
                <th className="px-4 py-3 text-left text-xs font-semibold text-foreground/40 uppercase tracking-wider">
                  Événement signalé
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
              {reports.map(report => (
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
        title={
          <span className="flex items-center justify-center gap-3">
            <Shield className="size-8 text-accent" />
            Administration
          </span>
        }
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
