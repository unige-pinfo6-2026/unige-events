/**
 * SCRUM-149 — force-download an event attachment from its public S3 URL.
 *
 * Le navigateur ignore l'attribut `<a download>` quand le href est
 * cross-origin (spec HTML), donc cliquer sur un lien direct vers MinIO
 * ouvre le fichier inline dans un nouvel onglet au lieu de déclencher
 * un téléchargement. On contourne via un `fetch` → `Blob` → URL
 * d'objet → ancre synthétique cliquée puis révoquée.
 *
 * Si le `fetch` échoue (CORS bloqué côté MinIO, réseau, 4xx/5xx),
 * fallback transparent vers `window.open(url, '_blank')` — comportement
 * équivalent à avant : l'utilisateur voit le fichier dans un nouvel
 * onglet. Mieux qu'une erreur silencieuse.
 */
export async function downloadAttachment(url: string, fileName: string): Promise<void> {
  try {
    const response = await fetch(url, { mode: 'cors', credentials: 'omit' })
    if (!response.ok) throw new Error('non-2xx response: ' + response.status)
    const blob = await response.blob()
    triggerBlobDownload(blob, fileName)
  } catch {
    globalThis.open(url, '_blank', 'noopener,noreferrer')
  }
}

function triggerBlobDownload(blob: Blob, fileName: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  anchor.rel = 'noopener noreferrer'
  // Some browsers (Safari) require the anchor to be in the DOM to honor click().
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Revoke on the next tick — Safari needs the URL alive long enough for
  // the click to register.
  setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
}
