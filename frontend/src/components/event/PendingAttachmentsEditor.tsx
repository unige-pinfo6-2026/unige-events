import { useRef } from 'react'
import { Paperclip, X } from 'lucide-react'
import {
  ATTACHMENT_ACCEPT_ATTR,
  ATTACHMENT_MAX_BYTES,
  ATTACHMENT_MAX_PER_EVENT,
  isAcceptedAttachmentFile,
} from '@/types/attachment'
import { formatFileSize } from '@/utils/formatFileSize'

/**
 * SCRUM-149 — file staged client-side in `EventCreatePage` before the event
 * exists in the backend. After `POST /events` succeeds, the parent dispatches
 * one `POST /events/{id}/attachments` per entry via `uploadEventAttachment`.
 */
export interface PendingAttachment {
  id: string
  file: File
  /** Client-side validation error (mime / size / overflow), or null if accepted. */
  error: string | null
}

interface Props {
  pending: PendingAttachment[]
  onAdd: (entries: PendingAttachment[]) => void
  onRemove: (id: string) => void
}

const ERROR_TOO_LARGE = `Ce fichier dépasse la taille maximale autorisée (${formatFileSize(ATTACHMENT_MAX_BYTES)}).`
const ERROR_BAD_TYPE = 'Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.'
const ERROR_LIMIT_REACHED = `Limite atteinte : ${ATTACHMENT_MAX_PER_EVENT} fichiers maximum par événement.`

function validate(file: File, overflow: boolean): string | null {
  if (!isAcceptedAttachmentFile(file)) return ERROR_BAD_TYPE
  if (file.size > ATTACHMENT_MAX_BYTES) return ERROR_TOO_LARGE
  if (overflow) return ERROR_LIMIT_REACHED
  return null
}

/**
 * "Fichiers joints" section embedded in `EventForm` create mode. Unlike the
 * edit-mode `EventAttachmentsEditor`, this variant cannot call
 * `POST /events/{id}/attachments` (no event yet) — it stages entries client
 * side and exposes them via `onAdd` / `onRemove` so `EventCreatePage` can
 * upload them after the event is created (same pattern as
 * `PendingCoOrganizersEditor`).
 */
export default function PendingAttachmentsEditor({ pending, onAdd, onRemove }: Readonly<Props>) {
  const inputRef = useRef<HTMLInputElement>(null)
  const acceptedCount = pending.filter((p) => p.error === null).length

  function handleFilesPicked(picked: FileList | null) {
    if (!picked || picked.length === 0) return
    const remainingSlots = Math.max(0, ATTACHMENT_MAX_PER_EVENT - acceptedCount)
    const next: PendingAttachment[] = Array.from(picked).map((file, idx) => ({
      id: `${Date.now()}-${idx}-${file.name}`,
      file,
      error: validate(file, idx >= remainingSlots),
    }))
    onAdd(next)
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <section
      aria-label="Fichiers joints"
      className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border"
    >
      <header className="flex items-center gap-3 mb-4">
        <div className="w-10 h-10 rounded-2xl bg-linear-to-br from-accent/30 to-pink-600/30 flex items-center justify-center">
          <Paperclip className="w-5 h-5 text-accent" />
        </div>
        <div className="min-w-0">
          <h3 className="text-lg font-semibold text-foreground">Fichiers joints</h3>
          <p className="text-xs text-foreground/60">
            PDF, DOC, DOCX, XLSX, PNG, JPEG — {formatFileSize(ATTACHMENT_MAX_BYTES)} max par fichier, {ATTACHMENT_MAX_PER_EVENT} fichiers max par événement. Les fichiers seront uploadés après la création de l'événement.
          </p>
        </div>
      </header>

      <div className="flex flex-col gap-4">
        <label
          htmlFor="pending-event-attachments-input"
          className="inline-flex w-fit items-center gap-2 px-4 py-2.5 rounded-xl border border-border text-sm font-semibold text-foreground cursor-pointer hover:border-foreground/30 transition-colors"
        >
          <Paperclip className="w-4 h-4" />
          Choisir des fichiers
          <input
            id="pending-event-attachments-input"
            ref={inputRef}
            type="file"
            multiple
            accept={ATTACHMENT_ACCEPT_ATTR}
            onChange={(e) => handleFilesPicked(e.target.files)}
            className="sr-only"
          />
        </label>

        {pending.length === 0 ? (
          <p className="text-sm text-foreground/50 italic">Aucun fichier joint pour le moment.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {pending.map((entry) => (
              <PendingRow key={entry.id} entry={entry} onRemove={() => onRemove(entry.id)} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function PendingRow({
  entry,
  onRemove,
}: Readonly<{ entry: PendingAttachment; onRemove: () => void }>) {
  return (
    <li
      className={`flex items-center gap-3 p-3 rounded-2xl border bg-background/30 ${entry.error ? 'border-error/40' : 'border-border'}`}
    >
      <Paperclip className="w-4 h-4 text-foreground/40 shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">{entry.file.name}</p>
        <p className="text-xs text-foreground/50">{formatFileSize(entry.file.size)}</p>
        {entry.error && <p className="text-xs text-error mt-1">{entry.error}</p>}
      </div>
      <span className={`px-2 py-1 rounded-lg text-xs font-medium border ${entry.error ? 'bg-error/10 text-error border-error/30' : 'bg-warning/10 text-warning border-warning/30'}`}>
        {entry.error ? 'Refusé' : 'À uploader'}
      </span>
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Retirer ${entry.file.name}`}
        className="p-2 rounded-lg text-foreground/40 hover:text-error hover:bg-error/10 transition-colors"
      >
        <X className="w-4 h-4" />
      </button>
    </li>
  )
}
