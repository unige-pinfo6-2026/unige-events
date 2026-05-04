import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, Library } from 'lucide-react'
import * as Collapsible from '@radix-ui/react-collapsible'
import { Skeleton } from 'boneyard-js/react'
import { useAuth } from '@/hooks/useAuth'
import { useTheme } from '@/contexts/ThemeContext'
import { useMyDrafts } from '@/hooks/useMyDrafts'
import { computeStripLayout } from '@/utils/draftsResumeStripLayout'
import DraftResumeCard from './DraftResumeCard'

const PANEL_ID = 'drafts-resume-panel'

// Mirrors the closed-state Collapsible.Root + Trigger of the real banner so the
// boneyard wrapper measures exactly h-14 (56px). Margins (mt-6 mb-8) live on the
// <Skeleton> wrapper itself so the BFC introduced by the flex parent does not
// prevent margin collapse and inflate the measured height.
function DraftsHeaderFixture() {
  return (
    <div className="h-14 w-full rounded-2xl border border-border/60 flex items-center justify-between gap-4 px-4">
      <div className="flex items-center gap-2">
        <div className="size-4" />
        <div className="h-4 w-[110px]" />
      </div>
      <div className="size-4" />
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

  const displayedDrafts = useMemo(() => {
    const { displayCount } = computeStripLayout(availableWidth, drafts.length)
    return drafts.slice(0, displayCount)
  }, [availableWidth, drafts])

  if (loading) {
    const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
    return (
      <Skeleton
        name="drafts-resume-strip"
        loading={true}
        animate="pulse"
        color={skeletonColor}
        className="mt-6 mb-8 block"
      >
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
            {/* "Voir tout" button will be re-enabled once /my-events exists (SCRUM-93).
                The rail's overflow-x-auto still lets the user reach overflow cards by
                scrolling horizontally, so hiding the button is a graceful fallback. */}
          </div>
        )}
      </Collapsible.Content>
    </Collapsible.Root>
  )
}
