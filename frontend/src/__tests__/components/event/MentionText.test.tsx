import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import MentionText from '@/components/event/MentionText'

function renderWithRouter(content: string) {
  return render(
    <MemoryRouter>
      <MentionText content={content} />
    </MemoryRouter>,
  )
}

describe('MentionText', () => {
  it('renders plain text untouched', () => {
    renderWithRouter('hello world')
    expect(screen.getByText('hello world')).toBeTruthy()
  })

  it('renders an @<handle> token as a Link to /profile/<handle>', () => {
    const { container } = renderWithRouter('hi @daniel !')
    const link = container.querySelector('a[href="/profile/daniel"]')
    expect(link).toBeTruthy()
    expect(link?.textContent).toBe('@daniel')
  })

  it('lowercases the link target while preserving displayed casing', () => {
    const { container } = renderWithRouter('@Daniel')
    const link = container.querySelector('a[href="/profile/daniel"]')
    expect(link).toBeTruthy()
    expect(link?.textContent).toBe('@Daniel')
  })

  it('renders multiple mentions independently', () => {
    const { container } = renderWithRouter('@alice and @bob')
    expect(container.querySelectorAll('a').length).toBe(2)
    expect(container.querySelector('a[href="/profile/alice"]')).toBeTruthy()
    expect(container.querySelector('a[href="/profile/bob"]')).toBeTruthy()
  })

  it('does NOT linkify an email-like token', () => {
    const { container } = renderWithRouter('foo@example.com')
    expect(container.querySelector('a')).toBeNull()
  })

  it('returns null for empty content (defensive)', () => {
    const { container } = renderWithRouter('')
    expect(container.textContent).toBe('')
  })
})
