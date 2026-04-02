import type { LucideIcon } from "lucide-react";

export const TextLink = ({ href, decorate = false, children }: { href: string, decorate?: boolean, children: React.ReactNode }) => (
    <a href={href} className="text-sm text-white/50 hover:text-white transition-colors inline-flex items-center gap-2 group">
        {decorate && <span className="w-0 h-0.5 bg-linear-to-r from-accent to-pink-600 group-hover:w-4 transition-all duration-300" />}
        {children}
    </a>
)

export const IconLink = ({ href, icon: Icon }: { href: string, icon: React.FC<{ className?: string, size?: number }> }) => (
  <a href={href} className="w-10 h-10 rounded-lg bg-white/5 hover:bg-accent/10 border border-white/10 flex items-center justify-center transition-colors">
    <Icon className="w-5 h-5 text-white/60 hover:text-white"/>
  </a>
)

export const ContactLink = ({ href, icon: Icon, children }: { href: string, icon: LucideIcon, children: React.ReactNode }) => (
    <a href={href} className="text-sm text-white/50 hover:text-white transition-colors flex items-center gap-3 group">
        <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center group-hover:bg-accent/10 transition-colors">
            <Icon className="w-4 h-4" />
        </div>

        {children}
    </a>
)
