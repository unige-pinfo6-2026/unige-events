import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/hooks/useCoOrganizerInvitations', () => ({
  useCoOrganizerInvitations: vi.fn(),
}))

import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'
import CoOrganizerInvitationsBadge from '@/components/user/CoOrganizerInvitationsBadge'

const mockHook = vi.mocked(useCoOrganizerInvitations)

function setup(overrides: Partial<ReturnType<typeof useCoOrganizerInvitations>> = {}) {
  mockHook.mockReturnValue({
    invitations: [],
    pendingCount: 0,
    loading: false,
    error: null,
    accept: vi.fn(),
    decline: vi.fn(),
    refresh: vi.fn(),
    ...overrides,
  })
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => mockHook.mockReset())

describe('CoOrganizerInvitationsBadge', () => {
  it('renders nothing when pendingCount is 0', () => {
    setup({ pendingCount: 0 })
    const { container } = render(
      <MemoryRouter>
        <CoOrganizerInvitationsBadge />
      </MemoryRouter>,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders nothing while loading', () => {
    setup({ loading: true, pendingCount: 3 })
    const { container } = render(
      <MemoryRouter>
        <CoOrganizerInvitationsBadge />
      </MemoryRouter>,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders count when pendingCount > 0', () => {
    setup({ pendingCount: 3 })
    render(
      <MemoryRouter>
        <CoOrganizerInvitationsBadge />
      </MemoryRouter>,
    )
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('Invitations')).toBeTruthy()
  })

  it('renders correct accessible label for plural', () => {
    setup({ pendingCount: 2 })
    render(
      <MemoryRouter>
        <CoOrganizerInvitationsBadge />
      </MemoryRouter>,
    )
    const link = screen.getByRole('link', { name: /2 invitations en attente/i })
    expect(link).toBeTruthy()
  })

  it('renders singular label for one', () => {
    setup({ pendingCount: 1 })
    render(
      <MemoryRouter>
        <CoOrganizerInvitationsBadge />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /1 invitation en attente/i })).toBeTruthy()
  })
})
