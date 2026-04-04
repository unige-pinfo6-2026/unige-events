import { Blobs } from '@/components/utils/Blobs'
import { ButtonPrimary } from '@/components/utils/Buttons'
import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="py-20 lg:py-32 relative overflow-hidden">
      <Blobs />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center space-y-10">
        <h1 className="text-[clamp(100px,20vw,160px)] font-black leading-none tracking-tighter bg-linear-to-br from-accent via-pink-500 to-purple-500 bg-clip-text text-transparent">
          404
        </h1>

        <div className="flex flex-col gap-2">
          <p className="text-2xl font-bold text-foreground">Page introuvable</p>
          <p className="text-foreground/60 font-light leading-relaxed">
            Cette page n'existe pas ou a été déplacée.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Link to="/">
            <ButtonPrimary size="md">Retour à l'accueil</ButtonPrimary>
          </Link>
        </div>
      </div>
    </div>
  )
}