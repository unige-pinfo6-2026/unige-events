import { useEffect, useId, useMemo, useRef, useState, type RefObject } from 'react'
import { useDebounce } from '@/hooks/useDebounce'
import { searchUsernames } from '@/services/userService'
import UserAvatar from '@/components/user/UserAvatar'
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

/** Charset that matches the SCRUM-169 username regex `[a-z0-9._-]{3,30}`,
 *  case-insensitive (the user can type `@Alice` ; we resolve against
 *  lowercased usernames in the backend). */
const HANDLE_CHARSET = /[a-zA-Z0-9._-]/

interface ActiveMention {
  /** Inclusive index of the `@` in `value`. */
  atIndex: number
  /** The prefix the user has typed since the `@`, lowercased. */
  prefix: string
}

/**
 * Detects the {@code @<prefix>} token currently around the caret. Returns
 * {@code null} if there is no active mention (no {@code @} before the caret,
 * or the prefix has been broken by a non-charset char like space / newline).
 *
 * <p>Walks <strong>backwards</strong> from the caret one char at a time :
 * <ul>
 *   <li>If the char is in the handle charset, keep going.</li>
 *   <li>If the char is {@code @} and the previous char (just before the
 *       {@code @}) is NOT a word character — i.e. the {@code @} starts a
 *       new mention — return the result.</li>
 *   <li>Anything else (whitespace, punctuation outside charset) → no
 *       active mention.</li>
 * </ul>
 *
 * <p>Boundary rule on the char before {@code @} matters : in
 * {@code "email@example.com"} the {@code @} is preceded by {@code l}
 * (word char), so it does NOT start a mention.
 */
export function detectActiveMention(value: string, caretPos: number): ActiveMention | null {
  if (caretPos < 1) return null
  let i = caretPos - 1
  while (i >= 0 && HANDLE_CHARSET.test(value[i])) {
    i--
  }
  // Either we hit a non-charset char or reached the start of the string.
  if (i < 0 || value[i] !== '@') return null
  // i points at the @. Check the char just before it.
  if (i > 0) {
    const before = value[i - 1]
    // Stop on word chars so `email@x` doesn't trigger.
    if (/\w/.test(before)) return null
  }
  return { atIndex: i, prefix: value.slice(i + 1, caretPos).toLowerCase() }
}

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
      if (!activeMention || results.length === 0) return
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
      } else if (event.key === 'Escape') {
        event.preventDefault()
        setIsOpen(false)
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
    onChange(newValue, newCaretPos)
    // Restore focus + caret on the textarea after the controlled update flushes.
    requestAnimationFrame(() => {
      const t = textareaRef.current
      if (t) {
        t.focus()
        t.setSelectionRange(newCaretPos, newCaretPos)
        setCaretPos(newCaretPos)
      }
    })
  }

  if (!activeMention) return null

  return (
    <div ref={containerRef} className="absolute z-20 top-full left-0 right-0 mt-1">
      <ul
        id={listboxId}
        role="listbox"
        aria-label="Suggestions d'utilisateurs"
        className="max-h-72 overflow-y-auto bg-background border border-border rounded-2xl shadow-xl"
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
}
