/**
 * SCRUM-148 — Fichier joint à un événement.
 *
 * Whitelist MIME stricte côté backend (PDF / DOC / DOCX / XLSX), max 10 MiB
 * par fichier, max 5 attachments par événement. `fileUrl` est un path S3
 * absolu directement consommable comme URL de téléchargement (bucket
 * public-read), au même titre que `bannerUrl` ou `avatarUrl`.
 */
export interface Attachment {
  id: number
  fileName: string
  fileUrl: string
  fileSize: number
  mimeType: AttachmentMimeType
  uploadedById: string
  uploadedAt: string
}

export const ATTACHMENT_MIME_TYPES = {
  'application/pdf':                                                            { label: 'PDF',  extension: '.pdf'  },
  'application/msword':                                                         { label: 'DOC',  extension: '.doc'  },
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document':    { label: 'DOCX', extension: '.docx' },
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':          { label: 'XLSX', extension: '.xlsx' },
} as const

export type AttachmentMimeType = keyof typeof ATTACHMENT_MIME_TYPES

export const ATTACHMENT_ACCEPT_ATTR = '.pdf,.doc,.docx,.xlsx'
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024 // 10 MiB — SCRUM-148
export const ATTACHMENT_MAX_PER_EVENT = 5

const EXTENSION_TO_MIME: Record<string, AttachmentMimeType> = {
  pdf:  'application/pdf',
  doc:  'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}

/**
 * Le navigateur ne remplit pas toujours `file.type` (selon l'OS / le drag &
 * drop / l'extension réelle). Pour rester aussi tolérant que possible tout
 * en mirrorant la whitelist serveur, on accepte si **soit** le `mimeType`
 * **soit** l'extension est dans la liste — c'est ce que fait l'attribut
 * `accept` HTML.
 */
export function isAcceptedAttachmentFile(file: File): boolean {
  if ((file.type as AttachmentMimeType) in ATTACHMENT_MIME_TYPES) return true
  const dot = file.name.lastIndexOf('.')
  if (dot === -1) return false
  const ext = file.name.slice(dot + 1).toLowerCase()
  return ext in EXTENSION_TO_MIME
}
