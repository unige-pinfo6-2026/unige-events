import { Bell } from 'lucide-react'
import { Dropdown } from '@/components/utils/Dropdown'
import { IconButton } from '@/components/utils/Buttons'

export function NotificationsDropdown() {
  return (
    <Dropdown
      align="right"
      showChevron={false}
      trigger={
        <IconButton label="Notifications" onClick={() => {}}>
          <Bell className="size-5" />
        </IconButton>
      }
    >
      {/* TODO: SPRINT 8 */}
      <div className="px-4 py-4 text-sm text-center text-foreground/40">
        SPRINT 8
      </div>
    </Dropdown>
  )
}
