import { useEffect, useId, useMemo, useRef, useState, type RefObject } from 'react'
import { createPortal } from 'react-dom'
import { useDebounce } from '@/hooks/useDebounce'
import { searchUsernames } from '@/services/userService'
import UserAvatar from '@/components/user/UserAvatar'
import { detectActiveMention } from '@/utils/mentions'
import { computePlacement, MAX_DROPDOWN_PX, type DropdownPlacement } from '@/components/event/mentionPlacement'
import type { UserPublicResponse } from '@/types/user'

interface MentionAutocompleteProps {
  /** Live value of the textarea (controlled by the parent). */
  value: string
  /** Called when the user selects a suggestion. The new value already has
   *  the @<typedPrefix> replaced by `@<username> ` (trailing space). */
  onChange: (newValue: string, newCaretPos: number) => void
  /** Ref to the textarea — used to track the caret position. */
  textareaRef: RefObject<HTMLTextAreaElement | null>
  /** Disable the dropdown entirely (e.g. when the textarea is disabled). */
  disabled?: boolean
}

const MIN_PREFIX_LENGTH = 2
const DEBOUNCE_MS = 300
const FETCH_LIMIT = 8

/**
 * Inline mention autocomplete for a textarea — SCRUM-147 Décision E.
 *
 * <p>Rendered as a sibling of the textarea (the parent wraps both in a
 * relative-positioned container). Listens to {@code value} + caret
 * position changes, triggers a debounced {@code /users/search} call when
 * the user types an {@code @<prefix>} with ≥ 2 chars, displays up to 8
 * results, and on selection replaces the matched substring with
 * {@code @<username> } (trailing space, caret repositioned).
 *
 * <p>Keyboard navigation : ↑/↓ move the active row, Enter inserts the
 * highlighted suggestion, Esc closes the dropdown. Click outside also
 * closes.
 */
