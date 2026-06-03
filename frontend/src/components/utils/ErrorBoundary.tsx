import { Component, type ErrorInfo, type ReactNode } from 'react'
import ErrorPage from '@/pages/ErrorPage'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught an unexpected error:', error, info)

    // Check if the error is due to a failed dynamic import (e.g. chunk loading error after deployment)
    const isChunkLoadError =
      error.message &&
      (error.message.includes('dynamically imported module') ||
       error.message.includes('Failed to fetch dynamically imported module') ||
       error.message.includes('error loading dynamically imported module'))

    if (isChunkLoadError) {
      try {
        const lastReload = sessionStorage.getItem('last_chunk_reload')
        const now = Date.now()

        // If we reloaded less than 10 seconds ago, don't reload again to prevent loops
        if (!lastReload || now - parseInt(lastReload, 10) > 10000) {
          sessionStorage.setItem('last_chunk_reload', now.toString())
          console.warn('Chunk load error detected, triggering page reload...')
          window.location.reload()
        } else {
          console.error('Chunk load error loop prevented.')
        }
      } catch (e) {
        console.error('Failed to handle chunk load error:', e)
      }
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col justify-center">
          <ErrorPage onRetry={() => window.location.reload()} />
        </div>
      )
    }

    return this.props.children
  }
}
