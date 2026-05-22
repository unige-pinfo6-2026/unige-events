import { Ticket } from 'lucide-react'
import ProfilePreviewSection from './ProfilePreviewSection'
import type { Event } from '@/types/event'

interface ProfileParticipationsProps {
  events: Event[]
  loading: boolean
  error: string | null
}

/**
 * Section "Participations publiques" on a public profile page (SCRUM-141
 * follow-up). Lists the PUBLISHED events the profile owner is registered to
 * (ATTENDING), fetched via `GET /users/{id}/participations` (privacy-gated
 * server-side), rendered through the shared {@link ProfilePreviewSection} shell.
 */
export default function ProfileParticipations({ events, loading, error }: Readonly<ProfileParticipationsProps>) {
  return (
    <ProfilePreviewSection
      headingId="profile-participations-heading"
      title="Participations publiques"
      events={events}
      loading={loading}
      error={error}
      emptyIcon={Ticket}
      emptyMessage="Aucune participation publique pour le moment."
    />
  )
}
