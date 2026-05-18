import { useRef, useState } from 'react'
import axios from 'axios'
import { Download, Paperclip, Upload, X } from 'lucide-react'
import { ButtonPrimary } from '@/components/utils/Buttons'
import {
  ATTACHMENT_ACCEPT_ATTR,
  ATTACHMENT_MAX_BYTES,
  ATTACHMENT_MAX_PER_EVENT,
  isAcceptedAttachmentFile,
  type Attachment,
} from '@/types/attachment'
import { deleteEventAttachment, uploadEventAttachment } from '@/services/attachmentApi'
import { formatFileSize } from '@/utils/formatFileSize'

interface EventAttachmentsEditorProps {
  eventId: number
  attachments: Attachment[]
  onChange: (next: Attachment[]) => void
}

interface StagedFile {
  id: string
  file: File
  /** Filled after a failed validation or backend rejection. */
  error: string | null
}

const ERROR_TOO_LARGE = `Ce fichier dépasse la taille maximale autorisée (${formatFileSize(ATTACHMENT_MAX_BYTES)}).`
const ERROR_BAD_TYPE = 'Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.'
const ERROR_LIMIT_REACHED = `Limite atteinte : ${ATTACHMENT_MAX_PER_EVENT} fichiers maximum par événement.`
const ERROR_GENERIC_UPLOAD = "L'upload de ce fichier a échoué."
const ERROR_GENERIC_DELETE = 'La suppression du fichier a échoué.'

/**
 * SCRUM-149 — "Fichiers joints" section embedded in the EventForm during
 * edit mode (the event must have an ID). Stage-then-upload UX: the user
 * picks files into a local list, then clicks "Uploader" to send them one
 * by one. Each upload reports its own error so a partial failure doesn't
 * block the others.
 *
 * Client validation mirrors the backend whitelist + limits (PDF/DOC/DOCX/
 * XLSX, 10 MiB, 5 attachments max). Backend rejections (422 / 413 / 415)
 * are surfaced per row without crashing the UI.
 */
