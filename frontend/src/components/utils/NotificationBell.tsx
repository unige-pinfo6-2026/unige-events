import { Bell } from 'lucide-react'
import { IconButton } from '@/components/utils/Buttons'

export function NotificationBell({ unreadCount }: Readonly<{ unreadCount: number }>) {
  return (
    <div className="relative">
      <IconButton label="Notifications" onClick={() => {}}>
        <Bell className="size-5" />
      </IconButton>
      {unreadCount > 0 && (
        <span
          aria-label={`${unreadCount} notification${unreadCount > 1 ? 's' : ''} non lue${unreadCount > 1 ? 's' : ''}`}
          className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[16px] h-4 px-1 text-[10px] font-bold leading-none text-white bg-red-500 rounded-full pointer-events-none select-none"
        >
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
    </div>
  )
}
