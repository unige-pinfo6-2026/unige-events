import type React from "react"

interface ButtonsWrapperProps {
  children: React.ReactNode
}

interface ButtonProps {
  size?: keyof typeof sizes
  children: React.ReactNode
  onClick?: () => void
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
}

interface IconButtonProps {
  label: string
  onClick: () => void
  children: React.ReactNode
}

const sizes = {
  'sm': 'px-4 py-2 text-sm',
  'md': 'px-6 py-3 text-md',
  'lg': 'px-8 py-4 text-lg'
}

export function ButtonsWrapper({ children } : Readonly<ButtonsWrapperProps>) {
  return (
    <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
      {children}
    </div>
  )
}

export function ButtonPrimary({ size = 'md', children, onClick, type = 'button', disabled }: Readonly<ButtonProps>) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-2 font-semibold rounded-xl bg-linear-to-r from-accent to-pink-600 hover:from-accent/90 hover:to-pink-600/90 text-white shadow-xl shadow-accent/30 transition-all cursor-pointer border-0 disabled:opacity-50 disabled:cursor-not-allowed ${sizes[size]}`}
    >
      {children}
    </button>
  )
}

export function ButtonSecondary({ size = 'md', children, onClick, type = 'button', disabled }: Readonly<ButtonProps>) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-2 font-semibold rounded-xl border-2 border-border hover:border-accent/50 hover:bg-background/5 text-foreground transition-all cursor-pointer bg-transparent disabled:opacity-50 disabled:cursor-not-allowed ${sizes[size]}`}
    >
      {children}
    </button>
  )
}

export function IconButton({ label, onClick, children }: Readonly<IconButtonProps>) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
    >
      {children}
    </button>
  )
}
