/**
 * SCRUM-147 — pure helpers for the inline {@code @<prefix>} mention
 * autocomplete. Live outside the component file so the lint rule
 * `react-refresh/only-export-components` doesn't complain about a non-
 * component export ; also makes the parser unit-testable without React.
 */

import { safeHttpUrl } from '@/utils/url'

/** Charset matching the SCRUM-169 username regex `[a-z0-9._-]{3,30}`, but
 *  accepted case-insensitively at type-time (the result is lowercased
 *  before any backend hit). */
export const HANDLE_CHARSET = /[a-zA-Z0-9._-]/

/** Matches a `@<handle>` token where the `@` is at start-of-string or follows
 *  a non-word character (mirrors {@link detectActiveMention}'s "char before
 *  is not a word char" rule — keeps `email@example.com` from matching).
 *  Capture group 1 is the bare handle (no leading `@`). The lower-bound of
 *  3 chars matches the SCRUM-169 username minimum. */
export const MENTION_RENDER_REGEX = /(?:^|[^\w])@([a-zA-Z0-9._-]{3,30})/g

export interface MentionSegment {
  kind: 'text' | 'mention'
  /** Raw text for `text` ; bare handle (no `@`) for `mention`. */
  value: string
}

/**
 * Splits a comment body into renderable segments — plain text + mention
 * tokens — so the UI can wrap mentions in {@code <Link to=/profile/handle>}
 * while preserving everything else as-is (including whitespace, newlines,
 * adjacent punctuation).
 *
 * <p>The token itself in the output preserves the original casing as typed
 * by the user (display only) — the link target is lowercased separately
 * by the caller to match the stored handle convention.
 */
export function splitMentions(content: string): MentionSegment[] {
  if (!content) return []
  const segments: MentionSegment[] = []
  let lastIndex = 0
  // Reset lastIndex defensively — `MENTION_RENDER_REGEX` is shared and
  // stateful (global flag).
  const re = new RegExp(MENTION_RENDER_REGEX.source, 'g')
  let match: RegExpExecArray | null
  while ((match = re.exec(content)) !== null) {
    const handle = match[1]
    const atIndex = match.index + match[0].length - handle.length - 1
    if (atIndex > lastIndex) {
      segments.push({ kind: 'text', value: content.slice(lastIndex, atIndex) })
    }
    segments.push({ kind: 'mention', value: handle })
    lastIndex = atIndex + 1 + handle.length
  }
  if (lastIndex < content.length) {
    segments.push({ kind: 'text', value: content.slice(lastIndex) })
  }
  return segments
}

export interface ContentSegment {
  kind: 'text' | 'mention' | 'url'
  /** Raw text for `text` ; bare handle (no `@`) for `mention` ; a safe http(s)
   *  URL for `url` (validated by `safeHttpUrl` here, so the caller can render it
   *  straight into an `href`). */
  value: string
}

/** Matches an absolute http(s) URL run (no whitespace). Trailing sentence
 *  punctuation is trimmed afterwards so `https://x.com.` doesn't swallow the
 *  period. */
const URL_RUN_REGEX = /https?:\/\/[^\s]+/gi
/** Punctuation that commonly trails a URL in prose but isn't part of it.
 *  Trimmed with a linear character scan rather than a `…+$` regex (which Sonar
 *  flags as ReDoS-sensitive, S5852). */
const TRAILING_PUNCT = new Set(['.', ',', '!', '?', ';', ':', ')', ']', '}', "'", '"'])

/** Splits a matched URL run into its real URL + the trailing prose punctuation
 *  (`https://x.com.` → `https://x.com` + `.`). O(n), no backtracking. */
function trimTrailingPunctuation(run: string): { candidate: string; trailing: string } {
  let end = run.length
  while (end > 0 && TRAILING_PUNCT.has(run[end - 1])) end--
  return { candidate: run.slice(0, end), trailing: run.slice(end) }
}

/**
 * Splits a plain-text run into {@code text} / {@code url} segments. Each URL
 * candidate is validated through {@link safeHttpUrl} — anything that isn't a
 * real http(s) URL stays plain text (closes the `javascript:`/`data:` hole).
 */
function splitUrls(text: string): ContentSegment[] {
  const segments: ContentSegment[] = []
  let lastIndex = 0
  const re = new RegExp(URL_RUN_REGEX.source, 'gi')
  let match: RegExpExecArray | null
  while ((match = re.exec(text)) !== null) {
    const { candidate, trailing } = trimTrailingPunctuation(match[0])
    // Unsafe / unparseable → leave the whole run as text (handled by the tail
    // slice or the next match's leading-text slice). `candidate` always keeps
    // its `http(s)://` scheme (only trailing prose punctuation is trimmed), so
    // safeHttpUrl is the single gate here.
    if (safeHttpUrl(candidate) === null) continue
    if (match.index > lastIndex) {
      segments.push({ kind: 'text', value: text.slice(lastIndex, match.index) })
    }
    segments.push({ kind: 'url', value: candidate })
    if (trailing) segments.push({ kind: 'text', value: trailing })
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < text.length) {
    segments.push({ kind: 'text', value: text.slice(lastIndex) })
  }
  return segments
}

/**
 * Splits a comment body into renderable {@code text} / {@code mention} /
 * {@code url} segments. URLs are detected FIRST (over the whole string) so an
 * {@code @handle} living inside a URL — e.g. {@code https://twitter.com/@dan}
 * — is never carved out as a mention; {@link splitMentions} then runs only on
 * the non-URL text runs.
 */
export function splitContent(content: string): ContentSegment[] {
  if (!content) return []
  const segments: ContentSegment[] = []
  for (const run of splitUrls(content)) {
    if (run.kind === 'url') {
      segments.push(run)
      continue
    }
    for (const m of splitMentions(run.value)) segments.push(m)
  }
  return segments
}

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
