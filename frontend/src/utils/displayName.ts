/**
 * Returns a sensible label for a user when `displayName` is null.
 *
 * Order of fallbacks:
 * 1. trimmed `displayName` if non-empty
 * 2. first 8 chars of the UUID (e.g. "a1b2c3d4") if provided — lisible
 *    sans fuiter l'UUID complet
 * 3. literal `"Utilisateur"` as a last resort
 *
 * Used by `CommentItem`, `EventOrganizerTeam`, and any future call site that
 * needs to display a user identity without an authoritative `displayName`.
 *
 * **Follow-up**: replace UUID short with @username once the username system
 * is implemented (tracked as separate ticket, post-PR-170).
 */
export function userDisplayLabel(
  displayName: string | null | undefined,
  userId?: string | null,
): string {
  const trimmed = displayName?.trim()
  if (trimmed) return trimmed
  if (userId) return userId.slice(0, 8)
  return 'Utilisateur'
}

/** Returns the 2-char initials for an avatar fallback (uppercased). */
export function userInitials(displayName: string | null | undefined): string {
  const trimmed = displayName?.trim()
  if (!trimmed) return '??'
  return trimmed.slice(0, 2).toUpperCase()
}
