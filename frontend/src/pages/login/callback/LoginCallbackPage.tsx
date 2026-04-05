import { useAuth0 } from '@auth0/auth0-react'

// Auth0Provider processes the code from the URL on mount here.
// onRedirectCallback in AuthProvider.tsx then navigates to / automatically.
export default function CallbackPage() {
  const { error } = useAuth0()

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        {error ? (
          <p className="text-error text-base">Erreur d'authentification : {error.message}</p>
        ) : (
          <>
            <div className="w-8 h-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />
            <p className="text-foreground/50 text-base">Connexion en cours…</p>
          </>
        )}
      </div>
    </div>
  )
}