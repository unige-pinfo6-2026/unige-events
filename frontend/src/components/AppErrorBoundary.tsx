import { Component, type ErrorInfo, type ReactNode } from 'react'
import ErrorPage from '@/pages/ErrorPage'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

export default class AppErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('AppErrorBoundary caught an unexpected error:', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col justify-center">
          <ErrorPage
            onRetry={() => window.location.reload()}
            retryLabel="Recharger la page"
          />
        </div>
      )
    }

    return this.props.children
  }
}