export default function EventAttachmentsEditor({ eventId, attachments, onChange }: Readonly<EventAttachmentsEditorProps>) {
  const [staged, setStaged] = useState<StagedFile[]>([])
  const [uploading, setUploading] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<{ id: number; message: string } | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const uploadedCount = attachments.length
  const acceptedStaged = staged.filter((s) => s.error === null)
  // Cap is server-enforced on actual uploads — only accepted staged files
  // count toward it. A row that was rejected client-side (bad mime, too
  // big, prior overflow) must not block otherwise-valid pending files.
  const limitExceeded = uploadedCount + acceptedStaged.length > ATTACHMENT_MAX_PER_EVENT
  const canUpload = !uploading && acceptedStaged.length > 0 && !limitExceeded

  function handleFilesPicked(picked: FileList | null) {
    if (!picked || picked.length === 0) return
    const remainingSlots = Math.max(0, ATTACHMENT_MAX_PER_EVENT - uploadedCount - acceptedStaged.length)
    const next: StagedFile[] = []
    Array.from(picked).forEach((file, idx) => {
      const overflow = idx >= remainingSlots
      next.push({
        id: `${Date.now()}-${idx}-${file.name}`,
        file,
        error: validate(file, overflow),
      })
    })
    setStaged((prev) => [...prev, ...next])
    if (inputRef.current) inputRef.current.value = ''
  }

  function removeStaged(stagedId: string) {
    setStaged((prev) => prev.filter((s) => s.id !== stagedId))
  }

  async function handleUpload() {
    if (!canUpload) return
    setUploading(true)
    const succeeded: Attachment[] = []
    const failed: StagedFile[] = []

    for (const item of acceptedStaged) {
      try {
        const uploaded = await uploadEventAttachment(eventId, item.file)
        succeeded.push(uploaded)
      } catch (error) {
        failed.push({ ...item, error: getUploadErrorMessage(error) })
      }
    }

    if (succeeded.length > 0) {
      onChange([...attachments, ...succeeded])
    }
    // Keep the rejected-on-the-client items (mime/size/limit) AND the
    // backend-failed ones so the user can fix or retry. Successful ones
    // are removed from staging.
    setStaged((prev) => {
      const stillRejectedClient = prev.filter((s) => s.error !== null && !acceptedStaged.includes(s))
      return [...stillRejectedClient, ...failed]
    })
    setUploading(false)
  }

  async function handleDelete(attachment: Attachment) {
    if (deletingId !== null) return
    setDeletingId(attachment.id)
    setDeleteError(null)
    try {
      await deleteEventAttachment(eventId, attachment.id)
      onChange(attachments.filter((a) => a.id !== attachment.id))
    } catch (error) {
      setDeleteError({ id: attachment.id, message: getDeleteErrorMessage(error) })
    } finally {
      setDeletingId(null)
    }
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
            PDF, DOC, DOCX, XLSX, PNG, JPEG — {formatFileSize(ATTACHMENT_MAX_BYTES)} max par fichier, {ATTACHMENT_MAX_PER_EVENT} fichiers max par événement.
          </p>
        </div>
      </header>

      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-start gap-3">
          <label
            htmlFor="event-attachments-input"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-border text-sm font-semibold text-foreground cursor-pointer hover:border-foreground/30 transition-colors"
          >
            <Paperclip className="w-4 h-4" />
            Choisir des fichiers
            <input
              id="event-attachments-input"
              ref={inputRef}
              type="file"
              multiple
              accept={ATTACHMENT_ACCEPT_ATTR}
              onChange={(e) => handleFilesPicked(e.target.files)}
              disabled={uploading}
              className="sr-only"
            />
          </label>
          <ButtonPrimary
            onClick={() => { void handleUpload() }}
            disabled={!canUpload}
            size="md"
          >
            <Upload className="w-4 h-4" />
            {uploading ? 'Envoi…' : 'Uploader'}
          </ButtonPrimary>
          {limitExceeded && (
            <p className="text-xs text-error self-center">{ERROR_LIMIT_REACHED}</p>
          )}
        </div>

        {staged.length > 0 && (
          <div>
            <h4 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-2">
              Fichiers à uploader
            </h4>
            <ul className="flex flex-col gap-2">
              {staged.map((item) => (
                <StagedRow
                  key={item.id}
                  staged={item}
                  disabled={uploading}
                  onRemove={() => removeStaged(item.id)}
                />
              ))}
            </ul>
          </div>
        )}

        <div>
          <h4 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-2">
            Fichiers joints à l'événement
          </h4>
          {attachments.length === 0 ? (
            <p className="text-sm text-foreground/50 italic">Aucun fichier joint pour le moment.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {attachments.map((att) => (
                <UploadedRow
                  key={att.id}
                  attachment={att}
                  deleting={deletingId === att.id}
                  disabled={deletingId !== null && deletingId !== att.id}
                  error={deleteError?.id === att.id ? deleteError.message : null}
                  onDelete={() => { void handleDelete(att) }}
                />
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  )
}

function validate(file: File, overflow: boolean): string | null {
  if (!isAcceptedAttachmentFile(file)) return ERROR_BAD_TYPE
  if (file.size > ATTACHMENT_MAX_BYTES) return ERROR_TOO_LARGE
  if (overflow) return ERROR_LIMIT_REACHED
  return null
}

function StagedRow({
  staged,
  disabled,
  onRemove,
}: Readonly<{ staged: StagedFile; disabled: boolean; onRemove: () => void }>) {
  return (
    <li
      className={`flex items-center gap-3 p-3 rounded-2xl border bg-background/30 ${staged.error ? 'border-error/40' : 'border-border'}`}
    >
      <Paperclip className="w-4 h-4 text-foreground/40 shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">{staged.file.name}</p>
        <p className="text-xs text-foreground/50">{formatFileSize(staged.file.size)}</p>
        {staged.error && <p className="text-xs text-error mt-1">{staged.error}</p>}
      </div>
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Retirer ${staged.file.name}`}
        disabled={disabled}
        className="p-2 rounded-lg text-foreground/40 hover:text-error hover:bg-error/10 transition-colors disabled:opacity-50"
      >
        <X className="w-4 h-4" />
      </button>
    </li>
  )
}

function UploadedRow({
  attachment,
  deleting,
  disabled,
  error,
  onDelete,
}: Readonly<{
  attachment: Attachment
  deleting: boolean
  disabled: boolean
  error: string | null
  onDelete: () => void
}>) {
  return (
    <li
      className={`flex items-center gap-3 p-3 rounded-2xl border bg-background/30 ${error ? 'border-error/40' : 'border-border'}`}
    >
      <Paperclip className="w-4 h-4 text-foreground/40 shrink-0" />
      <div className="flex-1 min-w-0">
        <a
          href={attachment.downloadUrl}
          download={attachment.fileName}
          className="text-sm font-medium text-foreground hover:text-accent truncate block no-underline"
        >
          {attachment.fileName}
        </a>
        <p className="text-xs text-foreground/50">{formatFileSize(attachment.fileSize)}</p>
        {error && <p className="text-xs text-error mt-1">{error}</p>}
      </div>
      <a
        href={attachment.downloadUrl}
        download={attachment.fileName}
        aria-label={`Télécharger ${attachment.fileName}`}
        className="p-2 rounded-lg text-foreground/40 hover:text-accent hover:bg-accent/10 transition-colors no-underline"
      >
        <Download className="w-4 h-4" />
      </a>
      <button
        type="button"
        onClick={onDelete}
        aria-label={`Supprimer ${attachment.fileName}`}
        disabled={deleting || disabled}
        className="p-2 rounded-lg text-foreground/40 hover:text-error hover:bg-error/10 transition-colors disabled:opacity-50"
      >
        <X className="w-4 h-4" />
      </button>
    </li>
  )
}

function getUploadErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const code = typeof error.response?.data?.code === 'string' ? error.response.data.code : null
    if (code === 'attachment_invalid_size' || status === 413) return ERROR_TOO_LARGE
    if (code === 'attachment_invalid_type' || status === 415) return ERROR_BAD_TYPE
    if (code === 'attachment_limit_exceeded') return ERROR_LIMIT_REACHED
    const message = error.response?.data?.message
    if (typeof message === 'string' && message.trim()) return message.trim()
  }
  return ERROR_GENERIC_UPLOAD
}

function getDeleteErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message
    if (typeof message === 'string' && message.trim()) return message.trim()
  }
  return ERROR_GENERIC_DELETE
}
