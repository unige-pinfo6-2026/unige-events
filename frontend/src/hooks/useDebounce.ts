import { useEffect, useState } from 'react'

/**
 * Returns a debounced copy of `value` that only updates after `delay` ms
 * have elapsed since the last change.
 *
 * Introduced for SCRUM-169 — `ProfileEditPage` debounces the username
 * live-check (HEAD /users/by-username/{u}) so the user can type without
 * firing one request per keystroke. The hook also serves any future
 * call site that needs the same shape (search-as-you-type, autosave).
 *
 * The timeout is cleaned up on every re-render and on unmount, so a
 * stale call cannot land after the component is gone.
 */
export function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState<T>(value)

  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(id)
  }, [value, delay])

  return debounced
}
