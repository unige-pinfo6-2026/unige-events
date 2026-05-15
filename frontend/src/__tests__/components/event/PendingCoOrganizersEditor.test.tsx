import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react'

vi.mock('@/services/userService', () => ({
  getUserByUsername: vi.fn(),
}))

import { getUserByUsername } from '@/services/userService'
import PendingCoOrganizersEditor, {
  type PendingCoOrganizer,
} from '@/components/event/PendingCoOrganizersEditor'
import type { User } from '@/types/user'

const mockGetUserByUsername = vi.mocked(getUserByUsername)

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
})
