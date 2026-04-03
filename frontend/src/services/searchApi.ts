import api from './api'
import type { SearchParams, SearchResponse } from '../types'

export async function searchEvents(params: SearchParams): Promise<SearchResponse> {
  const response = await api.get<SearchResponse>('/events/search', { params })
  return response.data
}

// TODO: No suggestion endpoint defined in openapi.yaml — stub returning empty array until backend provides one
export async function fetchSuggestions(_query: string): Promise<string[]> {
  return Promise.resolve([])
}
