import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Skeleton } from 'boneyard-js/react'
import { useDebounce } from '@/hooks/useDebounce'
import { Input } from '@/components/utils/FormField'
import { searchUsernames } from '@/services/userService'
import UserAvatar from '@/components/user/UserAvatar'
import type { UserPublicResponse } from '@/types/user'

interface UsernameAutocompleteProps {
  /** Controlled input value. The parent owns the typed string so it can
   *  submit the existing flow even if the user never picked a suggestion. */
  value: string
  /** Fires on every keystroke. */
  onChange: (value: string) => void
  /** Fires when the user picks a suggestion (click, Enter, etc.). The
   *  parent typically calls `onChange(user.username)` from inside this
   *  handler — pass it through directly for the standard behavior. */
  onSelect: (user: UserPublicResponse) => void
  placeholder?: string
  error?: string
  /** Usernames to hide from the dropdown (e.g. caller's handle + already
   *  invited co-organizers). Compared case-insensitively. */
  excludeUsernames?: string[]
  inputId?: string
  autoFocus?: boolean
  disabled?: boolean
}

const MIN_QUERY_LENGTH = 2
const DEBOUNCE_MS = 300
const FETCH_LIMIT = 8
// Bound the in-memory cache so a user typing for a long session doesn't grow
// it unbounded. 50 distinct prefixes covers any realistic session.
const CACHE_LIMIT = 50

/**
 * SCRUM-137 polish — autocomplete dropdown over `GET /users/search`. The
 * organizer types a prefix, sees up to {@link FETCH_LIMIT} matching users,
 * and clicks (or hits Enter) one to fill the input. The parent keeps the
 * submit flow intact, so a user who types a full username without picking
 * any suggestion still gets the same `getUserByUsername` → invite roundtrip.
 *
 * <p>State machine:
 * <ul>
 *   <li>Debounce the input by {@link DEBOUNCE_MS} before firing.</li>
 *   <li>Skip the call if the trimmed prefix is shorter than {@link MIN_QUERY_LENGTH}.</li>
 *   <li>In-memory cache: identical prefix → no re-fetch.</li>
 *   <li>Monotonic request counter discards stale responses (analogous to
 *       {@code useOccurrences}).</li>
 * </ul>
 *
 * <p>Accessibility: ARIA combobox + listbox pattern with keyboard nav
 * (↑/↓ to move, Enter to select, Escape to close), {@code aria-controls},
 * {@code aria-activedescendant}, {@code aria-expanded}.
 */
