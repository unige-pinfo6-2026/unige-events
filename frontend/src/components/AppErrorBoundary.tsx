import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Blobs } from '@/components/utils/Blobs'
import { ButtonPrimary, ButtonSecondary, ButtonsWrapper } from '@/components/utils/Buttons'

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
        <div className="py-20 lg:py-32 relative overflow-hidden min-h-screen">
          <Blobs />
          <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center space-y-10">
            <h1 className="text-[clamp(80px,16vw,140px)] font-black leading-none tracking-tighter bg-linear-to-br from-accent via-pink-500 to-purple-500 bg-clip-text text-transparent">
              Oops
            </h1>
            <div className="flex flex-col gap-2">
              <p className="text-2xl font-bold text-foreground">Une erreur inattendue est survenue</p>
              <p className="text-foreground/60 font-light leading-relaxed">
                Quelque chose s'est mal passé. Rechargez la page ou revenez à l'accueil.
              </p>
            </div>
            <ButtonsWrapper>
              <ButtonPrimary size="md" onClick={() => window.location.reload()}>
                Recharger la page
              </ButtonPrimary>
              <a href="/">
                <ButtonSecondary size="md">
                  Retour à l'accueil
                </ButtonSecondary>
              </a>
            </ButtonsWrapper>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
