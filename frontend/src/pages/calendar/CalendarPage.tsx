import { useCallback, useState } from 'react'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
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
        <SectionHeader
          align="left"
          title={<>Calendrier <mark>du campus</mark></>}
          subtitle="Visualisez et explorez tous les événements de la communauté."
        />

        <CategoryFilterBar
          disabled={disabledCategories}
          onToggle={handleToggleCategory}
        />

        <div className="flex flex-col h-[680px]">
          <EventCalendar disabledCategories={disabledCategories} />
        </div>
      </SectionWrapper>

      {/* Subscription block — only for authenticated callers ; the backend
          endpoints (/users/me/calendar-token*) are @Authenticated. */}
      {user && (
        <SectionWrapper padding="sm">
          <CalendarSubscribeButton />
        </SectionWrapper>
      )}
    </>
  )
}
