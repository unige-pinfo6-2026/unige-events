import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import UserBanner from '@/components/user/UserBanner'

afterEach(() => cleanup())

describe('UserBanner', () => {
  it('renders the gradient fallback when user is null', () => {
    const { container } = render(<UserBanner user={null} />)
    expect(container.querySelector('.from-accent\\/20')).toBeTruthy()
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders the gradient fallback when bannerUrl is null', () => {
    const { container } = render(<UserBanner user={{ bannerUrl: null }} />)
    expect(container.querySelector('.from-accent\\/20')).toBeTruthy()
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders an img when bannerUrl is set', () => {
    render(<UserBanner user={{ bannerUrl: 'https://example.com/banner.jpg' }} />)
    const img = document.querySelector('img')
    expect(img).toBeTruthy()
    expect(img?.src).toContain('banner.jpg')
  })

  it('falls back to gradient when the img fires onError (line 26)', () => {
    const { container } = render(
      <UserBanner user={{ bannerUrl: 'https://example.com/banner.jpg' }} />,
    )
    const img = document.querySelector('img')!
    expect(img).toBeTruthy()

    // Simulate the browser failing to load the image.
    fireEvent.error(img)

    // Image is replaced by the gradient fallback div.
    expect(document.querySelector('img')).toBeNull()
    expect(container.querySelector('.from-accent\\/20')).toBeTruthy()
  })

  it('applies the className prop to the wrapper', () => {
    const { container } = render(<UserBanner user={null} className="hero-banner" />)
    expect((container.firstChild as HTMLElement).classList.contains('hero-banner')).toBe(true)
  })

  it('renders children inside the banner', () => {
    render(
      <UserBanner user={null}>
        <span>Profile content</span>
      </UserBanner>,
    )
    expect(screen.getByText('Profile content')).toBeTruthy()
  })
})
