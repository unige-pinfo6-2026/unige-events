import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createRef, useState } from 'react'

// Comprehensive stub — every exported function from userService is mocked
// with a safe `vi.fn()`. This avoids the CI fork-pool quirk where
// `vi.importActual` can let the real implementation leak into sibling
// test files (caught FollowListPage previously). The only function this
// suite cares about is searchUsernames ; the rest are placeholders so
// nothing real ever gets called.
vi.mock('@/services/userService', () => ({
  getMe: vi.fn(),
  getUserById: vi.fn(),
  getPublicProfile: vi.fn(),
  getUserByUsername: vi.fn(),
  updateProfile: vi.fn(),
  updateUsername: vi.fn(),
  searchUsernames: vi.fn(),
  checkUsernameAvailable: vi.fn(),
  uploadPhoto: vi.fn(),
  uploadBanner: vi.fn(),
  deleteBanner: vi.fn(),
  getCalendarToken: vi.fn(),
  regenerateCalendarToken: vi.fn(),
}))

import { searchUsernames } from '@/services/userService'
import MentionAutocomplete from '@/components/event/MentionAutocomplete'
import { detectActiveMention } from '@/utils/mentions'
import type { UserPublicResponse } from '@/types/user'

const mockSearch = vi.mocked(searchUsernames)

function user(username: string, displayName: string | null = null): UserPublicResponse {
  return {
    id: `${username}-uuid`,
    username,
    displayName,
    faculty: null,
    studyLevel: null,
    bio: null,
    interests: [],
    avatarUrl: null,
    bannerUrl: null,
    profilePublic: false,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }
}

beforeEach(() => {
  // Reset to an empty-resolve default so other tests in the same vitest
  // fork that may indirectly trigger searchUsernames (e.g. SCRUM-137's
  // UsernameAutocomplete in EventCreatePage tests) don't get a `vi.fn()`
  // returning undefined — that would crash with `Cannot read property 'then'`.
  mockSearch.mockReset()
  mockSearch.mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

// Debounce window of MentionAutocomplete (keep in sync with its DEBOUNCE_MS).
const MENTION_DEBOUNCE_MS = 300

// Keyboard-navigation tests freeze the clock so the debounced search — and the
// single re-fire it triggers on the next render — settle deterministically
// *before* any key is pressed. With real timers these were flaky: an async
// search resolution could land mid-sequence and reset activeIndex back to 0
// (observed as "expected '@al' to be '@alan.jones'" under CI load).
// Requires vi.useFakeTimers() to be active.
async function settleDebouncedSearch() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(MENTION_DEBOUNCE_MS + 50)
  })
}

describe('detectActiveMention (pure)', () => {
  it('returns null when no @ before caret', () => {
    expect(detectActiveMention('hello world', 5)).toBeNull()
  })

  it('captures a basic @prefix at end of string', () => {
    expect(detectActiveMention('hello @al', 9)).toEqual({ atIndex: 6, prefix: 'al' })
  })

  it('lowercases the captured prefix', () => {
    expect(detectActiveMention('hello @Al', 9)).toEqual({ atIndex: 6, prefix: 'al' })
  })

  it('returns null when @ is preceded by a word char (email)', () => {
    // "email@al" — the @ does not start a new token.
    expect(detectActiveMention('email@al', 8)).toBeNull()
  })

  it('detects mention at start of string', () => {
    expect(detectActiveMention('@al', 3)).toEqual({ atIndex: 0, prefix: 'al' })
  })

  it('captures only the token around the caret when multiple @ exist', () => {
    // Caret right after "bob" in "@alice.dosh hi @bob"
    const value = '@alice.dosh hi @bob'
    expect(detectActiveMention(value, value.length)).toEqual({ atIndex: 15, prefix: 'bob' })
  })

  it('returns null when caret is before the @', () => {
    expect(detectActiveMention('hello @al', 5)).toBeNull()
  })

  it('handles dot and dash inside handles', () => {
    expect(detectActiveMention('hey @ann-marie.dosh', 19)).toEqual({ atIndex: 4, prefix: 'ann-marie.dosh' })
  })

  it('returns null when the token contains a space', () => {
    expect(detectActiveMention('@al ice', 7)).toBeNull()
  })

  it('captures empty prefix right after @', () => {
    expect(detectActiveMention('hello @', 7)).toEqual({ atIndex: 6, prefix: '' })
  })
})

