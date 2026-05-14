import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react'

vi.mock('@/hooks/useCoOrganizers', () => ({
  useCoOrganizers: vi.fn(),
}))

import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import CoOrganizersEditor from '@/components/event/CoOrganizersEditor'
import type { CoOrganizer } from '@/types/coOrganizer'

const mockUseCoOrganizers = vi.mocked(useCoOrganizers)

const UUID_VALID = '00000000-0000-0000-0000-000000000111'

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

  it('shows validation error for invalid UUID', async () => {
    const hook = setupHook()
    render(<CoOrganizersEditor eventId={42} />)
    const input = screen.getByLabelText(/UUID/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'not-a-uuid' } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(screen.getByText(/UUID v4/i)).toBeTruthy()
    })
    expect(hook.invite).not.toHaveBeenCalled()
  })

  it('calls invite with valid UUID and clears field on success', async () => {
    const invite = vi.fn().mockResolvedValue({ ok: true })
    setupHook({ invite })
    render(<CoOrganizersEditor eventId={42} />)
    const input = screen.getByLabelText(/UUID/i) as HTMLInputElement
    fireEvent.change(input, { target: { value: UUID_VALID } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(invite).toHaveBeenCalledWith(UUID_VALID)
    })
    await waitFor(() => {
      expect(input.value).toBe('')
    })
  })

  it('surfaces invite error from the hook', async () => {
    const invite = vi.fn().mockResolvedValue({ ok: false, error: 'Utilisateur introuvable.' })
    setupHook({ invite })
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.change(screen.getByLabelText(/UUID/i), { target: { value: UUID_VALID } })
    fireEvent.click(screen.getByRole('button', { name: /inviter/i }))
    await waitFor(() => {
      expect(screen.getByText(/Utilisateur introuvable/i)).toBeTruthy()
    })
  })

  it('renders co-organizers list with status chip and remove button', () => {
    const alice: CoOrganizer = {
      id: 1,
      userId: UUID_VALID,
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice',
      status: 'PENDING',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice] })
    render(<CoOrganizersEditor eventId={42} />)
    expect(screen.getByText('Alice')).toBeTruthy()
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
      username: 'alice',
      status: 'ACCEPTED',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice], remove })
    render(<CoOrganizersEditor eventId={42} />)
    fireEvent.click(screen.getByRole('button', { name: /retirer/i }))
    expect(remove).toHaveBeenCalledWith(UUID_VALID)
  })
})
