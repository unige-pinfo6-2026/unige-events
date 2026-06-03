import type React from "react"
import { Link } from "react-router-dom"

interface ButtonsWrapperProps {
  children: React.ReactNode
}

interface ButtonProps {
  size?: keyof typeof sizes
  children: React.ReactNode
  onClick?: () => void
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  /**
   * When set, the control renders a single styled react-router `<Link>` instead
   * of a `<button>`. Use this for navigation actions so callers don't nest a
   * `<button>` inside an `<a>` (invalid interactive markup, breaks keyboard/SR).
   */
  to?: string
}

interface IconButtonProps {
  label: string
  onClick: () => void
  children: React.ReactNode
}

const sizes = {
  'sm': 'px-4 py-2 text-sm',
  'md': 'px-6 py-3 text-md',
  'lg': 'px-8 py-4 text-lg',
} as const

const buttonBase =
  'inline-flex items-center justify-center gap-2 font-semibold rounded-xl transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-background'

const buttonVariants = {
  primary:
    'border-0 bg-linear-to-r from-accent to-pink-600 hover:from-accent/90 hover:to-pink-600/90 text-white shadow-xl shadow-accent/30 focus-visible:ring-accent',
  secondary:
    'border-2 border-border bg-transparent text-foreground hover:border-accent/50 hover:bg-background/5 focus-visible:ring-accent',
  neutral:
    'border border-border/50 bg-foreground/8 text-foreground hover:bg-foreground/15 focus-visible:ring-accent',
  destructive:
    'border border-error/40 bg-error/10 text-error hover:bg-error/20 focus-visible:ring-error',
} as const

type ButtonVariant = keyof typeof buttonVariants

function buttonClass(variant: ButtonVariant, size: keyof typeof sizes): string {
  return `${buttonBase} ${buttonVariants[variant]} ${sizes[size]}`
}

function Button({
  variant,
  size = 'md',
  children,
  onClick,
  type = 'button',
  disabled,
  to,
}: Readonly<ButtonProps & { variant: ButtonVariant }>) {
  const className = buttonClass(variant, size)
  if (to !== undefined) {
    return (
      <Link to={to} className={className}>
        {children}
      </Link>
    )
  }
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={className}
    >
      {children}
    </button>
  )
}

export function ButtonsWrapper({ children }: Readonly<ButtonsWrapperProps>) {
  return (
    <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
      {children}
    </div>
  )
}

export function ButtonPrimary(props: Readonly<ButtonProps>) {
  return <Button variant="primary" {...props} />
}

export function ButtonSecondary(props: Readonly<ButtonProps>) {
  return <Button variant="secondary" {...props} />
}

export function ButtonNeutral(props: Readonly<ButtonProps>) {
  return <Button variant="neutral" {...props} />
}

export function ButtonDestructive(props: Readonly<ButtonProps>) {
  return <Button variant="destructive" {...props} />
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