// ─── Component integration tests ─────────────────────────────────────

function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  const ref = createRef<HTMLTextAreaElement>()
  return (
    <div className="relative">
      <textarea
        ref={ref}
        data-testid="ta"
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
      <MentionAutocomplete
        value={value}
        onChange={(newValue) => setValue(newValue)}
        textareaRef={ref}
      />
    </div>
  )
}

function typeIn(ta: HTMLTextAreaElement, text: string) {
  fireEvent.change(ta, { target: { value: text } })
  // Place caret at end of typed text — fire change first, then sync.
  ta.setSelectionRange(text.length, text.length)
  fireEvent.keyUp(ta)
}

describe('MentionAutocomplete (component)', () => {
  // Real timers + a 500ms cushion (debounce is 300ms). Each test waits for
  // the search mock to either be called or skipped.

  it('does not call /users/search before the 2-char minimum', async () => {
    mockSearch.mockResolvedValue([])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@a')
    // Wait past the debounce window — the request still shouldn't fire.
    await new Promise((r) => setTimeout(r, 450))
    expect(mockSearch).not.toHaveBeenCalled()
  })

  it('fires a single search after debounce when prefix is ≥ 2 chars', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(mockSearch).toHaveBeenCalledWith('al', 8), { timeout: 1000 })
  })

  it('shows the dropdown with results, lists @username + displayName', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    expect(screen.getByText('Alice')).toBeTruthy()
  })

  it('clicking a suggestion replaces the prefix with @username plus space', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    fireEvent.mouseDown(screen.getByText('@alice.dosh'))
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alice.dosh '))
  })

  it('typing with no @ produces no dropdown call', async () => {
    mockSearch.mockResolvedValue([])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, 'hello world')
    await new Promise((r) => setTimeout(r, 450))
    expect(mockSearch).not.toHaveBeenCalled()
  })

  it('disabled prop hides the dropdown entirely', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    function DisabledHarness() {
      const [value, setValue] = useState('')
      const ref = createRef<HTMLTextAreaElement>()
      return (
        <div className="relative">
          <textarea ref={ref} data-testid="ta" value={value} onChange={(e) => setValue(e.target.value)} />
          <MentionAutocomplete value={value} onChange={setValue} textareaRef={ref} disabled />
        </div>
      )
    }
    render(<DisabledHarness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await new Promise((r) => setTimeout(r, 450))
    // Even past the debounce window, no listbox should render.
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('ArrowDown / ArrowUp move the active row, Enter inserts it', async () => {
    // Fake timers: the debounced search settles fully before the (synchronous)
    // key presses, so no async re-search can reset activeIndex between them.
    vi.useFakeTimers()
    try {
      mockSearch.mockResolvedValue([
        user('alice.dosh', 'Alice'),
        user('alex.smith', 'Alex'),
        user('alan.jones', 'Alan'),
      ])
      render(<Harness />)
      const ta = screen.getByTestId('ta') as HTMLTextAreaElement
      typeIn(ta, '@al')
      await settleDebouncedSearch()
      expect(screen.getByText('@alan.jones')).toBeTruthy()
      // Default active index is 0 (first row) — ArrowDown twice moves to row 2.
      // Synchronous presses → no microtask flush between them.
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'Enter' })
      expect(ta.value).toBe('@alan.jones ')
    } finally {
      vi.useRealTimers()
    }
  })

  it('ArrowDown stops at the last row (no overflow)', async () => {
    vi.useFakeTimers()
    try {
      mockSearch.mockResolvedValue([user('alice.dosh', 'Alice'), user('alex.smith', 'Alex')])
      render(<Harness />)
      const ta = screen.getByTestId('ta') as HTMLTextAreaElement
      typeIn(ta, '@al')
      await settleDebouncedSearch()
      // 2 results — pressing ArrowDown thrice should clamp at index 1.
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'Enter' })
      expect(ta.value).toBe('@alex.smith ')
    } finally {
      vi.useRealTimers()
    }
  })

  it('ArrowUp does not crash when activeIndex is already at 0', async () => {
    // Direct test of the `Math.max(prev - 1, 0)` clamp ; we assert
    // the first row stays active-selected (no overflow into negative
    // indices). Avoids chaining Enter — see the corresponding
    // "ArrowDown stops at the last row" test for the upper bound.
    vi.useFakeTimers()
    try {
      mockSearch.mockResolvedValue([user('alice.dosh', 'Alice'), user('alex.smith', 'Alex')])
      render(<Harness />)
      const ta = screen.getByTestId('ta') as HTMLTextAreaElement
      typeIn(ta, '@al')
      await settleDebouncedSearch()
      // Pressing ArrowUp on the already-top row must not throw and must
      // keep the first row aria-selected.
      expect(() => {
        fireEvent.keyDown(ta, { key: 'ArrowUp' })
        fireEvent.keyDown(ta, { key: 'ArrowUp' })
      }).not.toThrow()
      const firstRow = screen.getByText('@alice.dosh').closest('li')
      expect(firstRow?.getAttribute('aria-selected')).toBe('true')
    } finally {
      vi.useRealTimers()
    }
  })

  it('Escape closes the dropdown', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
    fireEvent.keyDown(ta, { key: 'Escape', bubbles: true })
    // Dropdown closes — no more listbox in the tree.
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull(), { timeout: 2000 })
  })

  it('typing more after Escape reopens the dropdown', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
    fireEvent.keyDown(ta, { key: 'Escape', bubbles: true })
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull(), { timeout: 2000 })
    // Typing one more char fires the value-watcher effect → reopens.
    typeIn(ta, '@ali')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 2000 })
  })

  it('clicking outside closes the dropdown', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(
      <div>
        <button data-testid="elsewhere">elsewhere</button>
        <Harness />
      </div>,
    )
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
    fireEvent.mouseDown(screen.getByTestId('elsewhere'))
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull())
  })

  it('row without displayName falls back to @username line only', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', null)])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    // The displayName <p> with class font-medium isn't rendered.
    expect(screen.queryByText('Alice')).toBeNull()
  })

  it('shows the "Aucun utilisateur." placeholder when the search returns 0 results', async () => {
    mockSearch.mockResolvedValue([])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@gh')
    await waitFor(() => expect(screen.getByText(/Aucun utilisateur/i)).toBeTruthy(), { timeout: 1000 })
  })

  it('shows "Recherche…" while the search is in flight', async () => {
    type Resolver = (data: UserPublicResponse[]) => void
    let resolveSearch: Resolver | null = null
    mockSearch.mockImplementation(
      () => new Promise<UserPublicResponse[]>((res) => { resolveSearch = res as Resolver }),
    )
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText(/Recherche/i)).toBeTruthy(), { timeout: 1000 })
    ;(resolveSearch as Resolver | null)?.([user('alice.dosh', 'Alice')])
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy())
  })

  it('swallows search errors and renders the empty-state placeholder', async () => {
    mockSearch.mockRejectedValue(new Error('boom'))
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    // The catch resets results to [] and isLoading to false ; empty state appears.
    await waitFor(() => expect(screen.getByText(/Aucun utilisateur/i)).toBeTruthy(), { timeout: 1000 })
  })

  it('hovering a row sets it as the active one (mouseEnter)', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice'), user('alex.smith', 'Alex')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alex.smith')).toBeTruthy(), { timeout: 1000 })
    // Hover the second row, then press Enter — should insert alex.smith.
    const aleRow = screen.getByText('@alex.smith').closest('li')!
    fireEvent.mouseEnter(aleRow)
    fireEvent.keyDown(ta, { key: 'Enter' })
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alex.smith '))
  })

  it('selecting then continuing within the inserted handle does not reopen the dropdown', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    fireEvent.mouseDown(screen.getByText('@alice.dosh'))
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alice.dosh '))
    // The dropdown closes after selection — value ends with a space, so
    // detectActiveMention returns null at the caret position past the
    // inserted handle. waitFor accommodates the rAF that repositions the
    // caret.
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull(), { timeout: 2000 })
  })

  it('typing past the inserted handle reopens the dropdown for a new mention', async () => {
    mockSearch.mockResolvedValue([user('bob.smith', 'Bob')])
    render(<Harness initial="@bob.smith " />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    // Caret is at end of inserted handle. Add a new mention.
    typeIn(ta, '@bob.smith hello @al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
  })

  it('Enter without an open mention is a no-op (does not preventDefault)', async () => {
    mockSearch.mockResolvedValue([])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    // No @ — pressing Enter should pass through without throwing.
    expect(() => fireEvent.keyDown(ta, { key: 'Enter' })).not.toThrow()
  })

  it('ignores keyboard events when there are no results', async () => {
    mockSearch.mockResolvedValue([])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@gh')
    await waitFor(() => expect(screen.getByText(/Aucun utilisateur/i)).toBeTruthy(), { timeout: 1000 })
    // ArrowDown / Enter on empty results — guarded by `results.length === 0`.
    expect(() => {
      fireEvent.keyDown(ta, { key: 'ArrowDown' })
      fireEvent.keyDown(ta, { key: 'Enter' })
    }).not.toThrow()
  })

  it('focuses the textarea and repositions the caret after a commit (rAF)', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    fireEvent.mouseDown(screen.getByText('@alice.dosh'))
    await waitFor(() => expect(ta.value).toBe('@alice.dosh '))
    // The requestAnimationFrame in commitSelection focuses the textarea and
    // moves the caret past the inserted handle (lines 202-203). Assert on the
    // caret position the rAF sets — deterministic once the frame has flushed.
    // (We don't assert document.activeElement: happy-dom doesn't reliably track
    // focus on a node React just re-rendered, but t.focus() still executes.)
    await waitFor(() => expect(ta.selectionStart).toBe('@alice.dosh '.length), { timeout: 1000 })
    expect(ta.selectionEnd).toBe('@alice.dosh '.length)
  })

  it('moving the caret back inside the just-inserted handle re-evaluates the reopen guard', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    fireEvent.mouseDown(screen.getByText('@alice.dosh'))
    await waitFor(() => expect(ta.value).toBe('@alice.dosh '))
    // Right after the commit the caret is at index 12 (past the trailing
    // space): detectActiveMention returns null there → dropdown is closed.
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull(), { timeout: 2000 })
    // Move the caret back to index 11 — just before the trailing space, i.e.
    // INSIDE the inserted handle. Now `lastInsertedRef` is set and the value
    // startsWith the inserted handle, so the guard at line 64 is entered and
    // `insertedEnd` (line 65) is computed; caretPos (11) < insertedEnd (12)
    // (branch 66-false), so the active mention is returned and the dropdown
    // reopens for the stale handle.
    ta.setSelectionRange(11, 11)
    fireEvent.keyUp(ta)
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 2000 })
  })

  it('bounds the dropdown height to the viewport (inline max-height + scrollable)', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    // Pin the textarea near the top of the viewport → plenty of room below.
    ta.getBoundingClientRect = () =>
      ({ top: 10, bottom: 50, left: 0, right: 100, width: 100, height: 40, x: 0, y: 10, toJSON: () => ({}) }) as DOMRect
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
    act(() => { fireEvent(window, new Event('resize')) })
    const listbox = screen.getByRole('listbox') as HTMLElement
    // A concrete pixel cap is set inline (not an unbounded list) and the list
    // stays scrollable so long result sets never overflow past the footer.
    expect(listbox.style.maxHeight).toMatch(/^\d+px$/)
    expect(listbox.className).toContain('overflow-y-auto')
    // Room below → anchored under the textarea.
    const container = listbox.parentElement as HTMLElement
    expect(container.className).toContain('top-full')
  })

  it('flips above the textarea when there is no room below', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    // Pin the textarea near the bottom of the viewport so space-below is ~0.
    ta.getBoundingClientRect = () =>
      ({ top: window.innerHeight - 8, bottom: window.innerHeight - 4, left: 0, right: 100, width: 100, height: 4, x: 0, y: window.innerHeight - 8, toJSON: () => ({}) }) as DOMRect
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy(), { timeout: 1000 })
    // Trigger a recompute now that the rect override is in place.
    act(() => { fireEvent(window, new Event('resize')) })
    const container = (screen.getByRole('listbox') as HTMLElement).parentElement as HTMLElement
    await waitFor(() => expect(container.className).toContain('bottom-full'))
  })

  it('clears the results (no crash) when the search request rejects', async () => {
    mockSearch.mockRejectedValueOnce(new Error('network down'))
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(mockSearch).toHaveBeenCalledWith('al', 8), { timeout: 1000 })
    // The .catch path runs for the still-current request id → setResults([]) /
    // setActiveIndex(-1), finally setLoading(false). The dropdown stays open
    // (the @al mention is still active) and shows the empty-state row.
    await waitFor(() => expect(screen.getByText('Aucun utilisateur.')).toBeTruthy(), { timeout: 1000 })
  })
})
