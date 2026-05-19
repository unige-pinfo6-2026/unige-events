/**
 * Format a byte count as a short human-readable string.
 *
 * Uses 1024-based units (KB / MB) consistent with the backend limit of
 * 10 MiB (10 485 760 bytes — SCRUM-148). Falls back to "0 B" for invalid
 * or non-positive inputs.
 */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const KB = 1024
  const MB = KB * 1024
  if (bytes < KB) return `${Math.round(bytes)} B`
  if (bytes < MB) return `${(bytes / KB).toFixed(1)} KB`
  return `${(bytes / MB).toFixed(1)} MB`
}
