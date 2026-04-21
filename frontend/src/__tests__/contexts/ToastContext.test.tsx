
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen } from '@testing-library/react'
import { ToastProvider } from '@/contexts/ToastContext'
import { useToast } from '@/hooks/useToast'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function ToastConsumer() {
  const { showToast, toasts, dismiss } = useToast()
  return (
    <div>
      <button onClick={() => showToast('success', 'Hello')}>add success</button>
      <button onClick={() => showToast('error', 'Oops', 3000)}>add error</button>
      {toasts.map(t => (
        <div key={t.id}>
          <span data-testid={`toast-${t.id}`}>{t.message}</span>
          <button onClick={() => dismiss(t.id)}>dismiss-{t.id}</button>
        </div>
      ))}
      <span data-testid="count">{toasts.length}</span>
    </div>
  )
}

describe('ToastContext', () => {
  it('starts with no toasts', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('showToast adds a toast with the correct message', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    act(() => { screen.getByText('add success').click() })
    expect(screen.getByTestId('count').textContent).toBe('1')
    expect(screen.getByText('Hello')).toBeTruthy()
  })

  it('multiple showToast calls add multiple toasts', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    act(() => { screen.getByText('add success').click() })
    act(() => { screen.getByText('add error').click() })
    expect(screen.getByTestId('count').textContent).toBe('2')
    expect(screen.getByText('Hello')).toBeTruthy()
    expect(screen.getByText('Oops')).toBeTruthy()
  })

  it('each toast gets a unique id', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    act(() => { screen.getByText('add success').click() })
    act(() => { screen.getByText('add success').click() })
    expect(screen.getAllByText('Hello')).toHaveLength(2)
    expect(screen.getByTestId('toast-0')).toBeTruthy()
    expect(screen.getByTestId('toast-1')).toBeTruthy()
  })

  it('dismiss removes the targeted toast', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    act(() => { screen.getByText('add success').click() })
    act(() => { screen.getByText('dismiss-0').click() })
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  it('dismiss only removes the targeted toast, not others', () => {
    render(<ToastProvider><ToastConsumer /></ToastProvider>)
    act(() => { screen.getByText('add success').click() })
    act(() => { screen.getByText('add error').click() })
    act(() => { screen.getByText('dismiss-0').click() })
    expect(screen.getByTestId('count').textContent).toBe('1')
    expect(screen.getByText('Oops')).toBeTruthy()
  })
})
