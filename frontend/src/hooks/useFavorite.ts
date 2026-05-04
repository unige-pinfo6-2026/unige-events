import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { addFavorite, removeFavorite } from '@/services/favoriteApi'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import { useAuth } from '@/hooks/useAuth'

export interface UseFavoriteOptions {
  /**
   * Invoked after a successful add/remove. The hook awaits the returned
   * promise before clearing `loading`, so a parent refetch (e.g. event
   * stats counters that depend on `interestedCount`) completes before the
   * button settles.
   */
  onAfterSuccess?: () => void | Promise<void>
}

interface UseFavoriteResult {
  favorited: boolean
  loading: boolean
  toggle: () => Promise<boolean>
}

export function useFavorite(
  eventId: number,
  initialFavorited = false,
  options: UseFavoriteOptions = {},
): UseFavoriteResult {
  const { onAfterSuccess } = options
  const { favoritedIds, isLoaded, markFavorited, markUnfavorited } = useFavoritesContext()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const favorited = isLoaded ? favoritedIds.has(eventId) : initialFavorited
  const [loading, setLoading] = useState(false)

  async function toggle(): Promise<boolean> {
    if (!isAuthenticated) {
      navigate('/login')
      return false
    }
    if (loading) return false
    const next = !favorited
    if (next) markFavorited(eventId)
    else markUnfavorited(eventId)
    setLoading(true)
    try {
      if (next) {
        await addFavorite(eventId)
      } else {
        await removeFavorite(eventId)
      }
      try {
        await onAfterSuccess?.()
      } catch {
        // A refetch failure must not roll back the successful mutation.
      }
      return true
    } catch {
      if (next) markUnfavorited(eventId)
      else markFavorited(eventId)
      return false
    } finally {
      setLoading(false)
    }
  }

  return { favorited, loading, toggle }
}
