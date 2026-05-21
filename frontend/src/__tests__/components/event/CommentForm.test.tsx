import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react'
import CommentForm from '@/components/event/CommentForm'

afterEach(() => cleanup())

describe('CommentForm', () => {
  it('renders textarea and submit button', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    expect(screen.getByLabelText(/contenu du commentaire/i)).toBeTruthy()
    expect(screen.getByRole('button', { name: /publier/i })).toBeTruthy()
  })

  it('disables submit when content is empty', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    const btn = screen.getByRole('button', { name: /publier/i })
    expect(btn.hasAttribute('disabled')).toBe(true)
  })

  it('enables submit when content is non-empty', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    const input = screen.getByLabelText(/contenu du commentaire/i)
    fireEvent.change(input, { target: { value: 'Hello' } })
    const btn = screen.getByRole('button', { name: /publier/i })
    expect(btn.hasAttribute('disabled')).toBe(false)
  })

  it('calls onSubmit and clears the field on success', async () => {
    const onSubmit = vi.fn().mockResolvedValue({ ok: true })
    render(<CommentForm onSubmit={onSubmit} submitting={false} />)
    const input = screen.getByLabelText(/contenu du commentaire/i) as HTMLTextAreaElement
    fireEvent.change(input, { target: { value: 'Hello' } })
    fireEvent.click(screen.getByRole('button', { name: /publier/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('Hello'))
    await waitFor(() => expect(input.value).toBe(''))
  })

  it('keeps the field intact when submit fails', async () => {
    const onSubmit = vi.fn().mockResolvedValue({ ok: false })
    render(<CommentForm onSubmit={onSubmit} submitting={false} />)
    const input = screen.getByLabelText(/contenu du commentaire/i) as HTMLTextAreaElement
    fireEvent.change(input, { target: { value: 'Hello' } })
    fireEvent.click(screen.getByRole('button', { name: /publier/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(input.value).toBe('Hello')
  })

  it('renders an Annuler button when onCancel is provided', () => {
    render(
      <CommentForm
        onSubmit={vi.fn().mockResolvedValue({ ok: true })}
        submitting={false}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: /annuler/i })).toBeTruthy()
  })

  it('calls onCancel when Annuler is clicked', () => {
    const onCancel = vi.fn()
    render(
      <CommentForm
        onSubmit={vi.fn().mockResolvedValue({ ok: true })}
        submitting={false}
        onCancel={onCancel}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /annuler/i }))
    expect(onCancel).toHaveBeenCalled()
  })

  it('shows the character counter', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    expect(screen.getByText(/500 caractères restants/)).toBeTruthy()
  })

  // ─── SCRUM-147 extensions ──────────────────────────────────────────

  it('seeds the textarea with initialValue on mount (SCRUM-147 reply prefix)', () => {
    render(
      <CommentForm
        onSubmit={vi.fn().mockResolvedValue({ ok: true })}
        submitting={false}
        initialValue="@alice.dosh "
      />,
    )
    const input = screen.getByLabelText(/contenu du commentaire/i) as HTMLTextAreaElement
    expect(input.value).toBe('@alice.dosh ')
  })

  it('focuses the textarea and places caret at end of initialValue when autoFocus', () => {
    render(
      <CommentForm
        onSubmit={vi.fn().mockResolvedValue({ ok: true })}
        submitting={false}
        autoFocus
        initialValue="@alice.dosh "
      />,
    )
    const input = screen.getByLabelText(/contenu du commentaire/i) as HTMLTextAreaElement
    expect(document.activeElement).toBe(input)
    expect(input.selectionStart).toBe(12)
    expect(input.selectionEnd).toBe(12)
  })

  it('uses the singular wording when exactly 1 char remains', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    const input = screen.getByLabelText(/contenu du commentaire/i)
    fireEvent.change(input, { target: { value: 'a'.repeat(499) } })
    expect(screen.getByText(/^1 caractère restant$/)).toBeTruthy()
  })

  it('disables submit and styles the counter as error when over the 500-char cap', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting={false} />)
    const input = screen.getByLabelText(/contenu du commentaire/i)
    // maxLength on the textarea is 501 (cap+1) so the change can land then
    // the in-code guard blocks the submit.
    fireEvent.change(input, { target: { value: 'a'.repeat(501) } })
    const btn = screen.getByRole('button', { name: /publier/i })
    expect(btn.hasAttribute('disabled')).toBe(true)
    // The counter element should be the error-styled one.
    expect(screen.getByText(/-1 caractère restant/i)).toBeTruthy()
  })

  it('honours submitLabel prop (e.g. "Répondre")', () => {
    render(
      <CommentForm
        onSubmit={vi.fn().mockResolvedValue({ ok: true })}
        submitting={false}
        submitLabel="Répondre"
      />,
    )
    expect(screen.getByRole('button', { name: /répondre/i })).toBeTruthy()
  })

  it('disables the textarea while submitting', () => {
    render(<CommentForm onSubmit={vi.fn().mockResolvedValue({ ok: true })} submitting />)
    const input = screen.getByLabelText(/contenu du commentaire/i) as HTMLTextAreaElement
    expect(input.disabled).toBe(true)
  })
})
