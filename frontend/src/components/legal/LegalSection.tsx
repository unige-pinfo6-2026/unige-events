import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

interface LegalSectionProps {
  title: string
  children: ReactNode
}

export function LegalSection({ title, children }: Readonly<LegalSectionProps>) {
  return (
    <section className="space-y-4">
      <h2 className="text-xl font-semibold text-foreground">{title}</h2>
      {children}
    </section>
  )
}

export function LegalParagraph({ children }: Readonly<{ children: ReactNode }>) {
  return <p className="text-foreground/70 leading-relaxed">{children}</p>
}

export function LegalList({ children }: Readonly<{ children: ReactNode }>) {
  return <ul className="list-disc list-inside space-y-2 text-foreground/70 leading-relaxed">{children}</ul>
}

export function LegalSubheading({ children }: Readonly<{ children: ReactNode }>) {
  return <h3 className="font-medium text-foreground">{children}</h3>
}

export function LegalBackLink() {
  return (
    <div className="pt-4 text-center">
      <Link
        to="/"
        onClick={() => window.scrollTo({ top: 0 })}
        className="text-sm text-foreground/40 hover:text-foreground/60 transition-colors"
      >
        Retour à l'accueil
      </Link>
    </div>
  )
}