export default function UsernameAutocomplete({
  value,
  onChange,
  onSelect,
  placeholder = 'alice.martin',
  error,
  excludeUsernames = [],
  inputId,
  autoFocus = false,
  disabled = false,
}: Readonly<UsernameAutocompleteProps>) {
  const generatedId = useId()
  const id = inputId ?? generatedId
  const listboxId = `${id}-listbox`

  const [results, setResults] = useState<UserPublicResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)

  const debounced = useDebounce(value.trim().toLowerCase(), DEBOUNCE_MS)

  const cacheRef = useRef<Map<string, UserPublicResponse[]>>(new Map())
  const requestIdRef = useRef(0)
  const containerRef = useRef<HTMLDivElement | null>(null)
  // Tracks the last username fed back via `onSelect` so we can skip the next
  // round-trip on the controlled re-render (the input value flips to the
  // selected handle, which would otherwise look like fresh input).
  const lastSelectedRef = useRef<string | null>(null)

  const excludeSet = useMemo(
    () => new Set(excludeUsernames.map((u) => u.toLowerCase())),
    [excludeUsernames],
  )
  const filteredResults = useMemo(
    () => results.filter((u) => !excludeSet.has(u.username.toLowerCase())),
    [results, excludeSet],
  )

  useEffect(() => {
    // Skip the fetch right after a selection — the value matches the picked
    // handle, the user hasn't typed anything new yet.
    if (lastSelectedRef.current !== null && lastSelectedRef.current === debounced) {
      return
    }
    lastSelectedRef.current = null

    if (debounced.length < MIN_QUERY_LENGTH) {
      setResults([])
      setLoading(false)
      setSearchError(null)
      return
    }

    const cached = cacheRef.current.get(debounced)
    if (cached !== undefined) {
      setResults(cached)
      setLoading(false)
      setSearchError(null)
      return
    }

    requestIdRef.current += 1
    const myRequestId = requestIdRef.current
    const isCurrent = () => requestIdRef.current === myRequestId

    setLoading(true)
    setSearchError(null)
    searchUsernames(debounced, FETCH_LIMIT)
      .then((data) => {
        if (!isCurrent()) return
        cacheRef.current.set(debounced, data)
        if (cacheRef.current.size > CACHE_LIMIT) {
          const oldest = cacheRef.current.keys().next().value
          if (oldest !== undefined) cacheRef.current.delete(oldest)
        }
        setResults(data)
      })
      .catch(() => {
        if (!isCurrent()) return
        setResults([])
        setSearchError('Erreur lors de la recherche.')
      })
      .finally(() => {
        if (!isCurrent()) return
        setLoading(false)
      })

    return () => {
      requestIdRef.current += 1
    }
  }, [debounced])

  // Close the dropdown when the user clicks outside the component.
  useEffect(() => {
    if (!isOpen) return
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node | null
      if (target && containerRef.current && !containerRef.current.contains(target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  function commitSelection(user: UserPublicResponse) {
    lastSelectedRef.current = user.username.toLowerCase()
    onChange(user.username)
    onSelect(user)
    setIsOpen(false)
    setActiveIndex(-1)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setIsOpen(true)
      setActiveIndex((prev) => {
        const next = prev + 1
        return next >= filteredResults.length ? filteredResults.length - 1 : next
      })
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((prev) => (prev <= 0 ? 0 : prev - 1))
    } else if (event.key === 'Enter') {
      if (isOpen && activeIndex >= 0 && activeIndex < filteredResults.length) {
        event.preventDefault()
        commitSelection(filteredResults[activeIndex])
      }
    } else if (event.key === 'Escape') {
      setIsOpen(false)
      setActiveIndex(-1)
    }
  }

  function handleInputChange(event: React.ChangeEvent<HTMLInputElement>) {
    onChange(event.target.value)
    setIsOpen(true)
    setActiveIndex(-1)
  }

  function handleFocus() {
    if (debounced.length >= MIN_QUERY_LENGTH) {
      setIsOpen(true)
    }
  }

  const shouldShowDropdown = isOpen && debounced.length >= MIN_QUERY_LENGTH
  const showNoResults = !loading && !searchError && shouldShowDropdown && filteredResults.length === 0

  return (
    <div ref={containerRef} className="relative">
      <Input
        id={id}
        type="text"
        value={value}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onFocus={handleFocus}
        placeholder={placeholder}
        error={error}
        autoComplete="off"
        autoFocus={autoFocus}
        disabled={disabled}
        role="combobox"
        aria-autocomplete="list"
        aria-controls={listboxId}
        aria-expanded={shouldShowDropdown}
        aria-activedescendant={
          activeIndex >= 0 && activeIndex < filteredResults.length
            ? `${id}-option-${activeIndex}`
            : undefined
        }
      />

      {shouldShowDropdown && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label="Suggestions d'utilisateurs"
          className="absolute z-20 top-full left-0 right-0 mt-1 max-h-80 overflow-y-auto bg-background border border-border rounded-2xl shadow-xl"
        >
          {loading && (
            <Skeleton name="username-autocomplete" loading={true} animate="pulse">
              <li className="px-3 py-2"><div className="h-10 rounded" /></li>
              <li className="px-3 py-2"><div className="h-10 rounded" /></li>
              <li className="px-3 py-2"><div className="h-10 rounded" /></li>
            </Skeleton>
          )}

          {!loading && searchError && (
            <li className="px-3 py-3 text-sm text-error">{searchError}</li>
          )}

          {showNoResults && (
            <li className="px-3 py-3 text-sm text-foreground/50 italic">Aucun résultat.</li>
          )}

          {!loading && !searchError && filteredResults.map((user, idx) => {
            const isActive = idx === activeIndex
            return (
              <li
                key={user.id}
                id={`${id}-option-${idx}`}
                role="option"
                aria-selected={isActive}
                // mousedown (not click) so the parent input doesn't blur and
                // close the dropdown before we read the selection.
                onMouseDown={(e) => { e.preventDefault(); commitSelection(user) }}
                onMouseEnter={() => setActiveIndex(idx)}
                className={`flex items-center gap-3 px-3 py-2 cursor-pointer transition-colors ${
                  isActive ? 'bg-foreground/10' : 'hover:bg-foreground/5'
                }`}
              >
                <UserAvatar user={user} className="size-8 text-xs" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">@{user.username}</p>
                  {user.displayName && (
                    <p className="text-xs text-foreground/60 truncate">{user.displayName}</p>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
