import { Link } from 'react-router-dom'
import { Skeleton } from 'boneyard-js/react'
import { AtSign, Bell, CalendarX2, Clock, Info, MessageSquare, UserCheck, UserPlus, Users } from 'lucide-react'
import { useTheme } from '@/contexts/ThemeContext'
import { NOTIFICATION_TYPES } from '@/types/notification'
import type { Notification, NotificationType } from '@/types/notification'

// ─── Constants ────────────────────────────────────────────────────────────────

interface TypeStyle { icon: typeof Bell; pill: string }

const typeStyles: Record<NotificationType, TypeStyle> = {
  EVENT_UPDATED:   { icon: Info,           pill: 'bg-blue-500/15 text-blue-400' },
  EVENT_CANCELLED: { icon: CalendarX2,     pill: 'bg-error/15 text-error' },
  EVENT_REMINDER:  { icon: Clock,          pill: 'bg-amber-500/15 text-amber-400' },
  NEW_ATTENDEE:    { icon: Users,          pill: 'bg-emerald-500/15 text-emerald-400' },
  NEW_FOLLOWER:    { icon: UserPlus,       pill: 'bg-accent/15 text-accent' },
  FOLLOW_REQUEST:  { icon: UserPlus,       pill: 'bg-amber-500/15 text-amber-400' },
  FOLLOW_ACCEPTED: { icon: UserCheck,      pill: 'bg-emerald-500/15 text-emerald-400' },
  COMMENT_MENTION: { icon: AtSign,         pill: 'bg-accent/15 text-accent' },
  NEW_COMMENT:     { icon: MessageSquare,  pill: 'bg-blue-500/15 text-blue-400' },
}

const FALLBACK_STYLE: TypeStyle = { icon: Bell, pill: 'bg-foreground/10 text-foreground/60' }

// ─── Helpers ──────────────────────────────────────────────────────────────────

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60_000)
  if (m < 1)   return "À l'instant"
  if (m < 60)  return `Il y a ${m} min`
  const h = Math.floor(m / 60)
  if (h < 24)  return `Il y a ${h}h`
  const d = Math.floor(h / 24)
  if (d < 7)   return `Il y a ${d}j`
  return new Date(iso).toLocaleDateString('fr-CH', { day: 'numeric', month: 'short' })
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function NotificationItem({ notif }: Readonly<{ notif: Notification }>) {
  // Defensive fallback: an unknown enum value (e.g. a 10th type the backend
  // ships before the frontend learns about it) must not crash the panel.
  const { icon: Icon, pill } = typeStyles[notif.type] ?? FALLBACK_STYLE
  const label = NOTIFICATION_TYPES[notif.type] ?? 'Notification'

  const content = (
    <div className={`flex items-center gap-3 px-3 py-[20px] transition-colors hover:bg-foreground/5 ${!notif.read ? 'bg-accent/5' : ''}`}>
      <div className={`shrink-0 flex items-center justify-center size-8 rounded-full ${pill}`}>
        <Icon className="size-4" />
      </div>
      <div className="flex-1 min-w-0">
        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium ${pill}`}>
          {label}
        </span>
        <p className="mt-1 text-xs text-foreground/80 leading-snug line-clamp-2">{notif.message}</p>
        <p className="mt-1 text-[10px] text-foreground/40">{relativeTime(notif.createdAt)}</p>
      </div>
      {!notif.read && (
        <span className="shrink-0 size-2 rounded-full bg-accent" aria-hidden />
      )}
    </div>
  )

  if (notif.eventId !== null) {
    return (
      <Link to={`/events/${notif.eventId}`} className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-accent">
        {content}
      </Link>
    )
  }
  return <div>{content}</div>
}

function NotificationPanelFixture() {
  return (
    <div className="w-80">
      <div className="h-12" />
      <div className="h-px" />
      <div className="flex flex-col divide-y divide-border">
        <div className="h-[72px]" />
        <div className="h-[72px]" />
        <div className="h-[72px]" />
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

interface NotificationPanelProps {
  notifications: Notification[]
  loading: boolean
  error: string | null
  onMarkAllRead: () => void
}

export function NotificationPanel({ notifications, loading, error, onMarkAllRead }: Readonly<NotificationPanelProps>) {
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
  const hasUnread = notifications.some(n => !n.read)

  if (loading) {
    return (
      <Skeleton name="notification-panel" loading animate="pulse" color={skeletonColor}>
        <NotificationPanelFixture />
      </Skeleton>
    )
  }

  return (
    <div className="w-80">
      {/* Header */}
      <div className="flex items-center justify-between px-3 h-12">
        <span className="text-sm font-semibold text-foreground">Notifications</span>
        {hasUnread && (
          <button
            type="button"
            onClick={onMarkAllRead}
            className="text-xs text-accent hover:text-accent/80 transition-colors cursor-pointer bg-transparent border-0 p-0"
          >
            Tout marquer comme lu
          </button>
        )}
      </div>

      <div className="h-px bg-border" />

      {/* Body */}
      {error ? (
        <div className="px-4 py-6 text-xs text-center text-foreground/40">{error}</div>
      ) : notifications.length === 0 ? (
        <div className="px-4 py-6 text-xs text-center text-foreground/40">
          Aucune notification
        </div>
      ) : (
        <div className="flex flex-col divide-y divide-border max-h-[360px] overflow-y-auto">
          {notifications.map(notif => (
            <NotificationItem key={notif.id} notif={notif} />
          ))}
        </div>
      )}
    </div>
  )
}
