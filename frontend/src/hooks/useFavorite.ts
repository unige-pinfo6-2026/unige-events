import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { addFavorite, removeFavorite } from '@/services/favoriteApi'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import { useAuth } from '@/hooks/useAuth'

interface UseFavoriteResult {
  favorited: boolean
  loading: boolean
  toggle: () => Promise<boolean>
}

export function useFavorite(eventId: number, initialFavorited = false): UseFavoriteResult {
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
