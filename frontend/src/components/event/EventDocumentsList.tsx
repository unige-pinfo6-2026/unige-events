import { FileText } from 'lucide-react'
import type { Attachment } from '@/types/attachment'
import { formatFileSize } from '@/utils/formatFileSize'

interface EventDocumentsListProps {
  attachments: Attachment[]
}

/**
 * SCRUM-149 — "Documents" section affichée sur `EventDetailPage` quand
 * `event.attachments.length > 0`. Visible pour tous (auth ET non-auth),
 * pas d'auth gate. `fileUrl` est un path S3 public-read absolu
 * (cf. AttachmentDTO dans openapi.yaml) — même convention que
 * `bannerUrl` / `avatarUrl` : rendu direct sans helper.
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
          <li
            key={att.id}
            className="flex items-center gap-3 p-3 rounded-2xl border border-border bg-background/30 hover:border-foreground/30 transition-colors"
          >
            <FileText className="w-4 h-4 text-foreground/40 shrink-0" />
            <a
              href={att.fileUrl}
              download={att.fileName}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm font-medium text-foreground hover:text-accent no-underline truncate flex-1 min-w-0"
            >
              {att.fileName}
            </a>
            <span className="text-xs text-foreground/50 shrink-0">
              {formatFileSize(att.fileSize)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
