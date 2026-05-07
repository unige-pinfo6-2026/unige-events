import api from './api'
import type { Event } from '@/types/event'
import type { Report, ReportStatus } from '@/types/admin'

export async function getReports(status?: ReportStatus): Promise<Report[]> {
  const response = await api.get<Report[]>('/admin/reports', {
    params: status ? { status } : undefined,
  })
  return response.data
}

export async function updateReportStatus(id: number, status: ReportStatus): Promise<Report> {
  const response = await api.patch<Report>(`/admin/reports/${id}`, { status })
  return response.data
}

export async function getFeaturedEvents(): Promise<Event[]> {
  const response = await api.get<Event[]>('/events', { params: { featured: true } })
  return response.data
}

export async function featureEvent(id: number): Promise<void> {
  await api.patch(`/admin/events/${id}/feature`)
}

export async function unfeatureEvent(id: number): Promise<void> {
  await api.patch(`/admin/events/${id}/unfeature`)
}
