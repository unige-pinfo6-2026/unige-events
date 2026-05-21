import { type ReactNode, useState } from 'react'
import { EVENT_CATEGORIES, type Event } from '@/types/event'

interface EventBannerProps {
  event: Event
  className?: string
  children?: ReactNode
}

export default function EventBanner({ event, className = '', children }: Readonly<EventBannerProps>) {
  const [errorSrc, setErrorSrc] = useState<string | null>(null)
  const src = event.bannerUrl ?? null
  const showImage = Boolean(src) && src !== errorSrc

  const category = EVENT_CATEGORIES[event.category]

  return (
    <div
      className={`relative overflow-hidden ${className}`}
      style={{ background: `linear-gradient(135deg, ${category.color}55, ${category.color}cc)` }}
    >
      {showImage && (
        <img
          src={src!}
          alt=""
          aria-hidden="true"
          className="absolute inset-0 w-full h-full object-cover"
          onError={() => setErrorSrc(src)}
        />
      )}
      {children}
    </div>
  )
}
