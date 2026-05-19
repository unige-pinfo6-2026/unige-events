import { Dropdown } from '@/components/utils/Dropdown'
import { NotificationBell } from '@/components/utils/NotificationBell'
import { NotificationPanel } from '@/components/utils/NotificationPanel'
import { useNotifications } from '@/hooks/useNotifications'

export function NotificationsDropdown() {
  const { notifications, unreadCount, loading, error, markAllAsRead, markOneAsRead } = useNotifications()

  return (
    <Dropdown
      align="right"
      showChevron={false}
      trigger={<NotificationBell unreadCount={unreadCount} />}
    >
      <NotificationPanel
        notifications={notifications}
        loading={loading}
        error={error}
        onMarkAllRead={markAllAsRead}
        onMarkOneRead={markOneAsRead}
      />
    </Dropdown>
  )
}
