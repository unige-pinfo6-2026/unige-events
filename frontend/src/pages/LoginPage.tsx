import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import Logo from '../components/Logo'
import './LoginPage.css'

const EVENTS = [
  'Soirée étudiante au Uni-Mail',
  'Match de basket Sciences vs GSEM ',
  'Conférence sur le changement climatique',
  'Atelier 3D printing — Faclab',
  'Concert de jazz, Parc de Bastions',
  'Hackathon 48h, Battelle',
  'Débat philo ouvert à tous',
  'Course autour du lac Léman',
  'Projection ciné en plein air',
  'Tournoi de ping-pong inter-facultés',
  
]

function Typewriter() {
  const [displayed, setDisplayed] = useState('')
  const [idx, setIdx] = useState(0)
  const [deleting, setDeleting] = useState(false)
  const [cursor, setCursor] = useState(true)

  useEffect(() => {
    const t = setInterval(() => setCursor(c => !c), 540)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    const current = EVENTS[idx]
    if (!deleting && displayed === current) {
      const t = setTimeout(() => setDeleting(true), 1400)
      return () => clearTimeout(t)
    }
    if (deleting && displayed === '') {
      setDeleting(false)
      setIdx(i => (i + 1) % EVENTS.length)
      return
    }
    const t = setTimeout(() => {
      setDisplayed(deleting
        ? current.slice(0, displayed.length - 1)
        : current.slice(0, displayed.length + 1)
      )
    }, deleting ? 35 : 65)
    return () => clearTimeout(t)
  }, [displayed, deleting, idx])

  return (
    <span>
      {displayed}
      <span style={{ opacity: cursor ? 1 : 0, marginLeft: 1 }}>|</span>
    </span>
  )
}

function LoginPage() {
  const { isAuthenticated, isLoading, login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const errorMessage = searchParams.get('error')
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate('/home', { replace: true })
    }
  }, [isAuthenticated, isLoading, navigate])

  return (
    <div className="login-page">
      {/* Ambient typewriter: top-left quadrant */}
      <div className="login-typewriter">
        <svg width="26" height="26" viewBox="0 0 48 48" fill="none" className="login-typewriter-icon">
          <rect x="4" y="10" width="40" height="34" rx="4" stroke="white" strokeWidth="3.5" fill="none" />
          <rect x="4" y="10" width="40" height="14" rx="4" fill="white" />
          <line x1="15" y1="5" x2="15" y2="14" stroke="white" strokeWidth="3.5" strokeLinecap="round" />
          <line x1="33" y1="5" x2="33" y2="14" stroke="white" strokeWidth="3.5" strokeLinecap="round" />
          <circle cx="16" cy="33" r="2.5" fill="white" opacity="0.75" />
          <circle cx="24" cy="33" r="2.5" fill="white" opacity="0.75" />
          <circle cx="32" cy="33" r="2.5" fill="white" opacity="0.75" />
        </svg>
        <span className="login-typewriter-text">
          <Typewriter />
        </span>
      </div>

      {/* Brand: centered */}
      <div className="login-container">
        <div className="login-brand">
          <Logo size={116} className="login-logo-svg" />
          <h1 className="login-title">UNIGE Events</h1>
          <div className="login-categories">
            <span>Sports</span>
            <span>Arts</span>
            <span>Académique</span>
            <span>Social</span>
          </div>
          <div className="login-tagline">
            Découvre · Rejoins · Organise
          </div>
        </div>
      </div>

      {/* Card: opens on hover */}
      <details
        className={`login-card${open ? ' login-card--open' : ''}`}
        open={open}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onToggle={(e) => setOpen((e.currentTarget as HTMLDetailsElement).open)}
      >
        <summary
          className="login-card-peek"
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
            <path d="M12 19V5M5 12l7-7 7 7" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          Se connecter
        </summary>
        <div className="login-card-content">
          {errorMessage && (
            <div className="login-error" role="alert">
              {errorMessage === 'auth_failed'
                ? "Échec de l'authentification. Veuillez réessayer."
                : errorMessage}
            </div>
          )}
          <div className="login-card-title">Bon retour</div>
          <div className="login-card-sub">Connectez-vous avec votre compte UNIGE</div>
          <button className="login-button" onClick={login} disabled={isLoading}>
            Se connecter
          </button>
        </div>
      </details>
    </div>
  )
}

export default LoginPage
