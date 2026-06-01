/**
 * Returns the canonical form of {@code value} iff it parses as an absolute
 * {@code http(s)} URL, otherwise {@code null}.
 *
 * <p>The backend stores free-text URLs (e.g. `event.websiteUrl`) validated by
 * Hibernate `@URL`, which still accepts dangerous schemes like
 * {@code javascript:} or {@code data:}. Anything that is not explicitly
 * {@code http:}/{@code https:} (or fails to parse) is rejected so callers can
 * fall back to rendering the raw string as plain text — never an `<a href>`.
 * This guards against XSS / open-redirect via crafted comment or profile URLs.
 */
export function safeHttpUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}
