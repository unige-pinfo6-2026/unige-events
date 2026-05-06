import { useState } from 'react'
import { Flag, X } from 'lucide-react'
import { Select, Textarea } from '@/components/utils/FormField'
import { REPORT_REASONS } from '@/hooks/useReport'
import type { ReportReason } from '@/hooks/useReport'

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
    <div
      role="presentation"
      className="fixed inset-0 bg-black/70 backdrop-blur-md flex items-end sm:items-center justify-center z-50 p-4"
      onClick={(e) => { if (e.target === e.currentTarget && !submitting) onClose() }}
      onKeyDown={(e) => { if (e.key === 'Escape' && !submitting) onClose() }}
    >
      <div className="bg-background border border-border rounded-3xl w-full max-w-lg shadow-2xl overflow-hidden">

        {/* Gradient header band */}
        <div className="relative bg-linear-to-br from-error/20 via-error/10 to-transparent px-7 pt-7 pb-6">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,_var(--tw-gradient-stops))] from-error/10 to-transparent pointer-events-none" />

          <div className="relative flex items-start justify-between gap-4">
            <div className="flex items-center gap-4">
              <div className="flex items-center justify-center w-12 h-12 rounded-2xl bg-error/15 border border-error/20 shrink-0">
                <Flag className="w-6 h-6 text-error" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-foreground leading-tight">Signaler cet événement</h2>
                <p className="text-sm text-foreground/50 mt-0.5">Votre signalement sera examiné par un modérateur.</p>
              </div>
            </div>
            <button
              type="button"
              aria-label="Fermer"
              onClick={onClose}
              disabled={submitting}
              className="p-2 rounded-xl text-foreground/40 hover:text-foreground hover:bg-foreground/10 transition-colors cursor-pointer disabled:opacity-50 shrink-0 mt-0.5"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Divider */}
        <div className="h-px bg-border" />

        {/* Form body */}
        <form onSubmit={handleSubmit} className="p-7 flex flex-col gap-5">

          <div className="flex flex-col gap-1.5">
            <label htmlFor="report-reason" className="text-sm font-semibold text-foreground">
              Motif du signalement <span className="text-error">*</span>
            </label>
            <p className="text-xs text-foreground/40">Choisissez la catégorie qui correspond le mieux à votre signalement.</p>
            <Select
              id="report-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value as ReportReason | '')}
              required
              disabled={submitting}
            >
              <option value="">Sélectionner un motif…</option>
              {REPORT_REASONS.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </Select>
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="report-description" className="text-sm font-semibold text-foreground">
              Détails <span className="text-foreground/35 font-normal text-xs">(optionnel)</span>
            </label>
            <p className="text-xs text-foreground/40">Donnez plus de contexte pour aider les modérateurs à traiter votre signalement.</p>
            <Textarea
              id="report-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Décrivez le problème en détail…"
              rows={4}
              disabled={submitting}
            />
          </div>

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="flex-1 px-4 py-3 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 hover:bg-foreground/5 transition-colors cursor-pointer bg-transparent"
            >
              Annuler
            </button>
            <button
              type="submit"
              disabled={!reason || submitting}
              className="flex-1 px-4 py-3 rounded-xl bg-error text-white text-sm font-bold disabled:opacity-40 disabled:cursor-not-allowed hover:bg-error/85 transition-colors cursor-pointer border-0 shadow-lg shadow-error/20"
            >
              {submitting ? 'Envoi en cours…' : 'Envoyer le signalement'}
            </button>
          </div>

        </form>
      </div>
    </div>
  )
}
