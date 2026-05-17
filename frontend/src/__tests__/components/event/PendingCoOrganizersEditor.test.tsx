import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react'

vi.mock('@/services/userService', () => ({
  getUserByUsername: vi.fn(),
  searchUsernames: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

// Stub the autocomplete: input + programmatic onSelect button + hidden
// error indicator. Mirror of the one in CoOrganizersEditor.test.tsx.
vi.mock('@/components/user/UsernameAutocomplete', () => ({
  default: function MockAutocomplete({
    value,
    onChange,
    onSelect,
    inputId,
    error,
    disabled,
    excludeUsernames,
  }: {
    value: string
    onChange: (v: string) => void
    onSelect: (u: { id: string; username: string; displayName: string | null; avatarUrl: string | null; profilePublic: boolean }) => void
    inputId?: string
    error?: string
    disabled?: boolean
    excludeUsernames?: string[]
  }) {
    return (
      <div>
        <input
          id={inputId}
          aria-label="username-input-stub"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
        />
        <button
          type="button"
          data-testid="pick-suggestion"
          onClick={() => onSelect({
            id: '00000000-0000-0000-0000-000000000333',
            username: 'picked.user',
            displayName: 'Picked User',
            avatarUrl: null,
            profilePublic: true,
          })}
        >
          pick
        </button>
        <span data-testid="exclude-len">{excludeUsernames?.length ?? 0}</span>
        {error && <input type="hidden" data-testid="autocomplete-error" value={error} readOnly />}
      </div>
    )
  },
}))

import { getUserByUsername } from '@/services/userService'
import { useAuth } from '@/hooks/useAuth'
import PendingCoOrganizersEditor, {
  type PendingCoOrganizer,
} from '@/components/event/PendingCoOrganizersEditor'
import type { User } from '@/types/user'

const mockGetUserByUsername = vi.mocked(getUserByUsername)
const mockUseAuth = vi.mocked(useAuth)

const CALLER_USER: User = {
  id: '00000000-0000-0000-0000-000000000999',
  auth0Id: 'auth0|caller',
  email: 'caller@example.com',
  username: 'caller.handle',
  profilePublic: true,
  createdAt: '2026-05-15T10:00:00',
}

function resolved(overrides: Partial<User> = {}): User {
  return {
    id: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
    auth0Id: 'auth0|alice',
    email: 'alice@example.com',
    username: 'alice.martin',
    displayName: 'Alice',
    profilePublic: true,
    createdAt: '2026-05-14T10:00:00',
    ...overrides,
  }
}

function entry(overrides: Partial<PendingCoOrganizer> = {}): PendingCoOrganizer {
  return {
    userId: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
    username: 'alice.martin',
    displayName: 'Alice',
    avatarUrl: null,
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => {
  mockGetUserByUsername.mockReset()
  mockUseAuth.mockReturnValue({
    user: CALLER_USER,
    isAuthenticated: true,
    isAdmin: false,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    updateUser: vi.fn(),
  })
})

describe('PendingCoOrganizersEditor', () => {
  it('renders the empty state when no pending entries', () => {
    render(<PendingCoOrganizersEditor pending={[]} onAdd={vi.fn()} onRemove={vi.fn()} />)
    expect(screen.getByText(/aucun co-organisateur/i)).toBeTruthy()
  })

  it('shows the staged entries with @username and the À inviter chip', () => {
    render(
      <PendingCoOrganizersEditor
        pending={[entry()]}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('@alice.martin')).toBeTruthy()
    expect(screen.getByText('À inviter')).toBeTruthy()
  })

  it('rejects an invalid username without hitting the API', async () => {
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'AB' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() => expect(screen.getByText(/username invalide/i)).toBeTruthy())
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('lowercases the username before resolving (SCRUM-169 case-insensitivity)', async () => {
    mockGetUserByUsername.mockResolvedValue(resolved())
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'Alice.Martin' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledWith('alice.martin'))
    await waitFor(() => expect(onAdd).toHaveBeenCalled())
  })

  it('passes the resolved entry to onAdd and clears the input', async () => {
    mockGetUserByUsername.mockResolvedValue(resolved())
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)
    const input = screen.getByLabelText(/username/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'alice.martin' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() =>
      expect(onAdd).toHaveBeenCalledWith({
        userId: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        username: 'alice.martin',
        displayName: 'Alice',
        avatarUrl: null,
      }),
    )
    await waitFor(() => expect(input.value).toBe(''))
  })

  it('rejects an already-staged username without hitting the API', async () => {
    const onAdd = vi.fn()
    render(
      <PendingCoOrganizersEditor pending={[entry()]} onAdd={onAdd} onRemove={vi.fn()} />,
    )
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() => expect(screen.getByText(/déjà dans la liste/i)).toBeTruthy())
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('shows "Utilisateur introuvable" when the username is not found', async () => {
    mockGetUserByUsername.mockResolvedValue(null)
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'unknown' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() => expect(screen.getByText(/utilisateur introuvable/i)).toBeTruthy())
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('surfaces a network error from the resolution call', async () => {
    mockGetUserByUsername.mockRejectedValue(new Error('boom'))
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
    await waitFor(() => expect(screen.getByText(/erreur réseau/i)).toBeTruthy())
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('calls onRemove with the userId when the × button is clicked', () => {
    const onRemove = vi.fn()
    render(
      <PendingCoOrganizersEditor pending={[entry()]} onAdd={vi.fn()} onRemove={onRemove} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /retirer alice/i }))
    expect(onRemove).toHaveBeenCalledWith('aa11bb22-cc33-dd44-ee55-ff6677889900')
  })

  it('uses the username when displayName is missing for label + initials', () => {
    render(
      <PendingCoOrganizersEditor
        pending={[entry({ displayName: null })]}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    // Label falls back to the username (rendered without the @ prefix on the
    // first row); avatar initials come from the same fallback.
    expect(screen.getByText('alice.martin')).toBeTruthy()
    expect(screen.getByText('@alice.martin')).toBeTruthy()
    expect(screen.getByText('AL')).toBeTruthy()
  })

  // ─── SCRUM-137 polish — username autocomplete ────────────────────────

  it('feeds the picked suggestion to the existing add flow', async () => {
    mockGetUserByUsername.mockResolvedValue(resolved({ id: '00000000-0000-0000-0000-000000000333', username: 'picked.user' }))
    const onAdd = vi.fn()
    render(<PendingCoOrganizersEditor pending={[]} onAdd={onAdd} onRemove={vi.fn()} />)

    fireEvent.click(screen.getByTestId('pick-suggestion'))
    const input = screen.getByLabelText('username-input-stub') as HTMLInputElement
    expect(input.value).toBe('picked.user')

    fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))

    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledWith('picked.user'))
    await waitFor(() => expect(onAdd).toHaveBeenCalledWith({
      userId: '00000000-0000-0000-0000-000000000333',
      username: 'picked.user',
      displayName: 'Alice',
      avatarUrl: null,
    }))
  })

  it('passes the caller handle + already-staged entries to excludeUsernames', () => {
    render(
      <PendingCoOrganizersEditor
        pending={[entry()]}
        onAdd={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    // 1 caller + 1 staged → 2 handles passed to the dropdown.
    expect(screen.getByTestId('exclude-len').textContent).toBe('2')
  })
})
