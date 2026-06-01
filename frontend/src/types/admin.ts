import type { ReportReason } from '@/types/report'

export type ReportStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED'

export type ReportTargetType = 'EVENT' | 'COMMENT'

export interface Report {
  id: number
  /** Discriminator (bug ③): drives whether the row shows the event or the comment. */
  targetType: ReportTargetType
  eventId: number | null
  commentId: number | null
  eventTitle: string | null
  /** Body of the reported comment (bug ③) — populated only for COMMENT reports. */
  commentContent: string | null
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
