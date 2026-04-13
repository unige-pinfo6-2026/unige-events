import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, ChevronDown, Library } from 'lucide-react'
import * as Collapsible from '@radix-ui/react-collapsible'
import { Skeleton } from 'boneyard-js/react'
import { useAuth } from '@/hooks/useAuth'
import { useTheme } from '@/contexts/ThemeContext'
import { useMyDrafts } from '@/hooks/useMyDrafts'
import { computeStripLayout } from '@/utils/draftsResumeStripLayout'
import DraftResumeCard from './DraftResumeCard'

const PANEL_ID = 'drafts-resume-panel'

function DraftsHeaderFixture() {
  return (
    <div className="mt-6 mb-8 h-14 rounded-2xl flex items-center justify-between gap-4 px-4">
      <div className="flex items-center gap-2">
        <div className="size-4 rounded-full" />
        <div className="h-4 w-32 rounded" />
      </div>
      <div className="size-4 rounded-full" />
    </div>
  )
}

function handleArrowNav(event: KeyboardEvent<HTMLDivElement>) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  const active = document.activeElement as HTMLElement | null
  if (!active || !event.currentTarget.contains(active)) return
  const sibling = event.key === 'ArrowRight' ? active.nextElementSibling : active.previousElementSibling
  if (sibling instanceof HTMLElement) {
    event.preventDefault()
    sibling.focus()
  }
}

export default function DraftsResumeStrip() {
  const { user } = useAuth()
  const { drafts, loading, error } = useMyDrafts(user?.id)
  const { theme } = useTheme()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)
  const [availableWidth, setAvailableWidth] = useState<number>(0)
  useLayoutEffect(() => {
    if (!open) return
    const el = panelRef.current
    if (!el) return

    const measure = () => setAvailableWidth(el.getBoundingClientRect().width)
    measure()

    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', measure)
      return () => window.removeEventListener('resize', measure)
    }

    const observer = new ResizeObserver(() => measure())
    observer.observe(el)
    return () => observer.disconnect()
  }, [open, drafts.length])

  const { displayedDrafts, showViewAll } = useMemo(() => {
    const { displayCount, showViewAll: svw } = computeStripLayout(availableWidth, drafts.length)
    return { displayedDrafts: drafts.slice(0, displayCount), showViewAll: svw }
  }, [availableWidth, drafts])

  if (loading) {
    const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
    return (
      <Skeleton name="drafts-resume-strip" loading={true} animate="pulse" color={skeletonColor}>
        <DraftsHeaderFixture />
      </Skeleton>
    )
  }

  if (error) return null

  const isEmpty = drafts.length === 0

  return (
    <Collapsible.Root
      open={open}
      onOpenChange={setOpen}
      className="mt-6 mb-8 rounded-2xl border border-border/60 bg-background/60 backdrop-blur-xl motion-safe:animate-fade-in"
    >
      <Collapsible.Trigger
        aria-controls={PANEL_ID}
        className="group flex h-14 w-full items-center justify-between gap-4 rounded-2xl px-4 text-left text-sm text-foreground/70 font-medium transition-colors hover:bg-background/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        <span className="flex items-center gap-2">
          <Library className="size-4" aria-hidden />
          <span>Mes brouillons</span>
        </span>
        <ChevronDown
          className="size-4 text-foreground/50 transition-transform duration-200 group-data-[state=open]:rotate-180 motion-reduce:transition-none"
          aria-hidden
        />
      </Collapsible.Trigger>
      <Collapsible.Content
        id={PANEL_ID}
        role="region"
        aria-label="Liste de mes brouillons"
        className="overflow-hidden motion-safe:data-[state=open]:animate-drafts-panel-open motion-safe:data-[state=closed]:animate-drafts-panel-close"
      >
        {isEmpty ? (
          <div className="flex items-center justify-center border-t border-border/40 px-4 py-4 text-sm text-foreground/50 italic">
            Aucun brouillon
          </div>
        ) : (
          <div
            ref={panelRef}
            className="flex items-center gap-4 border-t border-border/40 px-4 py-3"
          >
            <div className="hidden sm:flex items-center gap-2 shrink-0 text-xs text-foreground/60 w-[180px]">
              <span>Reprendre un brouillon</span>
            </div>
            <div
              role="toolbar"
              aria-label="Liste des brouillons"
              className="flex-1 flex items-center gap-3 overflow-x-auto snap-x snap-mandatory motion-safe:scroll-smooth overscroll-x-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
              onKeyDown={handleArrowNav}
            >
              {displayedDrafts.map(draft => (
                <DraftResumeCard
                  key={draft.id}
                  draft={draft}
                  onOpen={id => navigate(`/events/${id}/edit`)}
                />
              ))}
            </div>
            {showViewAll && (
              <button
                type="button"
                onClick={() => navigate('/my-events')}
                aria-label="Voir tous mes brouillons"
                className="shrink-0 flex items-center gap-1 rounded-xl border border-border/50 bg-background/40 hover:bg-background/80 px-3 h-10 text-xs text-foreground/70 transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background"
              >
                <span>Voir tout</span>
                <ArrowRight className="size-4" aria-hidden />
              </button>
            )}
          </div>
        )}
      </Collapsible.Content>
    </Collapsible.Root>
  )
}
