import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { fetchSuggestions, searchEvents } from '@/services/searchApi'
import type { Event, EventCategory, Faculty } from '@/types/event'
import type { SearchFilters, SearchParams } from '@/types/search'

const DEFAULT_FILTERS: SearchFilters = { includePast: false }

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
    facultyNone: searchParams.get('facultyNone') === 'true' ? true : undefined,
    dateFrom: searchParams.get('dateFrom') || undefined,
    dateTo: searchParams.get('dateTo') || undefined,
    includePast: searchParams.get('includePast') === 'true',
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
    if (filters.facultyNone) {
      params.facultyNone = 'true'
    } else if (filters.faculty) {
      params.faculty = filters.faculty
    }
    if (filters.dateFrom) params.dateFrom = filters.dateFrom
    if (filters.dateTo) params.dateTo = filters.dateTo
    if (filters.includePast) params.includePast = 'true'
    setSearchParams(params, { replace: true })
  }, [query, filters, setSearchParams])

  // 300ms debounce: query → suggestions (aborts stale in-flight suggestion requests)
  // Fix 3: set loading=true immediately when query becomes non-empty, before the timer fires
  useEffect(() => {
    if (!query.trim()) {
      setSuggestions([])
      return
    }
    setLoading(true)
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
    const today = new Date().toISOString().split('T')[0]
    // Fix 4: when includePast is false, enforce dateFrom >= today
    const futureDateFrom = f.dateFrom && f.dateFrom > today ? f.dateFrom : today
    const effectiveDateFrom = f.includePast ? f.dateFrom : futureDateFrom
    // Mutex côté client : facultyNone=true ignore faculty.
    const params: SearchParams = {
      q: trimmed || undefined,
      category: f.category,
      faculty: f.facultyNone ? undefined : f.faculty,
      facultyNone: f.facultyNone || undefined,
      dateFrom: effectiveDateFrom,
      dateTo: f.dateTo,
    }
    setLoading(true)
    setError(null)
    try {
      const data = await searchEvents(params, controller.signal)
      // Fix 5: sort client-side — upcoming events ascending, past events descending
      const now = Date.now()
      const sorted = [...data].sort((a, b) => {
        const aTime = new Date(a.startDate).getTime()
        const bTime = new Date(b.startDate).getTime()
        const aIsPast = aTime < now
        const bIsPast = bTime < now
        if (!aIsPast && !bIsPast) return aTime - bTime  // both upcoming: ascending
        if (aIsPast && bIsPast) return bTime - aTime    // both past: most-recent-first
        return aIsPast ? 1 : -1                          // upcoming before past
      })
      setResults(sorted)
    } catch (err) {
      if (err instanceof Error && (err.name === 'CanceledError' || err.name === 'AbortError')) return
      setError('Impossible de charger les résultats.')
    } finally {
      if (!controller.signal.aborted) setLoading(false)
    }
  }, [])

  // On initial mount: search immediately (no debounce).
  // On subsequent query changes (user typing): 2000ms debounce.
  const isFirstRender = useRef(true)
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false
      performSearch(query, filtersRef.current)
      return
    }
    const timer = setTimeout(() => {
      if (skipNextDebounce.current) {
        skipNextDebounce.current = false
        return
      }
      performSearch(query, filtersRef.current)
    }, 400)
    return () => clearTimeout(timer)
  }, [query, performSearch])

  const setQuery = useCallback((q: string) => setQueryState(q), [])

  // Fix 1: setFilters triggers immediate search, bypassing the 2000ms debounce
  const setFilters = useCallback(
    (f: SearchFilters) => {
      setFiltersState(f)
      // Skip the in-flight query debounce to avoid a duplicate search firing afterward
      skipNextDebounce.current = true
      performSearch(query, f)
    },
    [query, performSearch],
  )

  const resetFilters = useCallback(() => setFilters(DEFAULT_FILTERS), [setFilters])

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
