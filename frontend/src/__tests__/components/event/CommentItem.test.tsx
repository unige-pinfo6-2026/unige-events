import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent } from '@testing-library/react'

const showToastMock = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

import CommentItem from '@/components/event/CommentItem'
import type { Comment } from '@/types/comment'

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    content: 'Bonjour tout le monde',
    authorId: 'author-uuid',
    authorDisplayName: 'Alice',
    authorAvatarUrl: null,
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

beforeEach(() => showToastMock.mockReset())

describe('CommentItem', () => {
  it('renders content + displayName', () => {
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
    render(
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
})
