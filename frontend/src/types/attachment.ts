/**
 * SCRUM-148 + SCRUM-149 follow-up — Fichier joint à un événement.
 *
 * Whitelist MIME stricte côté backend (PDF / DOC / DOCX / XLSX + PNG / JPEG
 * depuis SCRUM-149), max 10 MiB par fichier, max 5 attachments par événement.
 *
 * **URL à utiliser côté frontend : `downloadUrl`** (endpoint API same-origin
 * `/api/events/{eventId}/attachments/{id}/download` qui streame depuis MinIO
 * avec `Content-Disposition: attachment`). `fileUrl` reste exposé pour parité
 * avec le DTO backend mais pointe sur l'endpoint MinIO interne (`minio:9000`)
 * et n'est **pas** consommable par le navigateur — c'est la différence
 * principale avec `bannerUrl` / `avatarUrl` qui, eux, sont des paths S3
 * directement chargeables comme `<img src>` (les buckets correspondants
 * sont accessibles par d'autres voies). Voir le commentaire détaillé sur
 * chaque champ ci-dessous.
 */
export interface Attachment {
  id: number
  fileName: string
  /**
   * Raw internal S3 URL. **Ne pas utiliser côté navigateur** : pointe sur
   * l'endpoint MinIO interne (`minio:9000`) qui n'est pas exposé
   * publiquement. Champ conservé pour parité avec l'`AttachmentDTO`
   * backend. Le frontend doit utiliser `downloadUrl` à la place
   * (SCRUM-149 follow-up).
   */
  fileUrl: string
  /**
   * URL same-origin (`/api/events/{eventId}/attachments/{id}/download`)
   * à utiliser pour télécharger le fichier. Le backend relit l'objet
   * depuis MinIO puis le streame avec
   * `Content-Disposition: attachment; filename="..."` → le navigateur
   * force le téléchargement (SCRUM-149 follow-up).
   */
  downloadUrl: string
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
  // SCRUM-149 — PNG / JPEG ajoutés en plus des documents pour permettre
  // des visuels (posters, captures) en tant que pièces jointes. La voie
  // backend reste `saveFile` (header-only validation, cf. Décision S).
  'image/png':                                                                  { label: 'PNG',  extension: '.png'  },
  'image/jpeg':                                                                 { label: 'JPEG', extension: '.jpg'  },
} as const

export type AttachmentMimeType = keyof typeof ATTACHMENT_MIME_TYPES

export const ATTACHMENT_ACCEPT_ATTR = '.pdf,.doc,.docx,.xlsx,.png,.jpg,.jpeg'
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024 // 10 MiB — SCRUM-148
export const ATTACHMENT_MAX_PER_EVENT = 5

const EXTENSION_TO_MIME: Record<string, AttachmentMimeType> = {
  pdf:  'application/pdf',
  doc:  'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  png:  'image/png',
  jpg:  'image/jpeg',
  jpeg: 'image/jpeg',
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
