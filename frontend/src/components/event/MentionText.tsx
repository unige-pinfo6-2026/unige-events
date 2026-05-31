import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { splitContent } from '@/utils/mentions'
import { safeHttpUrl } from '@/utils/url'

interface Props {
  content: string
}

/**
 * Renders a comment body with two kinds of inline links — SCRUM-147 :
 * <ul>
 *   <li>{@code @<handle>} tokens → {@code <Link to=/profile/<handle>>}
 *       (Instagram-style, {@code text-accent}).</li>
 *   <li>http(s) URLs → external {@code <a text-link>} (blue, matches the
 *       {@code event.websiteUrl} rendering on the detail page).</li>
 * </ul>
 *
 * <p>All other text is passed through verbatim inside a {@code <Fragment>}
 * chain so the parent paragraph keeps its {@code whitespace-pre-wrap} +
 * {@code break-words} behaviour.
 *
 * <p>The displayed mention token preserves the caller's original casing
 * ({@code @Daniel}) while the link target is lowercased — the backend stores
 * handles lowercase, and {@code ProfilePage} canonicalises mixed-case URLs
 * anyway. URL safety is enforced by {@link safeHttpUrl} (already applied in
 * {@link splitContent}); the defensive re-check here keeps a malformed value
 * from ever reaching an {@code href}.
 */
export default function MentionText({ content }: Readonly<Props>) {
  const segments = splitContent(content)
  if (segments.length === 0) return null
  return (
    <>
      {segments.map((segment, idx) => {
        if (segment.kind === 'mention') {
          const target = segment.value.toLowerCase()
          return (
            <Link
              key={idx}
              to={`/profile/${target}`}
              className="text-accent hover:underline"
            >
              @{segment.value}
            </Link>
          )
        }
        if (segment.kind === 'url') {
          const href = safeHttpUrl(segment.value)
          if (href) {
            return (
              <a
                key={idx}
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className="text-link hover:underline break-all"
              >
                {segment.value}
              </a>
            )
          }
        }
        return <Fragment key={idx}>{segment.value}</Fragment>
      })}
    </>
  )
}
