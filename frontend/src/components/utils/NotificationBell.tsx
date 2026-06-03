import { Bell } from 'lucide-react'

export function NotificationBell({ unreadCount }: Readonly<{ unreadCount: number }>) {
  return (
    <div className="relative">
      {/* Presentational only: the interactive control is the Dropdown trigger
          (role="button" + aria-label) that wraps this bell. A real <button>
          here would be a button nested in a button, which taps unreliably on
          iOS. The hover background is gated to hover-capable devices so it
          doesn't stick after a tap. */}
      <span className="flex p-2 rounded-lg text-foreground [@media(hover:hover)]:hover:bg-foreground/5 transition-colors">
        <Bell className="size-5" />
      </span>
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
