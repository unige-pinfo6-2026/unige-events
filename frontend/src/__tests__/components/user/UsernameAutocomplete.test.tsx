import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import UsernameAutocomplete from '@/components/user/UsernameAutocomplete'

vi.mock('@/services/userService', () => ({
  searchUsernames: vi.fn(),
}))

import { searchUsernames } from '@/services/userService'

const mockSearchUsernames = searchUsernames as ReturnType<typeof vi.fn>

const matches = [
  { id: 'u1', username: 'nexiumito',  displayName: 'Nexium Ito', avatarUrl: null, profilePublic: true },
  { id: 'u2', username: 'nexus.dev',  displayName: 'Nexus Dev',  avatarUrl: null, profilePublic: true },
  { id: 'u3', username: 'next.alice', displayName: 'Alice Next', avatarUrl: null, profilePublic: true },
]

interface HostProps {
  initialValue?: string
  onSelect?: (user: { id: string; username: string }) => void
  excludeUsernames?: string[]
  /** When true, omit the `inputId` prop so the component falls back to its
   *  generated `useId` value (exercises the `inputId ?? generatedId` else). */
  omitInputId?: boolean
}

// Tiny host that drives the controlled state — mirrors how the real editors
// wire UsernameAutocomplete (parent owns the typed value).
function Host({
  initialValue = '',
  onSelect = () => {},
  excludeUsernames = [],
  omitInputId = false,
}: HostProps) {
  const [value, setValue] = useState(initialValue)
  return (
    <UsernameAutocomplete
      value={value}
      onChange={setValue}
      onSelect={onSelect}
      excludeUsernames={excludeUsernames}
      inputId={omitInputId ? undefined : 'autocomplete-test'}
    />
  )
}

