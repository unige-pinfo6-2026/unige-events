import { Inbox } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'

/**
 * Compact badge for the navbar dropdown — shows the count of PENDING
 * co-organizer invitations and links to `/profile/me` where the full list
 * lives. Hidden when count is zero (no visual noise for users without
 * invitations).
 */
export default function CoOrganizerInvitationsBadge() {
  const { pendingCount, loading } = useCoOrganizerInvitations()

  if (loading || pendingCount === 0) return null

  return (
    <Link
      to="/profile/me"
      aria-label={`${pendingCount} invitation${pendingCount > 1 ? 's' : ''} en attente`}
      className="relative inline-flex items-center gap-2 px-3 py-2 rounded-xl text-sm font-medium text-foreground hover:bg-foreground/5 transition-colors"
    >
      <Inbox className="w-4 h-4 text-accent" />
      <span>Invitations</span>
      <span className="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full bg-accent text-white text-xs font-bold">
        {pendingCount}
      </span>
    </Link>
  )
}
