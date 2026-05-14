/**
 * RFC 4122 UUID matcher (any version, any variant).
 *
 * Used by route components that take an `:id` path param and want to fail
 * fast on a malformed UUID instead of round-tripping to the API only to
 * receive a 400 / 404.
 */
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function isUuid(value: string | undefined | null): value is string {
  if (typeof value !== 'string') return false
  return UUID_REGEX.test(value)
}
