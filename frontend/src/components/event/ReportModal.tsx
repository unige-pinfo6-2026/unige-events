import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Flag, X } from 'lucide-react'
import FormField, { Select, Textarea } from '@/components/utils/FormField'
import { REPORT_REASONS } from '@/types/report'
import type { ReportReason } from '@/types/report'

interface ReportModalProps {
  onClose: () => void
  onSubmit: (reason: ReportReason, description?: string) => Promise<void>
  submitting: boolean
  /** SCRUM-147 — change le titre + les motifs proposés selon la cible. Le
   *  backend consomme la même shape (reason + description) pour event et
   *  comment ; seul le wording et la liste affichée changent. */
  target?: 'event' | 'comment'
}

const TITLES: Record<'event' | 'comment', string> = {
  event: 'Signaler cet événement',
  comment: 'Signaler ce commentaire',
}

// Motifs proposés par cible : un commentaire ne peut pas être un « Faux
// événement » (FAKE), donc on ne l'expose pas côté commentaire.
const REASONS_BY_TARGET: Record<'event' | 'comment', ReportReason[]> = {
  event: ['SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER'],
  comment: ['SPAM', 'INAPPROPRIATE', 'OTHER'],
}

export default function ReportModal({ onClose, onSubmit, submitting, target = 'event' }: Readonly<ReportModalProps>) {
  const [reason, setReason] = useState<ReportReason | ''>('')
  const [description, setDescription] = useState('')

  async function handleSubmit(e: React.SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!reason) return
    await onSubmit(reason, description.trim() || undefined)
  }

  // Rendered through a portal on document.body so the fixed overlay isn't
  // trapped by an ancestor with backdrop-blur/transform (the comment cards),
  // which would otherwise confine + clip it to the comments section.
  return createPortal(
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">

        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Flag className="w-4 h-4 text-error" />
            <h2 className="text-lg font-bold text-foreground">{TITLES[target]}</h2>
          </div>
          <button
            type="button"
            aria-label="Fermer"
            onClick={onClose}
            disabled={submitting}
            className="p-1 rounded-lg text-foreground/40 hover:text-foreground hover:bg-foreground/5 transition-colors cursor-pointer disabled:opacity-50"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <FormField label="Motif" htmlFor="report-reason" required>
            <Select
              id="report-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value as ReportReason | '')}
              required
              disabled={submitting}
            >
              <option value="">Sélectionner un motif</option>
              {REASONS_BY_TARGET[target].map((key) => (
                <option key={key} value={key}>{REPORT_REASONS[key]}</option>
              ))}
            </Select>
          </FormField>

          <FormField label="Description" htmlFor="report-description">
            <Textarea
              id="report-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Précisez votre signalement (optionnel)..."
              rows={3}
              disabled={submitting}
            />
          </FormField>

          <div className="flex gap-3 justify-end mt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={!reason || submitting}
              className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
            >
              {submitting ? 'Envoi...' : 'Signaler'}
            </button>
          </div>
        </form>

      </div>
    </div>,
    document.body,
  )
}
