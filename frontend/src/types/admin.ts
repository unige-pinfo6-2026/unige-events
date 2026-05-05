export type ReportStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED'

export interface Report {
  id: number
  eventId: number
  eventTitle: string
  reason: string
  reportedByDisplayName: string
  createdAt: string
  status: ReportStatus
}

export interface UpdateReportStatusRequest {
  status: ReportStatus
}
