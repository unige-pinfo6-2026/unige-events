/**
 * Returns a sensible label for a user when `displayName` is null.
 *
 * Order of fallbacks (SCRUM-169) :
 * 1. trimmed `displayName` if non-empty
 * 2. `"@" + username` if provided — public-facing identifier, lisible
 * 3. first 8 chars of the UUID if provided — soft transition for call
 *    sites where `username` isn't yet wired into the DTO (e.g. comments
 *    pending their own back-fill of `authorUsername`)
 * 4. literal `"Utilisateur"` as a last resort
 *
 * Used by `CommentItem`, `EventOrganizerTeam`, and any future call site
 * that needs to display a user identity without an authoritative
 * `displayName`.
 */
export function userDisplayLabel(
  displayName: string | null | undefined,
  username?: string | null,
  userId?: string | null,
): string {
  const trimmed = displayName?.trim()
  if (trimmed) return trimmed
  if (username) return '@' + username
  if (userId) return userId.slice(0, 8)
  return 'Utilisateur'
}

/** Returns the 2-char initials for an avatar fallback (uppercased). */
export function userInitials(displayName: string | null | undefined): string {
  const trimmed = displayName?.trim()
  if (!trimmed) return '??'
  return trimmed.slice(0, 2).toUpperCase()
}
