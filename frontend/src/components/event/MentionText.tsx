import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { splitContent, type ContentSegment } from '@/utils/mentions'

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
 * anyway. URL safety is enforced by {@code safeHttpUrl} inside
 * {@link splitContent} — a {@code url} segment is therefore always a real
 * http(s) URL, rendered straight into the {@code href}.
 */
/**
 * Cumulative character offset of each segment within the body — a stable,
 * reorder-safe key source (the index would trip `react/no-array-index-key`,
 * and a running mutation in render trips `react-hooks/immutability`).
 */
function segmentOffsets(segments: ContentSegment[]): number[] {
  const offsets: number[] = []
  let sum = 0
  for (const s of segments) {
    offsets.push(sum)
    sum += s.value.length
  }
  return offsets
}

export default function MentionText({ content }: Readonly<Props>) {
  const segments = splitContent(content)
  if (segments.length === 0) return null
  const offsets = segmentOffsets(segments)
  return (
    <>
      {segments.map((segment, i) => renderSegment(segment, `${segment.kind}-${offsets[i]}`))}
    </>
  )
}

function renderSegment(segment: ContentSegment, key: string) {
  if (segment.kind === 'mention') {
    return (
      <Link key={key} to={`/profile/${segment.value.toLowerCase()}`} className="text-accent hover:underline">
        @{segment.value}
      </Link>
    )
  }
  if (segment.kind === 'url') {
    return (
      <a
        key={key}
        href={segment.value}
        target="_blank"
        rel="noopener noreferrer"
        className="text-link hover:underline break-all"
      >
        {segment.value}
      </a>
    )
  }
  return <Fragment key={key}>{segment.value}</Fragment>
}
