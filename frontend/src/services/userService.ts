import api from './api'
import type { User } from '@/types/user'

export async function getMe(): Promise<User> {
  const response = await api.get<User>('/users/me')
  return response.data
}

export async function getUserById(id: string): Promise<User | null> {
  const response = await api.get<User>(`/users/${id}`)
  return response.data
}

export async function updateProfile(data: Partial<User>): Promise<User> {
  const response = await api.put<User>('/users/me', data)
  return response.data
}

export async function uploadPhoto(file: File): Promise<User> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post<User>('/users/me/image', formData)
  return response.data
}
