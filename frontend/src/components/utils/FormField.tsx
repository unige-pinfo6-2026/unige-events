function inputClass(error?: string) {
  return [
    'w-full px-4 py-3 rounded-xl border bg-background text-foreground text-sm',
    'placeholder:text-foreground/30 transition-all',
    'focus:outline-none focus:ring-2 focus:ring-accent/20',
    error ? 'border-error/70 focus:border-error' : 'border-border focus:border-accent/50',
  ].join(' ')
}

export function Input({ error, className, ...props }: React.ComponentProps<'input'> & { error?: string }) {
  return <input className={[inputClass(error), className].filter(Boolean).join(' ')} {...props} />
}

export function Select({ error, className, children, ...props }: React.ComponentProps<'select'> & { error?: string }) {
  return <select className={[inputClass(error), className].filter(Boolean).join(' ')} {...props}>{children}</select>
}

// SCRUM-147 — Textarea forwards its ref so inline tools (e.g. mention
// autocomplete) can read selectionStart / set focus without resorting to
// a sibling raw <textarea>. React 19 accepts `ref` directly in the props
// signature, so no forwardRef wrapper is needed.
export function Textarea({
  ref,
  error,
  className,
  ...props
}: React.ComponentProps<'textarea'> & { error?: string }) {
  return (
    <textarea
      ref={ref}
      className={[inputClass(error), className].filter(Boolean).join(' ')}
      {...props}
    />
  )
}

export default function FormField({ label, htmlFor, required, error, children, className }: Readonly<{
  label: string
  htmlFor?: string
  required?: boolean
  error?: string
  children: React.ReactNode
  className?: string
}>) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="block text-sm font-semibold text-foreground/60 mb-2">
        {label}{required && <span className="text-red-400 ml-0.5"> *</span>}
      </label>
      {children}
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
