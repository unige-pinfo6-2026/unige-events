import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { splitMentions } from '@/utils/mentions'

interface Props {
  content: string
}

/**
 * Renders a comment body with {@code @<handle>} tokens turned into
 * {@code <Link to=/profile/<handle>>} (Instagram-style) — SCRUM-147.
 *
 * <p>All non-mention text is passed through verbatim inside a
 * {@code <Fragment>} chain so the parent paragraph keeps its
 * {@code whitespace-pre-wrap} + {@code break-words} behaviour.
 *
 * <p>The displayed token preserves the caller's original casing
 * ({@code @Daniel}) while the link target is lowercased — the backend
 * stores handles lowercase, and {@code ProfilePage} canonicalises
 * mixed-case URLs anyway.
 */
export default function MentionText({ content }: Readonly<Props>) {
  const segments = splitMentions(content)
  if (segments.length === 0) return null
  return (
    <>
      {segments.map((segment, idx) => {
        if (segment.kind === 'text') {
          return <Fragment key={idx}>{segment.value}</Fragment>
        }
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
      })}
    </>
  )
}
