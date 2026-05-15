import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react'

vi.mock('@/hooks/useCoOrganizers', () => ({
  useCoOrganizers: vi.fn(),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getUserByUsername: vi.fn(),
  searchUsernames: vi.fn().mockResolvedValue([]),
}))

// Mock the autocomplete with a stub that mirrors the controlled contract
// (value + onChange) and exposes a programmatic onSelect trigger via a
// hidden test-id button. Lets us assert the parent's submit flow on both
// "user typed manually" and "user picked a suggestion".
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
            id: '00000000-0000-0000-0000-000000000222',
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

import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import { useAuth } from '@/hooks/useAuth'
import { getUserByUsername } from '@/services/userService'
import CoOrganizersEditor from '@/components/event/CoOrganizersEditor'
import type { CoOrganizer } from '@/types/coOrganizer'
import type { User } from '@/types/user'

const mockUseCoOrganizers = vi.mocked(useCoOrganizers)
const mockUseAuth = vi.mocked(useAuth)
const mockGetUserByUsername = vi.mocked(getUserByUsername)

const CALLER_USER: User = {
  id: '00000000-0000-0000-0000-000000000999',
  auth0Id: 'auth0|caller',
  email: 'caller@example.com',
  username: 'caller.handle',
  profilePublic: true,
  createdAt: '2026-05-15T10:00:00',
}

const UUID_VALID = '00000000-0000-0000-0000-000000000111'

function resolvedUser(overrides: Partial<User> = {}): User {
  return {
    id: UUID_VALID,
    auth0Id: 'auth0|alice',
    email: 'alice@example.com',
    displayName: 'Alice',
    username: 'alice.martin',
    profilePublic: true,
    createdAt: '2026-05-14T10:00:00',
    ...overrides,
  }
}

function setupHook(overrides: Partial<ReturnType<typeof useCoOrganizers>> = {}) {
  const defaults: ReturnType<typeof useCoOrganizers> = {
    coOrganizers: [],
    loading: false,
    error: null,
    invite: vi.fn().mockResolvedValue({ ok: true }),
    remove: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
  }
  const merged = { ...defaults, ...overrides }
  mockUseCoOrganizers.mockReturnValue(merged)
  return merged
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => {
  mockUseCoOrganizers.mockReset()
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

describe('CoOrganizersEditor', () => {
  it('renders the section header', () => {
    setupHook()
    render(<CoOrganizersEditor eventId={42} />)
    expect(screen.getByRole('region', { name: /co-organisateurs/i })).toBeTruthy()
  })

  it('renders empty state when no co-organizers', () => {
    setupHook({ coOrganizers: [] })
    render(<CoOrganizersEditor eventId={42} />)
    expect(screen.getByText(/aucun co-organisateur/i)).toBeTruthy()
  })

  it('shows validation error for an invalid username', async () => {
    const hook = setupHook()
    render(<CoOrganizersEditor eventId={42} />)
    const input = screen.getByLabelText(/username/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'AB' } }) // too short
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(screen.getByText(/username invalide/i)).toBeTruthy()
    })
    expect(hook.invite).not.toHaveBeenCalled()
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
  })

  it('resolves the username to a UUID and invites on success', async () => {
    const invite = vi.fn().mockResolvedValue({ ok: true })
    setupHook({ invite })
    mockGetUserByUsername.mockResolvedValue(resolvedUser())
    render(<CoOrganizersEditor eventId={42} />)
    const input = screen.getByLabelText(/username/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'alice.martin' } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(mockGetUserByUsername).toHaveBeenCalledWith('alice.martin')
    })
    await waitFor(() => {
      expect(invite).toHaveBeenCalledWith(UUID_VALID)
    })
    await waitFor(() => {
      expect(input.value).toBe('')
    })
  })

  it('lowercases the username before resolving (SCRUM-169 case-insensitivity)', async () => {
    const invite = vi.fn().mockResolvedValue({ ok: true })
    setupHook({ invite })
    mockGetUserByUsername.mockResolvedValue(resolvedUser())
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'Alice.Martin' } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(mockGetUserByUsername).toHaveBeenCalledWith('alice.martin')
    })
  })

  it('shows "Utilisateur introuvable" when the username is not found', async () => {
    const invite = vi.fn()
    setupHook({ invite })
    mockGetUserByUsername.mockResolvedValue(null)
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'unknown' } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(screen.getByText(/Utilisateur introuvable/i)).toBeTruthy()
    })
    expect(invite).not.toHaveBeenCalled()
  })

  it('surfaces invite error from the hook', async () => {
    const invite = vi.fn().mockResolvedValue({ ok: false, error: 'Cet utilisateur est déjà co-organisateur.' })
    setupHook({ invite })
    mockGetUserByUsername.mockResolvedValue(resolvedUser())
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(screen.getByText(/déjà co-organisateur/i)).toBeTruthy()
    })
  })

  it('renders co-organizers list with status chip and remove button', () => {
    const alice: CoOrganizer = {
      id: 1,
      userId: UUID_VALID,
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice.martin',
      status: 'PENDING',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice] })
    render(<CoOrganizersEditor eventId={42} />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('@alice.martin')).toBeTruthy()
    expect(screen.getByText(/en attente/i)).toBeTruthy()
    expect(screen.getByRole('button', { name: /retirer alice/i })).toBeTruthy()
  })

  it('calls remove when × is clicked', () => {
    const remove = vi.fn().mockResolvedValue(undefined)
    const alice: CoOrganizer = {
      id: 1,
      userId: UUID_VALID,
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice.martin',
      status: 'ACCEPTED',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice], remove })
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.click(screen.getByRole('button', { name: /retirer/i }))
    expect(remove).toHaveBeenCalledWith(UUID_VALID)
  })

  // ─── SCRUM-137 polish — username autocomplete ────────────────────────

  it('feeds the picked suggestion to the manual submit flow (autocomplete + invite)', async () => {
    mockGetUserByUsername.mockResolvedValue(resolvedUser({ id: '00000000-0000-0000-0000-000000000222', username: 'picked.user' }))
    const hook = setupHook()
    render(<CoOrganizersEditor eventId={42} />)

    // 1. User picks a suggestion → fills the input via onSelect.
    fireEvent.click(screen.getByTestId('pick-suggestion'))
    const input = screen.getByLabelText('username-input-stub') as HTMLInputElement
    expect(input.value).toBe('picked.user')

    // 2. Submit flow resolves the picked handle and invites by UUID.
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledWith('picked.user'))
    await waitFor(() => expect(hook.invite).toHaveBeenCalledWith('00000000-0000-0000-0000-000000000222'))
  })

  it('passes the caller handle + accepted co-organizers to excludeUsernames', () => {
    const alice: CoOrganizer = {
      id: 1,
      userId: UUID_VALID,
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice.martin',
      status: 'ACCEPTED',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice] })
    render(<CoOrganizersEditor eventId={42} />)
    // 1 caller + 1 existing co-org → 2 excluded handles passed to the dropdown.
    expect(screen.getByTestId('exclude-len').textContent).toBe('2')
  })
})
