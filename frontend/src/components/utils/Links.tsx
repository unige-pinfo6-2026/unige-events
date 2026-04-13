import { Link } from 'react-router-dom'
import type { LucideIcon } from "lucide-react";

export function ActionLink({ to, icon: Icon, label }: Readonly<{ to: string; icon: LucideIcon; label: string }>) {
    return (
        <Link
            to={to}
            aria-label={label}
            className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground"
        >
            <Icon className="size-5" />
        </Link>
    )
}

export function TextLink({ href, decorate = false, children }: Readonly<{ href: string, decorate?: boolean, children: React.ReactNode }>) {
    return (
        <a href={href} className="text-sm text-overlay hover:text-foreground transition-colors inline-flex items-center gap-2 group">
            {decorate && <span className="w-0 h-0.5 bg-linear-to-r from-accent to-pink-600 group-hover:w-4 transition-all duration-300" />}
            {children}
        </a>
    )
}

export function IconLink({ href, icon: Icon }: Readonly<{ href: string, icon: React.FC<{ className?: string, size?: number }> }>) {
    return (
        <a href={href} className="w-10 h-10 rounded-lg bg-background/5 hover:bg-accent/10 border border-overlay flex items-center justify-center transition-colors">
            <Icon className="w-5 h-5 text-overlay hover:text-foreground"/>
        </a>
    )
}

export function ContactLink({ href, icon: Icon, children }: Readonly<{ href: string, icon: LucideIcon, children: React.ReactNode }>) {
    return (
        <a href={href} className="text-sm text-overlay hover:text-foreground transition-colors flex items-center gap-3 group">
            <div className="w-8 h-8 rounded-lg bg-foreground/5 flex items-center justify-center group-hover:bg-accent/10 transition-colors">
                <Icon className="w-4 h-4" />
            </div>

            {children}
        </a>
    )
}