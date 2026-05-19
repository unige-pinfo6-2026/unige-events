import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createRef, useState } from 'react'

// importActual + spread — partial mocks of `@/services/userService` leak
// across vitest forks in CI (FollowListPage / EventCreatePage tests need
// getUserByUsername / etc. to still exist on the module). Preserve every
// other export ; only override searchUsernames.
vi.mock('@/services/userService', async () => {
  const actual = await vi.importActual<typeof import('@/services/userService')>('@/services/userService')
  return {
    ...actual,
    searchUsernames: vi.fn(),
  }
})

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
})

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
    mockSearch.mockResolvedValue([
      user('alice.dosh', 'Alice'),
      user('alex.smith', 'Alex'),
      user('alan.jones', 'Alan'),
    ])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    // Default active index is 0 (first row) — ArrowDown twice moves to row 2.
    fireEvent.keyDown(ta, { key: 'ArrowDown' })
    fireEvent.keyDown(ta, { key: 'ArrowDown' })
    fireEvent.keyDown(ta, { key: 'Enter' })
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alan.jones '))
  })

  it('ArrowDown stops at the last row (no overflow)', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice'), user('alex.smith', 'Alex')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    // 2 results — pressing ArrowDown thrice should clamp at index 1.
    fireEvent.keyDown(ta, { key: 'ArrowDown' })
    fireEvent.keyDown(ta, { key: 'ArrowDown' })
    fireEvent.keyDown(ta, { key: 'ArrowDown' })
    fireEvent.keyDown(ta, { key: 'Enter' })
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alex.smith '))
  })

  it('ArrowUp clamps at row 0', async () => {
    mockSearch.mockResolvedValue([user('alice.dosh', 'Alice'), user('alex.smith', 'Alex')])
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText('@alice.dosh')).toBeTruthy(), { timeout: 1000 })
    fireEvent.keyDown(ta, { key: 'ArrowUp' })
    fireEvent.keyDown(ta, { key: 'ArrowUp' })
    fireEvent.keyDown(ta, { key: 'Enter' })
    // The active row remains the first option (alice.dosh).
    await waitFor(() => expect((screen.getByTestId('ta') as HTMLTextAreaElement).value).toBe('@alice.dosh '))
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
    let resolveSearch: ((data: UserPublicResponse[]) => void) | null = null
    mockSearch.mockImplementation(() => new Promise((res) => { resolveSearch = res }))
    render(<Harness />)
    const ta = screen.getByTestId('ta') as HTMLTextAreaElement
    typeIn(ta, '@al')
    await waitFor(() => expect(screen.getByText(/Recherche/i)).toBeTruthy(), { timeout: 1000 })
    resolveSearch?.([user('alice.dosh', 'Alice')])
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
})
