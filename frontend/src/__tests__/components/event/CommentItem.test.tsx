import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactElement } from 'react'

const showToastMock = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

vi.mock('@/services/commentApi', async () => {
  const actual = await vi.importActual<typeof import('@/services/commentApi')>('@/services/commentApi')
  return {
    ...actual,
    likeComment: vi.fn().mockResolvedValue({ liked: true, likeCount: 1 }),
    unlikeComment: vi.fn().mockResolvedValue(undefined),
  }
})

import CommentItem from '@/components/event/CommentItem'
import { likeComment, unlikeComment } from '@/services/commentApi'
import type { Comment } from '@/types/comment'

const mockLike = vi.mocked(likeComment)
const mockUnlike = vi.mocked(unlikeComment)

function renderItem(ui: ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    content: 'Bonjour tout le monde',
    authorId: 'author-uuid',
    authorDisplayName: 'Alice',
    authorAvatarUrl: null,
    authorUsername: 'alice',
    authorIsOrganizer: false,
    likeCount: 0,
    likedByMe: false,
    createdAt: new Date(Date.now() - 60000).toISOString(),
    parentCommentId: null,
    replies: [],
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => {
  showToastMock.mockReset()
  mockLike.mockReset()
  mockUnlike.mockReset()
  mockLike.mockResolvedValue({ liked: true, likeCount: 1 })
  mockUnlike.mockResolvedValue(undefined)
})

