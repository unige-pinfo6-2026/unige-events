import { CalendarOff } from 'lucide-react'
import ProfilePreviewSection from './ProfilePreviewSection'
import type { Event } from '@/types/event'

interface ProfileEventsListProps {
  events: Event[]
  loading: boolean
  error: string | null
  hideHeading?: boolean
}

/**
 * Section "Événements organisés" on a public profile page (SCRUM-141). Lists
 * the published events created by the profile owner, rendered through the
 * shared {@link ProfilePreviewSection} shell.
 */
export default function ProfileEventsList({ events, loading, error, hideHeading }: Readonly<ProfileEventsListProps>) {
  return (
    <ProfilePreviewSection
      headingId="profile-events-heading"
      title="Événements organisés"
      events={events}
      loading={loading}
      error={error}
      emptyIcon={CalendarOff}
      emptyMessage="Aucun événement organisé pour le moment."
      hideHeading={hideHeading}
    />
  )
}
