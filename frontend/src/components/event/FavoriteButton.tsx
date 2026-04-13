import type { MouseEvent } from 'react'
import { Star } from 'lucide-react'
import { useFavorite } from '@/hooks/useFavorite'

export default function FavoriteButton({
  eventId,
  initialFavorited = false,
  onRemove,
}: Readonly<{
  eventId: number
  initialFavorited?: boolean
  onRemove?: () => void
}>) {
  const { favorited, loading, toggle } = useFavorite(eventId, initialFavorited)

  async function handleClick(e: MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    const wasFavorited = favorited
    const success = await toggle()
    if (wasFavorited && success) onRemove?.()
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={loading}
      aria-label={favorited ? 'Retirer des favoris' : 'Ajouter aux favoris'}
      className="p-2 rounded-full transition-colors cursor-pointer border-0 bg-black/30 backdrop-blur-sm hover:bg-black/50 disabled:opacity-50"
    >
      <Star
        className="w-4 h-4"
        fill={favorited ? '#facc15' : 'none'}
        stroke={favorited ? '#facc15' : 'white'}
      />
    </button>
  )
}
