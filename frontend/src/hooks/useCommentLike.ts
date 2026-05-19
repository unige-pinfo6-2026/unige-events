import { useCallback, useState } from 'react'
import { likeComment, unlikeComment } from '@/services/commentApi'
import { useToast } from '@/hooks/useToast'

interface UseCommentLikeReturn {
  liked: boolean
  likeCount: number
  /** `true` pendant qu'une mutation est en cours. Le bouton appelant doit
   *  passer `disabled={toggling}` pour éviter les double-clics. */
  toggling: boolean
  /** Toggle le like avec optimistic update + rollback sur erreur. Idempotent
   *  sous le `disabled` : si appelé pendant `toggling`, c'est un no-op. */
  toggle: () => Promise<void>
}

/**
 * Hook bespoke (convention projet — pas de TanStack) pour gérer le like /
 * unlike d'un commentaire — SCRUM-147 Décision C.
 *
 * <p>Pattern optimistic + rollback :
 * <ol>
 *   <li>Au clic : flip immédiat de {@code liked} + ajustement de
 *       {@code likeCount} (+1 / -1).</li>
 *   <li>Lancement du POST (like) ou DELETE (unlike).</li>
 *   <li>Succès 2xx : confirme l'état optimiste, {@code toggling = false}.</li>
 *   <li>Erreur (4xx/5xx/réseau) : rollback à l'état pré-clic + toast erreur.</li>
 * </ol>
 *
 * <p>Le hook s'initialise avec la valeur du DTO renvoyé par
 * {@code GET /events/{id}/comments}. Le caller n'a pas à re-synchroniser à
 * la suite d'un refetch — chaque {@code CommentItem} a son propre hook.
 */
export function useCommentLike(
  commentId: number,
  initialLiked: boolean,
  initialCount: number,
): UseCommentLikeReturn {
  const [liked, setLiked] = useState(initialLiked)
  const [likeCount, setLikeCount] = useState(initialCount)
  const [toggling, setToggling] = useState(false)
  const { showToast } = useToast()

  const toggle = useCallback(async () => {
    if (toggling) return // double-click guard
    const previousLiked = liked
    const previousCount = likeCount

    // Optimistic flip — UI feels instantaneous.
    const nextLiked = !previousLiked
    setLiked(nextLiked)
    setLikeCount(previousCount + (nextLiked ? 1 : -1))
    setToggling(true)

    try {
      if (nextLiked) {
        await likeComment(commentId)
      } else {
        await unlikeComment(commentId)
      }
      // 2xx : the optimistic state stands. Backend may return a slightly
      // different count for race scenarios, but the in-flight cohort agrees
      // on +1/-1 — acceptable for an in-app counter.
    } catch {
      // Rollback to the pre-click state. The user sees the counter snap back.
      setLiked(previousLiked)
      setLikeCount(previousCount)
      showToast('error', 'Impossible d\'enregistrer le like, réessayez.')
    } finally {
      setToggling(false)
    }
  }, [toggling, liked, likeCount, commentId, showToast])

  return { liked, likeCount, toggling, toggle }
}
