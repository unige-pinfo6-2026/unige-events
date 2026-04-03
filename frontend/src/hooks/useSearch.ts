import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchSuggestions, searchEvents } from '../services/searchApi'
import type { Event, EventCategory, Faculty, SearchParams } from '../types'

export interface SearchFilters {
  category?: EventCategory
  faculty?: Faculty
  dateFrom?: string
  dateTo?: string
}

const DEFAULT_FILTERS: SearchFilters = {}

export interface UseSearchResult {
  query: string
  setQuery: (q: string) => void
  filters: SearchFilters
  setFilters: (f: SearchFilters) => void
  results: Event[]
  suggestions: string[]
  loading: boolean
  error: string | null
  resetFilters: () => void
  selectSuggestion: (text: string) => void
  searchNow: () => void
}

export function useSearch(): UseSearchResult {
  const [searchParams, setSearchParams] = useSearchParams()

  const [query, setQueryState] = useState<string>(searchParams.get('q') ?? '')
  const [filters, setFiltersState] = useState<SearchFilters>({
    category: (searchParams.get('category') as EventCategory) || undefined,
    faculty: (searchParams.get('faculty') as Faculty) || undefined,
    dateFrom: searchParams.get('dateFrom') || undefined,
    dateTo: searchParams.get('dateTo') || undefined,
  })
  const [results, setResults] = useState<Event[]>([])
  const [suggestions, setSuggestions] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Refs to access latest state inside callbacks without stale closures
  const filtersRef = useRef<SearchFilters>(filters)
  useEffect(() => {
    filtersRef.current = filters
  }, [filters])

  // Abort controller for in-flight search requests — aborts previous before starting a new one
  const searchAbortRef = useRef<AbortController | null>(null)

  // When true, the next 2000ms debounce tick is skipped (used after an immediate search)
  const skipNextDebounce = useRef(false)

  // Sync state → URL (replace so browser history stays clean)
  useEffect(() => {
    const params: Record<string, string> = {}
    const trimmedQuery = query.trim()
    if (trimmedQuery) params.q = trimmedQuery
    if (filters.category) params.category = filters.category
    if (filters.faculty) params.faculty = filters.faculty
    if (filters.dateFrom) params.dateFrom = filters.dateFrom
    if (filters.dateTo) params.dateTo = filters.dateTo
    setSearchParams(params, { replace: true })
  }, [query, filters, setSearchParams])

  // 300ms debounce: query → suggestions (aborts stale in-flight suggestion requests)
  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([])
      return
    }
    const controller = new AbortController()
    const timer = setTimeout(() => {
      fetchSuggestions(query, controller.signal)
        .then((data) => setSuggestions(data.slice(0, 5)))
        .catch((err: unknown) => {
          if (err instanceof Error && (err.name === 'CanceledError' || err.name === 'AbortError')) return
          setSuggestions([])
        })
    }, 300)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query])

  const performSearch = useCallback(async (q: string, f: SearchFilters) => {
    // Abort any in-flight search request before starting a new one
    searchAbortRef.current?.abort()
    const controller = new AbortController()
    searchAbortRef.current = controller

    const trimmed = q.trim()
    // TODO: SCRUM-77 — faculty filter omitted; backend does not yet support it
    const params: SearchParams = {
      q: trimmed || undefined,
      category: f.category,
      dateFrom: f.dateFrom,
      dateTo: f.dateTo,
    }
    setLoading(true)
    setError(null)
    try {
      const data = await searchEvents(params, controller.signal)
      setResults(data)
    } catch (err) {
      if (err instanceof Error && (err.name === 'CanceledError' || err.name === 'AbortError')) return
      setError('Impossible de charger les résultats.')
    } finally {
      if (!controller.signal.aborted) setLoading(false)
    }
  }, [])

  // 2000ms debounce: query + filters → search
  // Skipped when an immediate search (selectSuggestion / searchNow) just fired
  useEffect(() => {
    const timer = setTimeout(() => {
      if (skipNextDebounce.current) {
        skipNextDebounce.current = false
        return
      }
      performSearch(query, filters)
    }, 2000)
    return () => clearTimeout(timer)
  }, [query, filters, performSearch])

  const setQuery = useCallback((q: string) => setQueryState(q), [])

  const setFilters = useCallback((f: SearchFilters) => setFiltersState(f), [])

  const resetFilters = useCallback(() => setFiltersState({ ...DEFAULT_FILTERS }), [])

  const selectSuggestion = useCallback(
    (text: string) => {
      setQueryState(text)
      setSuggestions([])
      skipNextDebounce.current = true
      performSearch(text, filtersRef.current)
    },
    [performSearch],
  )

  const searchNow = useCallback(() => {
    skipNextDebounce.current = true
    performSearch(query.trim(), filtersRef.current)
  }, [query, performSearch])

  return {
    query,
    setQuery,
    filters,
    setFilters,
    results,
    suggestions,
    loading,
    error,
    resetFilters,
    selectSuggestion,
    searchNow,
  }
}
