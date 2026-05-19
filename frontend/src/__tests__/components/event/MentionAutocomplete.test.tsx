import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createRef, useState } from 'react'

vi.mock('@/services/userService', () => ({
  searchUsernames: vi.fn(),
}))

import { searchUsernames } from '@/services/userService'
import MentionAutocomplete, { detectActiveMention } from '@/components/event/MentionAutocomplete'
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
  mockSearch.mockReset()
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
})
