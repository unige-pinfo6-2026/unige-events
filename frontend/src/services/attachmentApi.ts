import api from './api'
import type { Attachment } from '@/types/attachment'

/**
 * SCRUM-148 — endpoints fichiers joints à un événement.
 * Conventions multipart identiques à `uploadEventImage` / `uploadPhoto` :
 * champ `file` posté via `FormData`, Axios déduit le content-type.
 */
export async function uploadEventAttachment(eventId: number, file: File): Promise<Attachment> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post<Attachment>('/events/' + eventId + '/attachments', formData)
  return response.data
}

export async function deleteEventAttachment(eventId: number, attachmentId: number): Promise<void> {
  await api.delete('/events/' + eventId + '/attachments/' + attachmentId)
}
