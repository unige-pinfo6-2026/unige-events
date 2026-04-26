import api from './api'
import type { SearchParams, SearchResponse } from '@/types/search'

// Sérialise les arrays sans crochets (`?tags=a&tags=b` au lieu de `?tags[]=a&tags[]=b`),
// format attendu par JAX-RS (`@QueryParam("tags") List<String> tags`).
const ARRAY_PARAMS_SERIALIZER = {
  indexes: null,
} as const

export async function searchEvents(params: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
  const response = await api.get<SearchResponse>('/events/search', {
    params,
    paramsSerializer: ARRAY_PARAMS_SERIALIZER,
    ...(signal !== undefined && { signal }),
  })
  return response.data
}

// TODO: No suggestion endpoint defined in openapi.yaml — stub returning empty array until backend provides one
// TODO: Forward signal to Axios when the real endpoint is available
export async function fetchSuggestions(_query: string, _signal?: AbortSignal): Promise<string[]> {
  return Promise.resolve([])
}
