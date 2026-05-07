export type ReportStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED'

export const REPORT_REASONS = {
  SPAM: { label: 'Spam' },
  INAPPROPRIATE: { label: 'Contenu inapproprié' },
  FAKE: { label: 'Faux événement' },
  OTHER: { label: 'Autre' },
} as const

export type ReportReason = keyof typeof REPORT_REASONS

export interface Report {
  id: number
  eventId: number | null
  eventTitle: string | null
  reporterId: string | null
  reporterDisplayName: string | null
  reason: ReportReason
  description: string | null
  status: ReportStatus
  moderationNote: string | null
  createdAt: string
  reviewedAt: string | null
  reviewedBy: string | null
}

export interface UpdateReportStatusRequest {
  status: ReportStatus
}