beforeEach(() => {
  vi.useFakeTimers()
  mockSearchUsernames.mockReset()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

async function flushAll() {
  // 1) flush the debounce (300 ms) — must come BEFORE the awaitable so the
  // service mock resolves inside an `act` boundary.
  await act(async () => {
    vi.advanceTimersByTime(310)
  })
  // 2) drain pending microtasks so the .then() of the promise lands.
  await act(async () => {
    await Promise.resolve()
  })
}

describe('UsernameAutocomplete (SCRUM-137)', () => {
  it('renders the controlled input with the provided value', () => {
    render(<Host initialValue="" />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    expect(input.value).toBe('')
    expect(input.getAttribute('aria-expanded')).toBe('false')
  })

  it('does not fetch until 2 characters are typed', async () => {
    mockSearchUsernames.mockResolvedValue([])
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'n' } })
    await flushAll()
    expect(mockSearchUsernames).not.toHaveBeenCalled()
  })

  it('debounces the input by 300ms before firing the fetch', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')

    fireEvent.change(input, { target: { value: 'ne' } })
    // Half the debounce — nothing should fire yet.
    await act(async () => { vi.advanceTimersByTime(150) })
    expect(mockSearchUsernames).not.toHaveBeenCalled()

    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // Only one call : the second keystroke reset the debounce.
    expect(mockSearchUsernames).toHaveBeenCalledTimes(1)
    expect(mockSearchUsernames).toHaveBeenCalledWith('nex', 8)
  })

  it('renders matching suggestions in the dropdown with handle + displayName', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    expect(screen.getByRole('listbox')).toBeTruthy()
    expect(screen.getByText('@nexiumito')).toBeTruthy()
    expect(screen.getByText('Nexium Ito')).toBeTruthy()
    expect(screen.getByText('@nexus.dev')).toBeTruthy()
  })

  it('lets the user pick a suggestion by clicking; calls onSelect + propagates value', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    const onSelect = vi.fn()
    render(<Host onSelect={onSelect} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    fireEvent.mouseDown(screen.getByText('@nexiumito'))

    expect(onSelect).toHaveBeenCalledWith(matches[0])
    expect(input.value).toBe('nexiumito')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('supports keyboard ArrowDown + Enter to select the first option', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    const onSelect = vi.fn()
    render(<Host onSelect={onSelect} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onSelect).toHaveBeenCalledWith(matches[0])
    expect(input.value).toBe('nexiumito')
  })

  it('closes the dropdown on Escape', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    expect(screen.getByRole('listbox')).toBeTruthy()
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('closes the dropdown on outside mousedown', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(
      <div>
        <Host />
        <button type="button">outside</button>
      </div>,
    )
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'nex' } })
    await flushAll()
    expect(screen.getByRole('listbox')).toBeTruthy()

    fireEvent.mouseDown(screen.getByRole('button', { name: 'outside' }))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('filters excluded usernames out of the dropdown', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host excludeUsernames={['nexiumito', 'NEXUS.DEV']} />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'nex' } })
    await flushAll()

    // The two excluded handles are filtered (case-insensitive). Only next.alice remains.
    expect(screen.queryByText('@nexiumito')).toBeNull()
    expect(screen.queryByText('@nexus.dev')).toBeNull()
    expect(screen.getByText('@next.alice')).toBeTruthy()
  })

  it('renders an empty-state message when the backend returns []', async () => {
    mockSearchUsernames.mockResolvedValue([])
    render(<Host />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'zz' } })
    await flushAll()

    expect(screen.getByText('Aucun résultat.')).toBeTruthy()
  })

  it('caches results: typing the same prefix twice fires the service once', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')

    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()
    expect(mockSearchUsernames).toHaveBeenCalledTimes(1)

    // Type something different, then back to "nex".
    fireEvent.change(input, { target: { value: 'al' } })
    await flushAll()
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // The "nex" prefix is served from cache — only the "al" round-trip is new.
    expect(mockSearchUsernames).toHaveBeenCalledTimes(2)
  })

  it('shows error message when searchUsernames rejects (lines 136-138)', async () => {
    mockSearchUsernames.mockRejectedValue(new Error('Network failure'))
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()
    expect(screen.getByText('Erreur lors de la recherche.')).toBeTruthy()
  })

  it('navigates up with ArrowUp including clamp at index 0 (lines 180-181)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // Navigate to index 1 via ArrowDown x2
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    // ArrowUp back to 0
    fireEvent.keyDown(input, { key: 'ArrowUp' })
    // ArrowUp at 0 → clamped to 0 (prev <= 0 branch)
    fireEvent.keyDown(input, { key: 'ArrowUp' })

    const items = screen.getAllByRole('option')
    expect(items[0].getAttribute('aria-selected')).toBe('true')
  })

  it('re-opens dropdown on focus when query is already long enough (lines 200-201)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')

    // Type a query and wait for results
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()
    expect(screen.getAllByRole('option').length).toBeGreaterThan(0)

    // Close via Escape
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(screen.queryAllByRole('option')).toHaveLength(0)

    // Re-focus — handleFocus fires → setIsOpen(true) because debounced >= MIN_QUERY_LENGTH
    fireEvent.focus(input)
    expect(screen.getAllByRole('option').length).toBeGreaterThan(0)
  })

  it('highlights a result on mouseEnter (line 267)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    const items = screen.getAllByRole('option')
    // Hovering the second item sets activeIndex to 1 → aria-selected true
    fireEvent.mouseEnter(items[1])
    expect(items[1].getAttribute('aria-selected')).toBe('true')
    expect(items[0].getAttribute('aria-selected')).toBe('false')
  })

  it('skips the immediate re-fetch right after a selection (input flips to the picked handle)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')

    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()
    fireEvent.mouseDown(screen.getByText('@nexiumito'))

    // The value just flipped to "nexiumito" — let the debounce run.
    await flushAll()

    // Only the initial "nex" fetch fired ; the selection didn't trigger
    // a redundant `searchUsernames("nexiumito")`.
    expect(mockSearchUsernames).toHaveBeenCalledTimes(1)
  })

  it('falls back to the generated useId when no inputId prop is passed (line 68)', () => {
    // `id = inputId ?? generatedId` — without inputId the listbox id derives
    // from the useId() value. We assert the input carries an aria-controls
    // attribute that is NOT our fixed test id.
    render(<Host omitInputId />)
    const input = screen.getByRole('combobox')
    const controls = input.getAttribute('aria-controls')
    expect(controls).toBeTruthy()
    expect(controls).not.toBe('autocomplete-test-listbox')
    expect(controls?.endsWith('-listbox')).toBe(true)
  })

  it('evicts the oldest cache entry once the prefix count exceeds the limit (lines 129-131)', async () => {
    // CACHE_LIMIT is 50. Resolve a distinct payload per prefix so each
    // 2-char query is a fresh, non-cached round-trip. After 51 distinct
    // prefixes the Map has overflowed and the oldest key is deleted.
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')

    // 51 distinct two-letter prefixes ("aa"..) — more than CACHE_LIMIT (50).
    const prefixes: string[] = []
    for (let i = 0; i < 51; i++) {
      const a = String.fromCharCode(97 + Math.floor(i / 10)) // a,b,c...
      const b = String.fromCharCode(97 + (i % 10))           // a..j
      prefixes.push(`${a}${b}`)
    }
    for (const p of prefixes) {
      fireEvent.change(input, { target: { value: p } })
      await flushAll()
    }
    // Every distinct prefix triggered exactly one fetch (none were cache hits).
    expect(mockSearchUsernames).toHaveBeenCalledTimes(51)

    // The very first prefix ("aa") was evicted by the overflow, so re-typing
    // it now misses the cache and fires a fresh fetch (52nd call).
    fireEvent.change(input, { target: { value: prefixes[0] } })
    await flushAll()
    expect(mockSearchUsernames).toHaveBeenCalledTimes(52)
  })

  it('clamps activeIndex at the last option when ArrowDown is pressed past the end (line 177)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // 3 results → 4 ArrowDown presses; the 4th would push past the end and is
    // clamped to filteredResults.length - 1 (= 2).
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'ArrowDown' })

    const items = screen.getAllByRole('option')
    expect(items[items.length - 1].getAttribute('aria-selected')).toBe('true')
  })

  it('Enter with no active option is a no-op (line 183 false branch)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    const onSelect = vi.fn()
    render(<Host onSelect={onSelect} />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // Dropdown is open but activeIndex is still -1 → the Enter guard
    // (activeIndex >= 0) is false, so nothing is selected.
    expect(screen.getByRole('listbox')).toBeTruthy()
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onSelect).not.toHaveBeenCalled()
    // Dropdown stays open — Enter did not commit a selection.
    expect(screen.getByRole('listbox')).toBeTruthy()
  })

  it('ignores keys that are not Arrow/Enter/Escape (line 187 else chain)', async () => {
    mockSearchUsernames.mockResolvedValue(matches)
    const onSelect = vi.fn()
    render(<Host onSelect={onSelect} />)
    const input = screen.getByRole('combobox')
    fireEvent.change(input, { target: { value: 'nex' } })
    await flushAll()

    // A plain character key falls through every branch of handleKeyDown
    // without preventing default or mutating selection state.
    fireEvent.keyDown(input, { key: 'a' })
    expect(onSelect).not.toHaveBeenCalled()
    expect(screen.getByRole('listbox')).toBeTruthy()
    expect(screen.queryByRole('option', { selected: true })).toBeNull()
  })

  it('does not open the dropdown on focus while the query is too short (line 200 false branch)', () => {
    mockSearchUsernames.mockResolvedValue(matches)
    render(<Host initialValue="n" />)
    const input = screen.getByRole('combobox')

    // debounced ("n") is below MIN_QUERY_LENGTH, so handleFocus skips opening.
    fireEvent.focus(input)
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(input.getAttribute('aria-expanded')).toBe('false')
  })
})
