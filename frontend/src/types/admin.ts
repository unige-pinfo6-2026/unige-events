import type { ReportReason } from '@/types/report'

export type ReportStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED'

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
