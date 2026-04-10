import api from '@/services/api'

export async function getShareUrl(eventId: number): Promise<string> {
  const { data } = await api.get<{ shareUrl: string }>(`/events/${eventId}/share`)
  return data.shareUrl
}
