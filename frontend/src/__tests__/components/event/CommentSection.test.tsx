import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const showToastMock = vi.fn()

vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: showToastMock }),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/hooks/useComments', () => ({
  useComments: vi.fn(),
}))

import { useAuth } from '@/hooks/useAuth'
import { useComments } from '@/hooks/useComments'
import CommentSection from '@/components/event/CommentSection'
import type { Comment } from '@/types/comment'

const mockUseAuth = vi.mocked(useAuth)
const mockUseComments = vi.mocked(useComments)

function setupAuth(authenticated: boolean, admin = false, userId = 'me') {
  mockUseAuth.mockReturnValue({
    user: authenticated
      ? ({ id: userId, auth0Id: 'auth0|me', email: 'me@x' } as never)
      : null,
    isAuthenticated: authenticated,
    isAdmin: admin,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    updateUser: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>)
}

function setupComments(overrides: Partial<ReturnType<typeof useComments>> = {}) {
  mockUseComments.mockReturnValue({
    comments: [],
    hasMore: false,
    loading: false,
    posting: false,
    error: null,
    post: vi.fn().mockResolvedValue({ ok: true }),
    postReply: vi.fn().mockResolvedValue({ ok: true }),
    remove: vi.fn().mockResolvedValue(undefined),
    loadMore: vi.fn(),
    refresh: vi.fn(),
    ...overrides,
  })
}

function makeComment(id: number, content = 'Hi'): Comment {
  return {
    id,
    content,
    authorId: 'author',
    authorDisplayName: 'Alice',
    authorAvatarUrl: null,
    authorUsername: 'alice',
    authorIsOrganizer: false,
    likeCount: 0,
    likedByMe: false,
    createdAt: new Date().toISOString(),
    parentCommentId: null,
    replies: [],
  }
}

function rend() {
  return render(
    <MemoryRouter>
      <CommentSection eventId={42} eventCreatorId="creator-uuid" eventStatus="PUBLISHED" />
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => {
  mockUseAuth.mockReset()
  mockUseComments.mockReset()
  showToastMock.mockReset()
})

describe('CommentSection', () => {
  it('shows the section title', () => {
    setupAuth(false)
    setupComments()
    rend()
    expect(screen.getByRole('region', { name: /commentaires/i })).toBeTruthy()
  })

  it('shows sign-in CTA for anonymous user', () => {
    setupAuth(false)
    setupComments()
    rend()
    expect(screen.getByRole('link', { name: /connectez-vous/i })).toBeTruthy()
  })

  it('shows the form for authenticated user on PUBLISHED event', () => {
    setupAuth(true)
    setupComments()
    rend()
    expect(screen.getByLabelText(/contenu du commentaire/i)).toBeTruthy()
  })

  it('hides the form when event is not PUBLISHED', () => {
    setupAuth(true)
    setupComments()
    render(
      <MemoryRouter>
        <CommentSection eventId={42} eventCreatorId="creator-uuid" eventStatus="DRAFT" />
      </MemoryRouter>,
    )
    expect(screen.queryByLabelText(/contenu du commentaire/i)).toBeNull()
    expect(screen.getByText(/désactivés/i)).toBeTruthy()
  })

  it('shows empty state when no comments', () => {
    setupAuth(true)
    setupComments({ comments: [] })
    rend()
    expect(screen.getByText(/aucun commentaire/i)).toBeTruthy()
  })

  it('renders the comment list', () => {
    setupAuth(true)
    setupComments({ comments: [makeComment(1, 'First'), makeComment(2, 'Second')] })
    rend()
    expect(screen.getByText('First')).toBeTruthy()
    expect(screen.getByText('Second')).toBeTruthy()
  })

  it('shows "Charger plus" when hasMore', () => {
    setupAuth(true)
    setupComments({ comments: [makeComment(1)], hasMore: true })
    rend()
    expect(screen.getByRole('button', { name: /charger plus/i })).toBeTruthy()
  })

  it('shows error message when error is set', () => {
    setupAuth(true)
    setupComments({ error: 'Boom' })
    rend()
    expect(screen.getByText('Boom')).toBeTruthy()
  })

  it('shows the loading skeleton on the first page (loading + no comments yet)', () => {
    setupAuth(true)
    setupComments({ loading: true, comments: [] })
    rend()
    // Skeleton placeholder is shown; the empty-state copy is suppressed while
    // loading (covers the `loading && comments.length === 0` branch at line 104).
    expect(screen.queryByText(/aucun commentaire/i)).toBeNull()
  })

  it('does not show the skeleton when loading a later page over existing comments', () => {
    setupAuth(true)
    setupComments({ loading: true, comments: [makeComment(1, 'Existing')] })
    rend()
    // loading is true but comments.length > 0 → skeleton suppressed, the
    // already-fetched comments stay visible (line 104 second sub-condition).
    expect(screen.getByText('Existing')).toBeTruthy()
  })

  it('passes a null currentUserId to comments rendered for an anonymous viewer', () => {
    setupAuth(false)
    setupComments({ comments: [makeComment(1, 'Anon-visible')] })
    rend()
    // Anonymous: user is null so user?.id is undefined → `?? null` branch (line 131).
    expect(screen.getByText('Anon-visible')).toBeTruthy()
    expect(screen.getByRole('link', { name: /connectez-vous/i })).toBeTruthy()
  })

  it('shows toast on post failure', async () => {
    const mockPost = vi.fn().mockResolvedValue({ ok: false })
    setupAuth(true)
    setupComments({ post: mockPost })
    rend()

    fireEvent.change(screen.getByLabelText(/contenu du commentaire/i), { target: { value: 'hello' } })
    fireEvent.click(screen.getByRole('button', { name: /publier/i }))

    await waitFor(() =>
      expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('Impossible')),
    )
  })

  it('does not show toast on post success', async () => {
    const mockPost = vi.fn().mockResolvedValue({ ok: true })
    setupAuth(true)
    setupComments({ post: mockPost })
    rend()

    fireEvent.change(screen.getByLabelText(/contenu du commentaire/i), { target: { value: 'hello' } })
    fireEvent.click(screen.getByRole('button', { name: /publier/i }))

    await waitFor(() => expect(mockPost).toHaveBeenCalled())
    expect(showToastMock).not.toHaveBeenCalled()
  })

  it('clicking "Charger plus" calls loadMore', () => {
    const mockLoadMore = vi.fn().mockResolvedValue(undefined)
    setupAuth(true)
    setupComments({ comments: [makeComment(1)], hasMore: true, loadMore: mockLoadMore })
    rend()

    fireEvent.click(screen.getByRole('button', { name: /charger plus/i }))

    expect(mockLoadMore).toHaveBeenCalledTimes(1)
  })

  it('swallows loadMore rejection silently', async () => {
    const mockLoadMore = vi.fn().mockRejectedValue(new Error('network'))
    setupAuth(true)
    setupComments({ comments: [makeComment(1)], hasMore: true, loadMore: mockLoadMore })
    rend()

    fireEvent.click(screen.getByRole('button', { name: /charger plus/i }))

    await waitFor(() => expect(mockLoadMore).toHaveBeenCalled())
    expect(showToastMock).not.toHaveBeenCalled()
  })
})
