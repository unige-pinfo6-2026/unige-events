export function Skeleton({ className = '' }: Readonly<{ className?: string }>) {
  return <div className={`rounded-xl bg-foreground/10 animate-pulse ${className}`} />
}
