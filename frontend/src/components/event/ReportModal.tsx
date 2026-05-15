import { useState } from 'react'
import { Flag, X } from 'lucide-react'
import FormField, { Select, Textarea } from '@/components/utils/FormField'
import { REPORT_REASONS } from '@/types/report'
import type { ReportReason } from '@/types/report'

interface ReportModalProps {
  onClose: () => void
  onSubmit: (reason: ReportReason, description?: string) => Promise<void>
  submitting: boolean
}

export default function ReportModal({ onClose, onSubmit, submitting }: Readonly<ReportModalProps>) {
  const [reason, setReason] = useState<ReportReason | ''>('')
  const [description, setDescription] = useState('')

  async function handleSubmit(e: React.SyntheticEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!reason) return
    await onSubmit(reason, description.trim() || undefined)
  }

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">

        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Flag className="w-4 h-4 text-error" />
            <h2 className="text-lg font-bold text-foreground">Signaler cet événement</h2>
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
              {Object.entries(REPORT_REASONS).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
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
    </div>
  )
}
