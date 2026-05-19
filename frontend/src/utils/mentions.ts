/**
 * SCRUM-147 — pure helpers for the inline {@code @<prefix>} mention
 * autocomplete. Live outside the component file so the lint rule
 * `react-refresh/only-export-components` doesn't complain about a non-
 * component export ; also makes the parser unit-testable without React.
 */

/** Charset matching the SCRUM-169 username regex `[a-z0-9._-]{3,30}`, but
 *  accepted case-insensitively at type-time (the result is lowercased
 *  before any backend hit). */
export const HANDLE_CHARSET = /[a-zA-Z0-9._-]/

export interface ActiveMention {
  /** Inclusive index of the `@` in the underlying value. */
  atIndex: number
  /** The prefix the user has typed since the `@`, lowercased. */
  prefix: string
}

/**
 * Detects the {@code @<prefix>} token currently around the caret. Returns
 * {@code null} when there is no active mention.
 *
 * <p>Walks backwards from {@code caretPos - 1} one char at a time :
 * <ul>
 *   <li>If the char is in {@link HANDLE_CHARSET}, keep going.</li>
 *   <li>If the char is {@code @} AND the char immediately before it is
 *       NOT a word character — i.e. {@code @} starts a new token — the
 *       match is valid.</li>
 *   <li>Anything else (whitespace, end of charset) → no active mention.</li>
 * </ul>
 *
 * <p>The "char before {@code @} is not a word char" rule keeps
 * {@code email@example.com} from triggering a mention.
 */
export function detectActiveMention(value: string, caretPos: number): ActiveMention | null {
  if (caretPos < 1) return null
  let i = caretPos - 1
  while (i >= 0 && HANDLE_CHARSET.test(value[i])) {
    i--
  }
  if (i < 0 || value[i] !== '@') return null
  if (i > 0) {
    const before = value[i - 1]
    if (/\w/.test(before)) return null
  }
  return { atIndex: i, prefix: value.slice(i + 1, caretPos).toLowerCase() }
}
