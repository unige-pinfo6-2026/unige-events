import api from './api'
import type { SearchParams, SearchResponse } from '../types'

export async function searchEvents(params: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
  const response = await api.get<SearchResponse>('/events/search', {
    params,
    ...(signal !== undefined && { signal }),
  })
  return response.data
}

// TODO: No suggestion endpoint defined in openapi.yaml — stub returning empty array until backend provides one
// TODO: Forward signal to Axios when the real endpoint is available
export async function fetchSuggestions(_query: string, _signal?: AbortSignal): Promise<string[]> {
  return Promise.resolve([])
}
