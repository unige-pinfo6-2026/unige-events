export const NOTIFICATION_TYPES = {
  EVENT_UPDATED:   'Événement mis à jour',
  EVENT_CANCELLED: 'Événement annulé',
  EVENT_REMINDER:  "Rappel d'événement",
} as const

export type NotificationType = keyof typeof NOTIFICATION_TYPES

export interface Notification {
  id: string
  userId: string
  eventId: string
  type: NotificationType
  message: string
  read: boolean
  createdAt: string
}
