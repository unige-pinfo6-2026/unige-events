// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('@/services/followApi', () => ({
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
}))

const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast, toasts: [], dismiss: vi.fn() }),
}))

import { followUser, unfollowUser } from '@/services/followApi'
import FollowButton from '@/components/user/FollowButton'

const mockFollow = followUser as ReturnType<typeof vi.fn>
const mockUnfollow = unfollowUser as ReturnType<typeof vi.fn>

const TARGET = 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c'

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('FollowButton', () => {
  describe('idle (followStatus = null)', () => {
    it('renders "Suivre" with the right aria + title attributes', () => {
      render(<FollowButton targetId={TARGET} followStatus={null} />)

      const btn = screen.getByRole('button', { name: 'Suivre cet utilisateur' })
      expect(btn.textContent).toBe('Suivre')
      expect(btn.getAttribute('aria-pressed')).toBe('false')
      expect(btn.getAttribute('title')).toBe('Suivre')
    })

    it('clicking POSTs, disables the button during the request, then calls onMutated', async () => {
      // No optimistic flip — the button stays "Suivre" (disabled) while the
      // request is in flight. The parent's onMutated refetch drives the final
      // state (ACCEPTED for public targets, PENDING for private), avoiding the
      // "Demande envoyée → Abonné" flash on public profiles.
      mockFollow.mockResolvedValue({
        id: 1, followerId: 'me', followedId: TARGET, status: 'PENDING', createdAt: 'x',
      })
      const onMutated = vi.fn()
      render(<FollowButton targetId={TARGET} followStatus={null} onMutated={onMutated} />)

      const btn = screen.getByRole('button')
      fireEvent.click(btn)

      expect(mockFollow).toHaveBeenCalledWith(TARGET)
      await waitFor(() => expect(onMutated).toHaveBeenCalledTimes(1))
      // Button is re-enabled and still shows "Suivre" until parent updates prop.
      await waitFor(() => expect((btn as HTMLButtonElement).disabled).toBe(false))
      expect(screen.getByText('Suivre')).toBeTruthy()
    })

    it('rolls back to "Suivre" + toasts on server error', async () => {
      mockFollow.mockRejectedValue(new Error('network'))
      const onMutated = vi.fn()
      render(<FollowButton targetId={TARGET} followStatus={null} onMutated={onMutated} />)

      fireEvent.click(screen.getByRole('button'))

      await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith(
        'error', 'Impossible de mettre à jour le suivi.',
      ))
      expect(screen.getByText('Suivre')).toBeTruthy()
      expect(onMutated).not.toHaveBeenCalled()
    })

    it('treats undefined followStatus as idle', () => {
      render(<FollowButton targetId={TARGET} />)
      expect(screen.getByText('Suivre')).toBeTruthy()
    })
  })

  describe('pending (followStatus = "PENDING")', () => {
    it('renders "Demande envoyée" with cancel-tooltip + aria-pressed=true', () => {
      render(<FollowButton targetId={TARGET} followStatus="PENDING" />)

      const btn = screen.getByRole('button', { name: 'Annuler la demande de suivi' })
      expect(btn.textContent).toBe('Demande envoyée')
      expect(btn.getAttribute('aria-pressed')).toBe('true')
      expect(btn.getAttribute('title')).toBe('Cliquer pour annuler')
    })

    it('clicking DELETEs, calls onMutated, and shows "Suivre" after parent re-renders with null', async () => {
      // No optimistic flip — button stays "Demande envoyée" until the parent
      // refetch (via onMutated) re-renders with followStatus=null.
      mockUnfollow.mockResolvedValue(undefined)
      const onMutated = vi.fn()
      const { rerender } = render(<FollowButton targetId={TARGET} followStatus="PENDING" onMutated={onMutated} />)

      fireEvent.click(screen.getByRole('button'))

      expect(mockUnfollow).toHaveBeenCalledWith(TARGET)
      await waitFor(() => expect(onMutated).toHaveBeenCalledTimes(1))

      // Simulate the parent refetch resolving with null (no longer following).
      rerender(<FollowButton targetId={TARGET} followStatus={null} onMutated={onMutated} />)
      await waitFor(() => expect(screen.getByText('Suivre')).toBeTruthy())
    })

    it('rolls back + toasts on cancel error', async () => {
      mockUnfollow.mockRejectedValue(new Error('boom'))
      render(<FollowButton targetId={TARGET} followStatus="PENDING" />)

      fireEvent.click(screen.getByRole('button'))

      await waitFor(() => expect(mockShowToast).toHaveBeenCalled())
      expect(screen.getByText('Demande envoyée')).toBeTruthy()
    })
  })

  describe('accepted (followStatus = "ACCEPTED")', () => {
    it('renders both labels for the CSS hover swap (default + group-hover)', () => {
      render(<FollowButton targetId={TARGET} followStatus="ACCEPTED" />)

      const btn = screen.getByRole('button', { name: 'Se désabonner' })
      // Both spans render; CSS controls which is visible. The DOM contains both.
      expect(screen.getByText('Abonné')).toBeTruthy()
      expect(screen.getByText('Se désabonner')).toBeTruthy()
      expect(btn.getAttribute('aria-pressed')).toBe('true')
      // The container is the group so hover styling cascades to the spans.
      expect(btn.className).toContain('group')
    })

    it('clicking DELETEs, calls onMutated, and shows "Suivre" after parent re-renders with null', async () => {
      // No optimistic flip — button stays "Abonné" until the parent refetch
      // (via onMutated) re-renders with followStatus=null.
      mockUnfollow.mockResolvedValue(undefined)
      const onMutated = vi.fn()
      const { rerender } = render(<FollowButton targetId={TARGET} followStatus="ACCEPTED" onMutated={onMutated} />)

      fireEvent.click(screen.getByRole('button'))

      expect(mockUnfollow).toHaveBeenCalledWith(TARGET)
      await waitFor(() => expect(onMutated).toHaveBeenCalled())

      // Simulate the parent refetch resolving with null (no longer following).
      rerender(<FollowButton targetId={TARGET} followStatus={null} onMutated={onMutated} />)
      await waitFor(() => expect(screen.getByText('Suivre')).toBeTruthy())
    })
  })

  describe('prop sync (real bug from Copilot review)', () => {
    it('adopts the parent followStatus when it changes (e.g. auto-ACCEPT on public target)', async () => {
      // User clicks Suivre on a public profile → server auto-ACCEPTs →
      // parent refetches → followStatus prop flips to 'ACCEPTED'. The button
      // must adopt the new value (no optimistic state to fight it).
      const { rerender } = render(<FollowButton targetId={TARGET} followStatus={null} />)
      expect(screen.getByText('Suivre')).toBeTruthy()

      rerender(<FollowButton targetId={TARGET} followStatus="ACCEPTED" />)
      await waitFor(() => expect(screen.getByRole('button', { name: 'Se désabonner' })).toBeTruthy())
    })

    it('does NOT re-enable or reset the button while a mutation is in flight', async () => {
      // Without an optimistic flip the button stays "Suivre" (disabled) during
      // the round-trip. If the parent re-renders with the same stale prop the
      // `pending` guard must prevent the effect from flipping state early.
      let resolveFn: () => void = () => {}
      mockFollow.mockImplementation(() => new Promise<unknown>(resolve => {
        resolveFn = () => resolve({
          id: 1, followerId: 'me', followedId: TARGET, status: 'PENDING', createdAt: 'x',
        })
      }))

      const { rerender } = render(<FollowButton targetId={TARGET} followStatus={null} />)
      const btn = screen.getByRole('button') as HTMLButtonElement
      fireEvent.click(btn)

      // Button is disabled while the request is in-flight.
      await waitFor(() => expect(btn.disabled).toBe(true))

      // Parent re-renders with the still-stale `null` prop mid-flight — the
      // button must stay disabled (not reset to "Suivre" enabled).
      rerender(<FollowButton targetId={TARGET} followStatus={null} />)
      expect(btn.disabled).toBe(true)

      // Once the mutation settles, prop sync re-engages and button re-enables.
      act(() => { resolveFn() })
      await waitFor(() => expect(btn.disabled).toBe(false))
    })
  })

  describe('concurrency guard', () => {
    it('disables the button while a mutation is in flight', async () => {
      let resolveFn: () => void = () => {}
      mockFollow.mockImplementation(() => new Promise<unknown>(resolve => {
        resolveFn = () => resolve({
          id: 1, followerId: 'me', followedId: TARGET, status: 'PENDING', createdAt: 'x',
        })
      }))

      render(<FollowButton targetId={TARGET} followStatus={null} />)
      const btn = screen.getByRole('button') as HTMLButtonElement

      fireEvent.click(btn)
      await waitFor(() => expect(btn.disabled).toBe(true))

      // Second click during in-flight is a no-op (we assert via call count).
      fireEvent.click(btn)
      expect(mockFollow).toHaveBeenCalledTimes(1)

      resolveFn()
      await waitFor(() => expect(btn.disabled).toBe(false))
    })
  })
})
