export type AttendanceStatus = 'ATTENDING' | 'WAITLISTED'

export interface Attendance {
  id: number
  userId: string
  eventId: number
  status: AttendanceStatus
  createdAt: string
}

export interface AttendanceRequest {
  status: AttendanceStatus
}