export default function MentionAutocomplete({
  value,
  onChange,
  textareaRef,
  disabled = false,
}: Readonly<MentionAutocompleteProps>) {
  const generatedId = useId()
  const listboxId = `${generatedId}-listbox`

  const [results, setResults] = useState<UserPublicResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [caretPos, setCaretPos] = useState(0)
  const [isOpen, setIsOpen] = useState(true)
  const [placement, setPlacement] = useState<DropdownPlacement>({ side: 'below', maxHeight: MAX_DROPDOWN_PX, left: 0, width: 0, top: 0 })

  const containerRef = useRef<HTMLDivElement | null>(null)
  const requestIdRef = useRef(0)
  const lastInsertedRef = useRef<string | null>(null)

  const activeMention = useMemo(() => {
    if (disabled || !isOpen) return null
    const m = detectActiveMention(value, caretPos)
    if (!m || m.prefix.length < MIN_PREFIX_LENGTH) return null
    // Don't reopen the dropdown right after a selection inserted
    // `@<username> ` (the value still contains that handle but the user
    // hasn't typed anything new).
    if (lastInsertedRef.current !== null && value.startsWith(lastInsertedRef.current, m.atIndex)) {
      const insertedEnd = m.atIndex + lastInsertedRef.current.length
      if (caretPos >= insertedEnd) return null
    }
    return m
  }, [disabled, isOpen, value, caretPos])

  const debouncedPrefix = useDebounce(activeMention?.prefix ?? '', DEBOUNCE_MS)

  // Sync caret position from the textarea on every relevant event.
  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    function sync() {
      // Defensive on detached refs.
      const t = textareaRef.current
      if (t) setCaretPos(t.selectionStart ?? 0)
    }
    el.addEventListener('keyup', sync)
    el.addEventListener('click', sync)
    el.addEventListener('focus', sync)
    el.addEventListener('input', sync)
    return () => {
      el.removeEventListener('keyup', sync)
      el.removeEventListener('click', sync)
      el.removeEventListener('focus', sync)
      el.removeEventListener('input', sync)
    }
  }, [textareaRef])

  // Fire the /users/search call when the debounced prefix changes.
  useEffect(() => {
    if (!activeMention || debouncedPrefix.length < MIN_PREFIX_LENGTH) {
      setResults([])
      setLoading(false)
      return
    }
    requestIdRef.current += 1
    const myReqId = requestIdRef.current
    setLoading(true)
    searchUsernames(debouncedPrefix, FETCH_LIMIT)
      .then((data) => {
        if (requestIdRef.current !== myReqId) return
        setResults(data)
        setActiveIndex(data.length > 0 ? 0 : -1)
      })
      .catch(() => {
        if (requestIdRef.current !== myReqId) return
        setResults([])
        setActiveIndex(-1)
      })
      .finally(() => {
        if (requestIdRef.current !== myReqId) return
        setLoading(false)
      })
  }, [debouncedPrefix, activeMention])

  // Click outside → close (until a new @ retriggers).
  useEffect(() => {
    if (!activeMention) return
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node | null
      const ta = textareaRef.current
      if (
        target &&
        containerRef.current &&
        !containerRef.current.contains(target) &&
        (!ta || !ta.contains(target))
      ) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [activeMention, textareaRef])

  // Keyboard navigation on the textarea — intercepted via a ref-bound listener.
  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    function handleKeyDown(event: KeyboardEvent) {
      if (!activeMention) return
      // Escape closes the dropdown regardless of result state — it must
      // work while the search is still in-flight too (the user pressing
      // Escape to dismiss a loading dropdown is a common UX pattern).
      if (event.key === 'Escape') {
        event.preventDefault()
        setIsOpen(false)
        return
      }
      // The other shortcuts move / select a row, so they need a non-empty
      // results list.
      if (results.length === 0) return
      if (event.key === 'ArrowDown') {
        event.preventDefault()
        setActiveIndex((prev) => Math.min(prev + 1, results.length - 1))
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        setActiveIndex((prev) => Math.max(prev - 1, 0))
      } else if (event.key === 'Enter') {
        if (activeIndex >= 0 && activeIndex < results.length) {
          event.preventDefault()
          commitSelection(results[activeIndex])
        }
      }
    }
    el.addEventListener('keydown', handleKeyDown)
    return () => el.removeEventListener('keydown', handleKeyDown)
    // commitSelection is stable enough — depends on activeMention/results/activeIndex
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeMention, results, activeIndex, textareaRef])

  // Reopen the dropdown if the user keeps typing after pressing Escape.
  useEffect(() => {
    if (!isOpen) setIsOpen(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  // Keep the dropdown inside the viewport while it's open: cap its height to
  // the available space and flip above the textarea when there's no room
  // below (otherwise it would spill past the page footer). Recomputed on
  // scroll / resize so it tracks the textarea as the page moves.
  useEffect(() => {
    if (!activeMention) return
    const recompute = () => setPlacement(computePlacement(textareaRef.current))
    recompute()
    window.addEventListener('resize', recompute)
    window.addEventListener('scroll', recompute, true)
    return () => {
      window.removeEventListener('resize', recompute)
      window.removeEventListener('scroll', recompute, true)
    }
  }, [activeMention, textareaRef])

  function commitSelection(user: UserPublicResponse) {
    if (!activeMention) return
    const before = value.slice(0, activeMention.atIndex)
    const after = value.slice(caretPos)
    const inserted = `@${user.username} `
    const newValue = before + inserted + after
    const newCaretPos = before.length + inserted.length
    lastInsertedRef.current = inserted
    setResults([])
    setActiveIndex(-1)
    // Sync our internal caretPos state immediately so the next activeMention
    // recompute sees the post-insert caret position (past the trailing space,
    // which breaks the mention regex → dropdown closes deterministically).
    // The rAF below handles the DOM side (focus + visual caret) which has to
    // wait for the parent re-render to commit.
    setCaretPos(newCaretPos)
    onChange(newValue, newCaretPos)
    requestAnimationFrame(() => {
      const t = textareaRef.current
      if (t) {
        t.focus()
        t.setSelectionRange(newCaretPos, newCaretPos)
      }
    })
  }

  if (!activeMention) return null

  // Portaled to <body> in `position: fixed` so the dropdown escapes (a) the
  // CommentSection card's backdrop-blur stacking context — which would trap
  // its z-index below the footer's z-10 content — and (b) the page wrapper's
  // overflow-hidden clip. z-40 keeps it above the footer (z-10) yet below
  // modals (z-50).
  const dropdown = (
    <div
      ref={containerRef}
      data-side={placement.side}
      style={{
        position: 'fixed',
        left: `${placement.left}px`,
        width: `${placement.width}px`,
        ...(placement.side === 'below'
          ? { top: `${placement.top}px` }
          : { bottom: `${placement.bottom}px` }),
      }}
      className="z-40"
    >
      <ul
        id={listboxId}
        role="listbox"
        aria-label="Suggestions d'utilisateurs"
        style={{ maxHeight: `${placement.maxHeight}px` }}
        className="overflow-y-auto bg-background border border-border rounded-2xl shadow-xl"
      >
        {loading && results.length === 0 && (
          <li className="px-3 py-3 text-xs text-foreground/50 italic">Recherche…</li>
        )}
        {!loading && results.length === 0 && (
          // The dropdown is rendered above ; if there are 0 results we
          // simply leave it empty rather than display noise. The
          // parent component is responsible for keeping the textarea
          // available even when this list is empty.
          <li className="px-3 py-3 text-xs text-foreground/50 italic">Aucun utilisateur.</li>
        )}
        {results.map((user, idx) => {
          const isActive = idx === activeIndex
          return (
            <li
              key={user.id}
              id={`${generatedId}-option-${idx}`}
              role="option"
              aria-selected={isActive}
              onMouseDown={(e) => { e.preventDefault(); commitSelection(user) }}
              onMouseEnter={() => setActiveIndex(idx)}
              className={`flex items-center gap-3 px-3 py-2 cursor-pointer transition-colors ${
                isActive ? 'bg-foreground/10' : 'hover:bg-foreground/5'
              }`}
            >
              <UserAvatar user={user} className="size-8 text-xs" />
              <div className="flex-1 min-w-0">
                {user.displayName && (
                  <p className="text-sm font-medium text-foreground truncate">{user.displayName}</p>
                )}
                <p className="text-xs text-foreground/60 truncate">@{user.username}</p>
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )

  return createPortal(dropdown, document.body)
}
