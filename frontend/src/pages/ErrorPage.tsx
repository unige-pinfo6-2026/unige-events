import { Blobs } from '@/components/utils/Blobs'
import { ButtonPrimary, ButtonSecondary, ButtonsWrapper } from '@/components/utils/Buttons'
import { Link } from 'react-router-dom'

interface ErrorPageProps {
  statusCode?: number
  onRetry?: () => void
}

export default function ErrorPage({ statusCode = 500, onRetry }: Readonly<ErrorPageProps>) {
  return (
    <div className="py-20 lg:py-32 relative overflow-hidden">
      <Blobs />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center space-y-10">
        <h1 className="text-[clamp(100px,20vw,160px)] font-black leading-none tracking-tighter bg-linear-to-br from-accent via-pink-500 to-purple-500 bg-clip-text text-transparent">
          {statusCode}
        </h1>

        <div className="flex flex-col gap-2">
          <p className="text-2xl font-bold text-foreground">Ce n'est pas vous, c'est nous.</p>
          <p className="text-foreground/60 font-light leading-relaxed max-w-xl mx-auto">
            Une erreur inattendue est survenue de notre côté.
            <br/>
            Veuillez nous excuser pour le désagrément.
          </p>
        </div>

        <ButtonsWrapper>
          {onRetry && (
            <ButtonPrimary size="md" onClick={onRetry}>
              Réessayer
            </ButtonPrimary>
          )}
          <Link to="/">
            <ButtonSecondary size="md">Retour à l'accueil</ButtonSecondary>
          </Link>
        </ButtonsWrapper>
      </div>
    </div>
  )
}
