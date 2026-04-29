
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import FormField, { Input, Select, Textarea } from '@/components/utils/FormField'

afterEach(() => { cleanup() })

describe('Input', () => {
  it('renders an input element', () => {
    render(<Input placeholder="Saisir..." />)
    expect(screen.getByPlaceholderText('Saisir...')).toBeTruthy()
  })

  it('applies error border class when error is provided', () => {
    const { container } = render(<Input error="Champ requis" />)
    expect(container.querySelector('input')?.className).toContain('border-error')
  })

  it('applies default border class when no error', () => {
    const { container } = render(<Input />)
    expect(container.querySelector('input')?.className).toContain('border-border')
  })

  it('passes additional props (id, name)', () => {
    render(<Input id="my-input" name="my-name" />)
    const input = document.getElementById('my-input')
    expect(input).toBeTruthy()
    expect((input as HTMLInputElement).name).toBe('my-name')
  })
})

describe('Select', () => {
  it('renders a select element with children', () => {
    render(
      <Select>
        <option value="a">Option A</option>
      </Select>,
    )
    expect(screen.getByRole('combobox')).toBeTruthy()
    expect(screen.getByText('Option A')).toBeTruthy()
  })

  it('applies error border class when error is provided', () => {
    const { container } = render(<Select error="Requis"><option>x</option></Select>)
    expect(container.querySelector('select')?.className).toContain('border-error')
  })

  it('applies default border class when no error', () => {
    const { container } = render(<Select><option>x</option></Select>)
    expect(container.querySelector('select')?.className).toContain('border-border')
  })
})

describe('Textarea', () => {
  it('renders a textarea element', () => {
    render(<Textarea placeholder="Description..." />)
    expect(screen.getByPlaceholderText('Description...')).toBeTruthy()
  })

  it('applies error border class when error is provided', () => {
    const { container } = render(<Textarea error="Requis" />)
    expect(container.querySelector('textarea')?.className).toContain('border-error')
  })

  it('applies default border class when no error', () => {
    const { container } = render(<Textarea />)
    expect(container.querySelector('textarea')?.className).toContain('border-border')
  })
})

describe('FormField', () => {
  it('renders label text', () => {
    render(<FormField label="Titre" htmlFor="title"><input id="title" /></FormField>)
    expect(screen.getByText('Titre')).toBeTruthy()
  })

  it('renders required asterisk when required is true', () => {
    render(<FormField label="Email" required><input /></FormField>)
    expect(screen.getByText('*')).toBeTruthy()
  })

  it('does not render asterisk when required is false', () => {
    render(<FormField label="Email"><input /></FormField>)
    expect(screen.queryByText('*')).toBeNull()
  })

  it('renders error message when error is provided', () => {
    render(<FormField label="Champ" error="Valeur invalide"><input /></FormField>)
    expect(screen.getByText('Valeur invalide')).toBeTruthy()
  })

  it('does not render error message when no error', () => {
    render(<FormField label="Champ"><input /></FormField>)
    expect(screen.queryByRole('paragraph')).toBeNull()
  })

  it('renders children', () => {
    render(<FormField label="Champ"><input placeholder="Test" /></FormField>)
    expect(screen.getByPlaceholderText('Test')).toBeTruthy()
  })

  it('associates label with input via htmlFor', () => {
    render(<FormField label="Titre" htmlFor="title-input"><input id="title-input" /></FormField>)
    const label = screen.getByText('Titre').closest('label')
    expect(label?.htmlFor).toBe('title-input')
  })
})
