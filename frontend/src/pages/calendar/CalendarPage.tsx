import { useCallback, useState } from 'react'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionWrapper } from '@/components/utils/Section'
import { useAuth } from '@/hooks/useAuth'
import EventCalendar from '@/components/calendar/EventCalendar'
import CategoryFilterBar from '@/components/calendar/CategoryFilterBar'
import CalendarSubscribeButton from '@/components/calendar/CalendarSubscribeButton'
import type { EventCategory } from '@/types/event'

export default function CalendarPage() {
  const { user } = useAuth()

  // Categories the user has masked via CategoryFilterBar. Empty Set = show all.
  // Ephemeral state — not persisted across reloads (per spec decision Q4).
  const [disabledCategories, setDisabledCategories] = useState<ReadonlySet<EventCategory>>(
    () => new Set<EventCategory>(),
  )
  const [subscribeOpen, setSubscribeOpen] = useState(false)

  const handleToggleCategory = useCallback((category: EventCategory) => {
    setDisabledCategories((prev) => {
      const next = new Set(prev)
      if (next.has(category)) next.delete(category)
      else next.add(category)
      return next
    })
  }, [])

  return (
    <>
      <SectionWrapper padding="sm" background={<BlobsSubtle />}>
        {/* Title row: heading + optional "S'abonner" button for logged-in users */}
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex flex-col gap-6 text-left">
            <h2 className="text-3xl lg:text-6xl font-bold tracking-tight">
              Calendrier <mark>du campus</mark>
            </h2>
            <p className="text-base lg:text-lg text-foreground/60 font-light max-w-3xl">
              Visualisez et explorez tous les événements de la communauté.
            </p>
          </div>
          {user && (
            <button
              type="button"
              onClick={() => setSubscribeOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border-2 border-accent/40 bg-accent/5 text-accent text-sm font-semibold hover:border-accent hover:bg-accent/10 transition-all shrink-0 cursor-pointer self-start mt-2"
            >
              S&apos;abonner
            </button>
          )}
        </div>

        <CategoryFilterBar
          disabled={disabledCategories}
          onToggle={handleToggleCategory}
        />

        <div className="flex flex-col h-[680px]">
          <EventCalendar disabledCategories={disabledCategories} />
        </div>
      </SectionWrapper>

      {/* Calendar subscription modal */}
      {subscribeOpen && (
        <>
          {/* Backdrop — native button so keyboard/click both work */}
          <button
            type="button"
            aria-label="Fermer"
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 cursor-default"
            onClick={() => setSubscribeOpen(false)}
          />
          {/* Dialog panel — pointer-events-none on wrapper lets the backdrop catch stray clicks */}
          <div
            role="dialog"
            aria-modal="true"
            aria-label="S'abonner au calendrier"
            className="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
          >
            <div className="w-full max-w-lg pointer-events-auto">
              <CalendarSubscribeButton />
            </div>
          </div>
        </>
      )}
    </>
  )
}
