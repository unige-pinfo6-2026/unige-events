import { useAuth0 } from '@auth0/auth0-react'
import './CallbackPage.css'

// Auth0Provider processes the code from the URL on mount here.
// onRedirectCallback in main.tsx then navigates to /home automatically.
function CallbackPage() {
  const { error } = useAuth0()

  if (error) {
    return (
      <div className="callback-page">
        <div className="callback-spinner-wrapper">
          <p className="callback-message">Erreur d&apos;authentification : {error.message}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="callback-page">
      <div className="callback-spinner-wrapper">
        <div className="spinner" />
        <p className="callback-message">Connexion en cours…</p>
      </div>
    </div>
  )
}

export default CallbackPage
