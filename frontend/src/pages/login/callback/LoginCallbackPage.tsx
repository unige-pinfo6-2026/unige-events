import { InfoMessage } from '@/components/utils/InfoMessage'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { useAuth0 } from '@auth0/auth0-react'

// Auth0Provider processes the code from the URL on mount here.
// onRedirectCallback in AuthProvider.tsx then navigates to / automatically.
export default function CallbackPage() {
  const { error } = useAuth0()

  if(error) return <InfoMessage type='error' message={`Erreur d'authentification ${error}`}/>

  return (
    <LoadingSpinner>
      Connexion en cours...
    </LoadingSpinner>
  )
}