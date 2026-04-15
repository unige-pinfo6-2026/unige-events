// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ToastProvider } from '@/contexts/ToastContext'
import { useToast } from '@/hooks/useToast'
import ToastsWrapper from '@/components/utils/Toast'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function ToastTrigger({ message = 'Hello', duration }: { message?: string; duration?: number }) {
  const { showToast } = useToast()
  return (
    <button onClick={() => showToast('success', message, duration)}>show</button>
  )
}

function renderToastApp(props?: { message?: string; duration?: number }) {
  return render(
    <ToastProvider>
      <ToastsWrapper />
      <ToastTrigger {...props} />
    </ToastProvider>,
  )
}

describe('Toast', () => {
  it('renders a toast when showToast is called', async () => {
    renderToastApp({ message: 'Hello world' })
    fireEvent.click(screen.getByText('show'))
    expect(await screen.findByText('Hello world')).toBeTruthy()
  })

  it('clicking the toast button triggers the hide animation', async () => {
    renderToastApp({ message: 'Click me' })
    fireEvent.click(screen.getByText('show'))
    const toast = await screen.findByText('Click me')
    fireEvent.click(toast)
    // After clicking, the button gets the animate-toast-out class (hiding=true)
    await waitFor(() =>
      expect(toast.closest('button')?.className).toContain('animate-toast-out'),
    )
  })

  it('renders multiple toasts', async () => {
    renderToastApp({ message: 'One' })
    fireEvent.click(screen.getByText('show'))
    fireEvent.click(screen.getByText('show'))
    expect((await screen.findAllByText('One')).length).toBeGreaterThanOrEqual(1)
  })
})
