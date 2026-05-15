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
}

// Tiny host that drives the controlled state — mirrors how the real editors
// wire UsernameAutocomplete (parent owns the typed value).
function Host({ initialValue = '', onSelect = () => {}, excludeUsernames = [] }: HostProps) {
  const [value, setValue] = useState(initialValue)
  return (
    <UsernameAutocomplete
      value={value}
      onChange={setValue}
      onSelect={onSelect}
      excludeUsernames={excludeUsernames}
      inputId="autocomplete-test"
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
})
