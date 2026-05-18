export const NOTIFICATION_TYPES = {
  EVENT_UPDATED:    'Événement mis à jour',
  EVENT_CANCELLED:  'Événement annulé',
  EVENT_REMINDER:   "Rappel d'événement",
  NEW_ATTENDEE:     'Nouvelle inscription',
  NEW_FOLLOWER:     'Nouveau follower',
  FOLLOW_REQUEST:   'Demande de suivi',
  FOLLOW_ACCEPTED:  'Demande acceptée',
  COMMENT_MENTION:  'Mention en commentaire',
  NEW_COMMENT:      'Nouveau commentaire',
} as const

export type NotificationType = keyof typeof NOTIFICATION_TYPES

export interface Notification {
  id: number
  userId: string
  type: NotificationType
  message: string
  read: boolean
  createdAt: string
  eventId: number | null
  relatedUserId: string | null
  readAt: string | null
}
