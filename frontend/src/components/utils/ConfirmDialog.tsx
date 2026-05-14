import type React from 'react'

interface ConfirmDialogProps {
  title: string
  message: React.ReactNode
  confirmLabel: string
  pending?: boolean
  onConfirm: () => void
  onClose: () => void
}

/**
 * Standard confirmation modal used across the site (event cancel, event delete,
 * comment delete, draft delete, …). Matches the visual pattern of the historical
 * local `ConfirmDialog` in `EventDetailPage` and `ConfirmModal` in
 * `MyPublicationsPage` — they can be progressively migrated to this shared
 * component when those files are touched.
 *
 * Use over `globalThis.confirm()` which is the browser-native (ugly) alert.
 */
export default function ConfirmDialog({
  title,
  message,
  confirmLabel,
  pending = false,
  onConfirm,
  onClose,
}: Readonly<ConfirmDialogProps>) {
  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-background border border-border rounded-3xl p-8 max-w-sm w-[90%] shadow-2xl">
        <h2 className="text-lg font-bold text-foreground mb-2">{title}</h2>
        <div className="text-sm text-foreground/50 mb-6">{message}</div>
        <div className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="px-4 py-2.5 rounded-xl border border-border text-foreground text-sm font-semibold disabled:opacity-50 hover:border-foreground/30 transition-colors cursor-pointer bg-transparent"
          >
            Annuler
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className="px-4 py-2.5 rounded-xl bg-error text-white text-sm font-semibold disabled:opacity-50 hover:bg-error/80 transition-colors cursor-pointer border-0"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
