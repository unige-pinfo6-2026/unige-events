import { useRef, type KeyboardEvent } from 'react'

interface TagInputProps {
  value: string[]
  onChange: (tags: string[]) => void
  placeholder?: string
  maxTags?: number
}

export default function TagInput({ value, onChange, placeholder = 'Add a tag...', maxTags }: Readonly<TagInputProps>) {
  const inputRef = useRef<HTMLInputElement>(null)

  function addTag(raw: string) {
    const tag = raw.trim()
    if (!tag || value.includes(tag)) return
    if (maxTags !== undefined && value.length >= maxTags) return
    onChange([...value, tag])
    if (inputRef.current) inputRef.current.value = ''
  }

  function removeTag(index: number) {
    onChange(value.filter((_, i) => i !== index))
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    const input = inputRef.current
    if (!input) return

    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addTag(input.value)
    } else if (e.key === 'Backspace' && input.value === '' && value.length > 0) {
      removeTag(value.length - 1)
    }
  }

  function handleBlur() {
    if (inputRef.current?.value) {
      addTag(inputRef.current.value)
    }
  }

  const atMax = maxTags !== undefined && value.length >= maxTags

  return (
    <div
      className="flex flex-wrap items-center gap-2 w-full px-3 py-2 rounded-xl border border-border bg-background focus-within:ring-2 focus-within:ring-accent/20 focus-within:border-accent/50 transition-all"
    >
      {value.map((tag, i) => (
        <span
          key={tag}
          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-foreground/10 text-foreground text-sm font-medium"
        >
          {tag}
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); removeTag(i) }}
            className="text-foreground/50 hover:text-foreground transition-colors leading-none cursor-pointer"
            aria-label={`Remove tag ${tag}`}
          >
            ×
          </button>
        </span>
      ))}
      {!atMax && (
        <input
          ref={inputRef}
          type="text"
          onKeyDown={handleKeyDown}
          onBlur={handleBlur}
          placeholder={value.length === 0 ? placeholder : ''}
          className="flex-1 min-w-24 bg-transparent text-sm text-foreground placeholder:text-foreground/30 focus:outline-none"
        />
      )}
    </div>
  )
}
