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

  // 300ms debounce: query → suggestions
  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([])
      return
    }
    const timer = setTimeout(() => {
      fetchSuggestions(query)
        .then((data) => setSuggestions(data.slice(0, 5)))
        .catch(() => setSuggestions([]))
    }, 300)
    return () => clearTimeout(timer)
  }, [query])

  const performSearch = useCallback(async (q: string, f: SearchFilters) => {
    const trimmed = q.trim()
    const params: SearchParams = {
      q: trimmed || undefined,
      category: f.category,
      faculty: f.faculty,
      dateFrom: f.dateFrom,
      dateTo: f.dateTo,
    }
    setLoading(true)
    setError(null)
    try {
      const data = await searchEvents(params)
      setResults(data)
    } catch {
      setError('Impossible de charger les résultats.')
    } finally {
      setLoading(false)
    }
  }, [])

  // 2000ms debounce: query + filters → search
  useEffect(() => {
    const timer = setTimeout(() => {
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
      performSearch(text, filtersRef.current)
    },
    [performSearch],
  )

  const searchNow = useCallback(() => {
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
