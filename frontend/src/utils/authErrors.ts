/**
 * Auth0 OAuth error codes that signal "the session is gone, the user is no
 * longer authenticated" — i.e. nothing is wrong with the network or the
 * backend, the refresh token is just expired / revoked / missing.
 *
 * The SPA SDK throws these as instances of `OAuthError` / `GenericError` /
 * `MissingRefreshTokenError`, all carrying an `error: string` field with one
 * of the codes below. We treat them all as "silently log the user out" —
 * see ISSUE-107 (no error toast for an expired session).
 *
 * Source : auth0-spa-js error classes (`MissingRefreshTokenError`,
 * `OAuthError`) + the standard OAuth 2.0 / OIDC error responses that the
 * SDK forwards verbatim from Auth0's `/oauth/token` and `/authorize`
 * endpoints when the SSO session can't be silently renewed.
 */
export const AUTH_SESSION_EXPIRED_CODES = [
  // Auth0 returns this on `/authorize?prompt=none` when the user has no
  // active SSO session (cookie expired or wiped). The SDK forwards it as
  // an OAuthError on `getAccessTokenSilently`.
  'login_required',
  // `/oauth/token` returns this when the supplied refresh token is no
  // longer valid (expired absolute lifetime, rotated, or revoked).
  'invalid_grant',
  // SSO session exists but consent must be re-collected interactively.
  'consent_required',
  // SSO session exists but an MFA challenge / step-up is needed.
  'interaction_required',
  // SDK-specific: no refresh token is stored locally (typical when
  // localStorage was cleared by the user or a different tab). Comes from
  // `MissingRefreshTokenError`.
  'missing_refresh_token',
] as const

type AuthSessionExpiredCode = (typeof AUTH_SESSION_EXPIRED_CODES)[number]

/**
 * Returns `true` when the error thrown by `getAccessTokenSilently` (or any
 * downstream call) means "the Auth0 session is expired/absent, fall back to
 * unauthenticated state silently". Returns `false` for everything else —
 * network errors, 5xx, unexpected exceptions — which should still surface
 * as a visible error.
 *
 * The predicate is defensive: it accepts anything (`unknown`) and only
 * matches when the value is an object carrying an `error` field whose
 * string value is one of {@link AUTH_SESSION_EXPIRED_CODES}.
 */
export function isAuthSessionExpiredError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const code = (err as { error?: unknown }).error
  if (typeof code !== 'string') return false
  return (AUTH_SESSION_EXPIRED_CODES as readonly string[]).includes(code as AuthSessionExpiredCode)
}