describe('CommentItem', () => {
  it('renders content + displayName', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId="other-user"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByText('Bonjour tout le monde')).toBeTruthy()
    expect(screen.getByText('Alice')).toBeTruthy()
  })

  it('renders Organisateur badge when authorIsOrganizer is true', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorIsOrganizer: true })}
        eventCreatorId="creator-uuid"
        currentUserId={null}
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByText('Organisateur')).toBeTruthy()
  })

  it('shows Répondre button only for top-level + authenticated', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByRole('button', { name: /répondre/i })).toBeTruthy()
  })

  it('hides Répondre on a reply', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        isReply
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.queryByRole('button', { name: /répondre/i })).toBeNull()
  })

  it('hides Répondre and Supprimer for anonymous users', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId={null}
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.queryByRole('button', { name: /répondre/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /supprimer/i })).toBeNull()
  })

  it('shows Supprimer for the author', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorId: 'me' })}
        eventCreatorId="creator-uuid"
        currentUserId="me"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByRole('button', { name: /supprimer/i })).toBeTruthy()
  })

  it('opens a ConfirmDialog (not browser confirm) when Supprimer is clicked', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    renderItem(
      <CommentItem
        comment={makeComment({ id: 7, authorId: 'me' })}
        eventCreatorId="creator-uuid"
        currentUserId="me"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={onDelete}
        posting={false}
      />,
    )
    // Click the inline Supprimer link — opens the modal, does NOT call onDelete yet.
    fireEvent.click(screen.getByRole('button', { name: /^supprimer$/i }))
    expect(onDelete).not.toHaveBeenCalled()
    // Modal title is visible.
    expect(screen.getByText(/supprimer ce commentaire/i)).toBeTruthy()
    // Click Confirmer.
    fireEvent.click(screen.getByRole('button', { name: /^confirmer$/i }))
    // Wait for the async onDelete to fire.
    await vi.waitFor(() => expect(onDelete).toHaveBeenCalledWith(7))
  })

  it('closes the ConfirmDialog without calling onDelete when Annuler is clicked', () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    renderItem(
      <CommentItem
        comment={makeComment({ authorId: 'me' })}
        eventCreatorId="creator-uuid"
        currentUserId="me"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={onDelete}
        posting={false}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /^supprimer$/i }))
    expect(screen.getByText(/supprimer ce commentaire/i)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /^annuler$/i }))
    expect(onDelete).not.toHaveBeenCalled()
    expect(screen.queryByText(/supprimer ce commentaire/i)).toBeNull()
  })

  it('shows Supprimer for the event creator', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorId: 'someone-else' })}
        eventCreatorId="creator-uuid"
        currentUserId="creator-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByRole('button', { name: /supprimer/i })).toBeTruthy()
  })

  it('shows Supprimer for admin', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByRole('button', { name: /supprimer/i })).toBeTruthy()
  })

  it('toggles reply form when Répondre is clicked', () => {
    renderItem(
      <CommentItem
        comment={makeComment()}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /répondre/i }))
    expect(screen.getByLabelText(/contenu du commentaire/i)).toBeTruthy()
  })

  it('shows informational toast when Signaler is clicked (Décision B)', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorId: 'someone-else' })}
        eventCreatorId="creator-uuid"
        currentUserId="me"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /signaler/i }))
    expect(showToastMock).toHaveBeenCalled()
    expect(showToastMock.mock.calls[0][1]).toMatch(/bientôt/i)
  })

  it('renders replies recursively', () => {
    const reply = makeComment({ id: 2, content: 'Une réponse', parentCommentId: 1 })
    renderItem(
      <CommentItem
        comment={makeComment({ replies: [reply] })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByText('Une réponse')).toBeTruthy()
  })

  // SCRUM-169 — author label fallback order : displayName -> @username ->
  // UUID prefix. Pre-2026-05-14 the @username step did not exist
  // (CommentDTO didn't carry authorUsername), so the helper would land
  // directly on the UUID prefix.

  it('falls back to @username when displayName is null', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorDisplayName: null, authorUsername: 'alice.martin' })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByText('@alice.martin')).toBeTruthy()
  })

  it('falls back to UUID prefix when displayName and username are both null', () => {
    renderItem(
      <CommentItem
        comment={makeComment({
          authorDisplayName: null,
          authorUsername: null,
          authorId: '19f3ab78-0fbf-4cfb-896e-5c0346fabed5',
        })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.getByText('19f3ab78')).toBeTruthy()
  })

  // SCRUM-169 — author name + avatar link to /profile/{username}. Falls back to
  // /profile/{uuid} when username is null (orphan row) — `ProfilePage` then
  // redirects to the canonical username URL. When both are null we skip the link.

  it('links the author to /profile/:username (SCRUM-169)', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ authorUsername: 'alice.martin' })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const link = screen.getByRole('link', { name: 'Alice' })
    expect(link.getAttribute('href')).toBe('/profile/alice.martin')
  })

  it('falls back to /profile/:uuid when username is null', () => {
    renderItem(
      <CommentItem
        comment={makeComment({
          authorDisplayName: 'Bob',
          authorUsername: null,
          authorId: '19f3ab78-0fbf-4cfb-896e-5c0346fabed5',
        })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const link = screen.getByRole('link', { name: 'Bob' })
    expect(link.getAttribute('href')).toBe('/profile/19f3ab78-0fbf-4cfb-896e-5c0346fabed5')
  })

  it('skips the link when both authorUsername and authorId are null', () => {
    renderItem(
      <CommentItem
        comment={makeComment({
          authorDisplayName: 'Anon',
          authorUsername: null,
          authorId: null,
        })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.queryByRole('link', { name: /Anon/i })).toBeNull()
    expect(screen.getByText('Anon')).toBeTruthy()
  })

  // ─── SCRUM-147 like button ──────────────────────────────────────────

  it('renders the like button for every visitor including anonymous (disabled)', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ likeCount: 4, likedByMe: false })}
        eventCreatorId="creator-uuid"
        currentUserId={null}
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const btn = screen.getByRole('button', { name: /Aimer ce commentaire/i })
    expect(btn).toBeTruthy()
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    expect(btn.getAttribute('title')).toBe('Connectez-vous pour aimer')
    expect(screen.getByText('4')).toBeTruthy()
  })

  it('shows aria-label Retirer le like when liked and renders count', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ likeCount: 3, likedByMe: true })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const btn = screen.getByRole('button', { name: /Retirer le like/i })
    expect(btn.getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByText('3')).toBeTruthy()
  })

  it('hides the count when likeCount is 0', () => {
    renderItem(
      <CommentItem
        comment={makeComment({ likeCount: 0, likedByMe: false })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    expect(screen.queryByText('0')).toBeNull()
  })

  it('toggle optimistic flip then resolves on 2xx', async () => {
    mockLike.mockResolvedValue({ liked: true, likeCount: 6 })
    renderItem(
      <CommentItem
        comment={makeComment({ likeCount: 5, likedByMe: false })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const btn = screen.getByRole('button', { name: /Aimer ce commentaire/i })
    fireEvent.click(btn)
    // Optimistic — flips immediately, count goes to 6
    await waitFor(() => expect(screen.getByText('6')).toBeTruthy())
    expect(mockLike).toHaveBeenCalledWith(1)
  })

  it('rolls back the optimistic flip when the like API errors', async () => {
    mockLike.mockRejectedValue(new Error('boom'))
    renderItem(
      <CommentItem
        comment={makeComment({ likeCount: 5, likedByMe: false })}
        eventCreatorId="creator-uuid"
        currentUserId="user-uuid"
        isAdmin={false}
        onReply={vi.fn().mockResolvedValue({ ok: true })}
        onDelete={vi.fn().mockResolvedValue(undefined)}
        posting={false}
      />,
    )
    const btn = screen.getByRole('button', { name: /Aimer ce commentaire/i })
    fireEvent.click(btn)
    // Eventually rolls back to 5 + shows error toast
    await waitFor(() => expect(showToastMock).toHaveBeenCalledWith('error', expect.any(String)))
    expect(screen.getByText('5')).toBeTruthy()
  })
})
