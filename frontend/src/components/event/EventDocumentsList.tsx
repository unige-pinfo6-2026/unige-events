import { Download, FileText } from 'lucide-react'
import type { Attachment } from '@/types/attachment'
import { formatFileSize } from '@/utils/formatFileSize'

interface EventDocumentsListProps {
  attachments: Attachment[]
}

/**
 * SCRUM-149 — "Documents" section affichée sur `EventDetailPage` quand
 * `event.attachments.length > 0`. Visible pour tous (auth ET non-auth),
 * pas d'auth gate.
 *
 * Le lien pointe sur `attachment.downloadUrl` (endpoint API same-origin
 * `GET /api/events/{eventId}/attachments/{id}/download`) qui streame
 * l'objet depuis MinIO avec `Content-Disposition: attachment` — le
 * navigateur force le téléchargement, et l'URL est routable contrairement
 * au `fileUrl` interne (`minio:9000`).
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
            <a
              href={att.downloadUrl}
              download={att.fileName}
              aria-label={`Télécharger ${att.fileName}`}
              className="w-full flex items-center gap-3 p-3 rounded-2xl border border-border bg-background/30 hover:border-foreground/30 hover:bg-background/50 transition-colors text-left no-underline"
            >
              <FileText className="w-4 h-4 text-foreground/40 shrink-0" />
              <span className="text-sm font-medium text-foreground truncate flex-1 min-w-0">
                {att.fileName}
              </span>
              <span className="text-xs text-foreground/50 shrink-0">
                {formatFileSize(att.fileSize)}
              </span>
              <Download className="w-4 h-4 text-foreground/40 shrink-0" />
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
