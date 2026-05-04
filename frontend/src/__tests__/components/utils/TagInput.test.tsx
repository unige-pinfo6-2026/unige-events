
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import TagInput from '@/components/utils/TagInput'

afterEach(() => { cleanup() })

describe('TagInput', () => {
  it('renders existing tags as chips', () => {
    render(<TagInput value={['react', 'typescript']} onChange={vi.fn()} />)
    expect(screen.getByText('react')).toBeTruthy()
    expect(screen.getByText('typescript')).toBeTruthy()
  })

  it('renders placeholder when no tags', () => {
    render(<TagInput value={[]} onChange={vi.fn()} placeholder="Ajoutez des tags…" />)
    expect(screen.getByPlaceholderText('Ajoutez des tags…')).toBeTruthy()
  })

  it('hides placeholder when tags are present', () => {
    render(<TagInput value={['react']} onChange={vi.fn()} placeholder="Ajoutez des tags…" />)
    expect((screen.getByRole('textbox') as HTMLInputElement).placeholder).toBe('')
  })

  it('adds a tag on Enter', () => {
    const onChange = vi.fn()
    render(<TagInput value={[]} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'vitest' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onChange).toHaveBeenCalledWith(['vitest'])
  })

  it('adds a tag on comma', () => {
    const onChange = vi.fn()
    render(<TagInput value={[]} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'vitest' } })
    fireEvent.keyDown(input, { key: ',' })
    expect(onChange).toHaveBeenCalledWith(['vitest'])
  })

  it('does not add a duplicate tag', () => {
    const onChange = vi.fn()
    render(<TagInput value={['react']} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'react' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('does not add an empty tag', () => {
    const onChange = vi.fn()
    render(<TagInput value={[]} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('removes the last tag on Backspace when input is empty', () => {
    const onChange = vi.fn()
    render(<TagInput value={['react', 'typescript']} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.keyDown(input, { key: 'Backspace' })
    expect(onChange).toHaveBeenCalledWith(['react'])
  })

  it('does not remove a tag on Backspace when input has text', () => {
    const onChange = vi.fn()
    render(<TagInput value={['react']} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'ty' } })
    fireEvent.keyDown(input, { key: 'Backspace' })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('removes a tag when clicking its × button', () => {
    const onChange = vi.fn()
    render(<TagInput value={['react', 'typescript']} onChange={onChange} />)
    fireEvent.click(screen.getByLabelText('Remove tag react'))
    expect(onChange).toHaveBeenCalledWith(['typescript'])
  })

  it('adds a tag on blur if input has text', () => {
    const onChange = vi.fn()
    render(<TagInput value={[]} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'vitest' } })
    fireEvent.blur(input)
    expect(onChange).toHaveBeenCalledWith(['vitest'])
  })

  it('does not exceed maxTags', () => {
    const onChange = vi.fn()
    render(<TagInput value={['a', 'b']} onChange={onChange} maxTags={2} />)
    expect(screen.queryByRole('textbox')).toBeNull()
  })

  it('trims whitespace from tag input', () => {
    const onChange = vi.fn()
    render(<TagInput value={[]} onChange={onChange} />)
    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: '  vitest  ' } })
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onChange).toHaveBeenCalledWith(['vitest'])
  })

  it('forwards maxLength to the underlying input', () => {
    const { container } = render(
      <TagInput value={[]} onChange={vi.fn()} maxLength={16} />,
    )
    const input = container.querySelector('input')!
    expect(input.maxLength).toBe(16)
  })
})
