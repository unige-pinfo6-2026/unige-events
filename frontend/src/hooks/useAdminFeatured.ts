import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getFeaturedEvents,
  featureEvent as featureEventApi,
  unfeatureEvent as unfeatureEventApi,
} from '@/services/adminApi'
import { searchEvents } from '@/services/searchApi'
import type { Event } from '@/types/event'

interface UseAdminFeaturedResult {
  featuredEvents: Event[]
  loading: boolean
  error: string | null
  searchQuery: string
  setSearchQuery: (q: string) => void
  searchResults: Event[]
  searchLoading: boolean
  featureEvent: (id: number) => Promise<boolean>
  unfeatureEvent: (id: number) => Promise<boolean>
  refresh: () => void
}

export function useAdminFeatured(): UseAdminFeaturedResult {
  const [featuredEvents, setFeaturedEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<Event[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getFeaturedEvents()
      setFeaturedEvents(data)
    } catch {
      setError('Impossible de charger les événements mis en avant.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetch()
  }, [fetch])

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)

    if (!searchQuery.trim()) {
      setSearchResults([])
      return
    }

    debounceRef.current = setTimeout(async () => {
      setSearchLoading(true)
      try {
        const results = await searchEvents({ q: searchQuery, size: 8 })
        setSearchResults(results)
      } catch {
        setSearchResults([])
      } finally {
        setSearchLoading(false)
      }
    }, 350)

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [searchQuery])

  const featureEvent = useCallback(async (id: number) => {
    try {
      await featureEventApi(id)
      const featured = searchResults.find(e => e.id === id)
      if (featured) {
        setFeaturedEvents(prev => [featured, ...prev])
        setSearchResults(prev => prev.filter(e => e.id !== id))
      }
      return true
    } catch {
      return false
    }
  }, [searchResults])

  const unfeatureEvent = useCallback(async (id: number) => {
    try {
      await unfeatureEventApi(id)
      setFeaturedEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  return {
    featuredEvents,
    loading,
    error,
    searchQuery,
    setSearchQuery,
    searchResults,
    searchLoading,
    featureEvent,
    unfeatureEvent,
    refresh: fetch,
  }
}
