import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from '@/hooks/useAuth'
import { getFavorites } from '@/services/favoriteApi'

interface FavoritesContextValue {
  favoritedIds: Set<number>
  isLoaded: boolean
  markFavorited: (id: number) => void
  markUnfavorited: (id: number) => void
}

const FavoritesContext = createContext<FavoritesContextValue | null>(null)

export function FavoritesProvider({ children }: Readonly<{ children: ReactNode }>) {
  const { isAuthenticated } = useAuth()
  const [favoritedIds, setFavoritedIds] = useState<Set<number>>(new Set())
  const [isLoaded, setIsLoaded] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) {
      setFavoritedIds(new Set())
      setIsLoaded(false)
      return
    }
    getFavorites()
      .then(events => setFavoritedIds(new Set(events.map(e => e.id))))
      .catch(() => { /* leave set empty on error */ })
      .finally(() => setIsLoaded(true))
  }, [isAuthenticated])

  const markFavorited = useCallback((id: number) => {
    setFavoritedIds(prev => new Set([...prev, id]))
  }, [])

  const markUnfavorited = useCallback((id: number) => {
    setFavoritedIds(prev => {
      const next = new Set(prev)
      next.delete(id)
      return next
    })
  }, [])

  return (
    <FavoritesContext.Provider value={{ favoritedIds, isLoaded, markFavorited, markUnfavorited }}>
      {children}
    </FavoritesContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useFavoritesContext(): FavoritesContextValue {
  const ctx = useContext(FavoritesContext)
  if (!ctx) throw new Error('useFavoritesContext must be used within FavoritesProvider')
  return ctx
}
