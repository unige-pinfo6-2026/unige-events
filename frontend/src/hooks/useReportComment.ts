import { useCallback, useState } from 'react'
import { AxiosError } from 'axios'
import { reportComment, type ReportCommentRequest } from '@/services/reportApi'
import { useToast } from '@/hooks/useToast'

interface UseReportCommentReturn {
  submitting: boolean
  /** Returns `true` when the modal should close (success or terminal
   *  "already-reported"), `false` when it should stay open for retry. */
  submit: (body: ReportCommentRequest) => Promise<boolean>
}

/**
 * Hook bespoke pour le signalement d'un commentaire — SCRUM-147 Décision G.
 *
 * <p>Wrap {@code reportApi.reportComment} + gestion des codes applicatifs :
 * <ul>
 *   <li><strong>201</strong> : succès. Toast {@code "Commentaire signalé. Merci."}.
 *       Renvoie {@code true} pour fermer la modal.</li>
 *   <li><strong>409 already_reported</strong> : Toast
 *       {@code "Vous avez déjà signalé ce commentaire."}. Renvoie
 *       {@code true} pour fermer (l'action est déjà accomplie).</li>
 *   <li><strong>422 cannot_report_own_comment</strong> : filet défensif (le
 *       bouton est censé être masqué pour l'auteur). Toast
 *       {@code "Vous ne pouvez pas signaler votre propre commentaire."} +
 *       fermeture.</li>
 *   <li><strong>4xx / 5xx</strong> : Toast générique
 *       {@code "Erreur lors du signalement, réessayez."} + la modal reste
 *       ouverte pour retry.</li>
 * </ul>
 */
export function useReportComment(commentId: number): UseReportCommentReturn {
  const [submitting, setSubmitting] = useState(false)
  const { showToast } = useToast()

  const submit = useCallback(async (body: ReportCommentRequest): Promise<boolean> => {
    if (submitting) return false
    setSubmitting(true)
    try {
      await reportComment(commentId, body)
      showToast('success', 'Commentaire signalé. Merci.')
      return true
    } catch (err) {
      const status = err instanceof AxiosError ? err.response?.status : undefined
      if (status === 409) {
        showToast('error', 'Vous avez déjà signalé ce commentaire.')
        return true
      }
      if (status === 422) {
        showToast('error', 'Vous ne pouvez pas signaler votre propre commentaire.')
        return true
      }
      showToast('error', 'Erreur lors du signalement, réessayez.')
      return false
    } finally {
      setSubmitting(false)
    }
  }, [submitting, commentId, showToast])

  return { submitting, submit }
}
