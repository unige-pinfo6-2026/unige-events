import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
}))

const mockDuplicateEvent = vi.fn()
vi.mock('@/services/eventApi', () => ({
  duplicateEvent: (...args: unknown[]) => mockDuplicateEvent(...args),
}))

import DuplicateButton from '@/components/event/DuplicateButton'

afterEach(() => {
  cleanup()
  mockNavigate.mockReset()
  mockShowToast.mockReset()
  mockDuplicateEvent.mockReset()
})

function renderBtn(eventId = 1) {
  return render(
    <MemoryRouter>
      <DuplicateButton eventId={eventId} />
    </MemoryRouter>,
  )
}

describe('DuplicateButton', () => {
  it('renders the duplicate button', () => {
    renderBtn()
    expect(screen.getByRole('button', { name: /Dupliquer/i })).toBeTruthy()
  })

  it('navigates to /events/:id/edit on success', async () => {
    mockDuplicateEvent.mockResolvedValue({ id: 99 })
    renderBtn(1)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/99/edit'))
    expect(mockShowToast).toHaveBeenCalledWith('success', expect.any(String))
  })

  it('shows an error toast when the API call fails', async () => {
    mockDuplicateEvent.mockRejectedValue(new Error('fail'))
    renderBtn(1)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.any(String)))
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('disables the button and shows loading text while the request is in flight', async () => {
    let resolve!: (v: { id: number }) => void
    mockDuplicateEvent.mockReturnValue(new Promise((r) => { resolve = r }))
    renderBtn(1)
    const btn = screen.getByRole('button') as HTMLButtonElement
    fireEvent.click(btn)
    expect(btn.disabled).toBe(true)
    expect(btn.textContent).toMatch(/Duplication/i)
    resolve({ id: 5 })
    await waitFor(() => expect(mockNavigate).toHaveBeenCalled())
  })

  it('calls duplicateEvent with the correct eventId', async () => {
    mockDuplicateEvent.mockResolvedValue({ id: 7 })
    renderBtn(42)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(mockDuplicateEvent).toHaveBeenCalledWith(42))
  })
})
