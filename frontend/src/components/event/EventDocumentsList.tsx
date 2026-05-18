import { FileText, Download } from 'lucide-react'
import type { Attachment } from '@/types/attachment'
import { formatFileSize } from '@/utils/formatFileSize'
import { downloadAttachment } from '@/utils/downloadAttachment'

interface EventDocumentsListProps {
  attachments: Attachment[]
}

/**
 * SCRUM-149 — "Documents" section affichée sur `EventDetailPage` quand
 * `event.attachments.length > 0`. Visible pour tous (auth ET non-auth),
 * pas d'auth gate. Le clic sur une row déclenche un téléchargement
 * forcé via `downloadAttachment` (fetch → blob → ancre synthétique) :
 * indispensable car `<a download>` est ignoré sur les URLs cross-origin
 * (l'URL pointe sur MinIO en S3 public-read).
 */
export default function EventDocumentsList({ attachments }: Readonly<EventDocumentsListProps>) {
  if (attachments.length === 0) return null

  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-6 border border-border">
      <h2 className="text-xs font-bold uppercase tracking-widest text-foreground/30 mb-3">
        Documents
      </h2>
      <ul className="flex flex-col gap-2">
        {attachments.map((att) => (
          <li key={att.id}>
            <button
              type="button"
              onClick={() => { void downloadAttachment(att.fileUrl, att.fileName) }}
              aria-label={`Télécharger ${att.fileName}`}
              className="w-full flex items-center gap-3 p-3 rounded-2xl border border-border bg-background/30 hover:border-foreground/30 hover:bg-background/50 transition-colors text-left cursor-pointer"
            >
              <FileText className="w-4 h-4 text-foreground/40 shrink-0" />
              <span className="text-sm font-medium text-foreground truncate flex-1 min-w-0">
                {att.fileName}
              </span>
              <span className="text-xs text-foreground/50 shrink-0">
                {formatFileSize(att.fileSize)}
              </span>
              <Download className="w-4 h-4 text-foreground/40 shrink-0" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
