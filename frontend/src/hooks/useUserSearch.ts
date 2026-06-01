import { useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { useDebounce } from '@/hooks/useDebounce'
import { searchUsernames } from '@/services/userService'
import type { UserPublicResponse } from '@/types/user'

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 300
const RESULT_LIMIT = 20

export interface UseUserSearchResult {
  query: string
  setQuery: (q: string) => void
  results: UserPublicResponse[]
  loading: boolean
  error: string | null
  /** True once a query ≥ 2 chars has triggered a search (drives the empty state). */
  searched: boolean
}

/**
 * Debounced user search backed by `GET /api/users/search` (bug ⑦). The endpoint
 * is `@Authenticated`, so callers pass `enabled = isAuthenticated` — while
 * disabled the hook stays inert (no request, empty results). A monotonic
 * `requestIdRef` discards stale responses. 401s are swallowed (treated as "not
 * authenticated") rather than surfaced as an error.
 */
export function useUserSearch(enabled: boolean): UseUserSearchResult {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<UserPublicResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searched, setSearched] = useState(false)
  const debounced = useDebounce(query.trim(), DEBOUNCE_MS)
  const requestIdRef = useRef(0)

  useEffect(() => {
    if (!enabled || debounced.length < MIN_QUERY_LENGTH) {
      requestIdRef.current += 1
      setResults([])
      setLoading(false)
      setError(null)
      setSearched(false)
      return
    }
    const myId = ++requestIdRef.current
    setLoading(true)
    setError(null)
    setSearched(true)
    searchUsernames(debounced, RESULT_LIMIT)
      .then((data) => {
        if (requestIdRef.current === myId) setResults(data)
      })
      .catch((err) => {
        if (requestIdRef.current !== myId) return
        setResults([])
        if (!axios.isAxiosError(err) || err.response?.status !== 401) {
          setError('Impossible de charger les utilisateurs.')
        }
      })
      .finally(() => {
        if (requestIdRef.current === myId) setLoading(false)
      })
  }, [enabled, debounced])

  return { query, setQuery, results, loading, error, searched }
}
