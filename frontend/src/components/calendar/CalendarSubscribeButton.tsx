import { useEffect, useState } from 'react'
import { Calendar, Download, ExternalLink, RefreshCw } from 'lucide-react'
import { getCalendarToken, regenerateCalendarToken } from '@/services/userService'
import type { CalendarTokenResponse } from '@/types/calendarToken'

export default function CalendarSubscribeButton() {
  const [token, setToken] = useState<CalendarTokenResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [regenerating, setRegenerating] = useState(false)
  const [regenerated, setRegenerated] = useState(false)

  useEffect(() => {
    getCalendarToken()
      .then(setToken)
      .catch(() => setError('Impossible de charger le lien de calendrier.'))
      .finally(() => setLoading(false))
  }, [])

  function handleRegenerate() {
    setRegenerating(true)
    setRegenerated(false)
    regenerateCalendarToken()
      .then((data) => {
        setToken(data)
        setRegenerated(true)
      })
      .catch(() => setError('Impossible de régénérer le lien.'))
      .finally(() => setRegenerating(false))
  }

  return (
    <div className="bg-linear-to-br from-accent/5 to-background/40 backdrop-blur-xl rounded-3xl border border-accent/30 p-6 flex flex-col gap-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Calendar className="w-4 h-4 text-accent" />
        S&apos;abonner au calendrier
      </div>

      <p className="text-sm text-foreground/50 leading-relaxed">
        Abonnez-vous à votre calendrier personnel UNIGE Events. Il se met à jour automatiquement avec tous les événements que vous avez mis en favoris ou auxquels vous participez.
      </p>

      {loading && (
        <p className="text-sm text-foreground/50">Chargement…</p>
      )}

      {error && (
        <p className="text-sm text-error">{error}</p>
      )}

      {!loading && !error && token && (
        <>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <a
                href={token.webcalUrl}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold no-underline hover:border-foreground/30 hover:text-foreground transition-colors self-start"
              >
                <Calendar className="w-4 h-4" />
                S&apos;abonner (Apple / Outlook)
              </a>
              <p className="text-xs text-foreground/40 pl-1">Ouvre directement dans Apple Calendar ou Outlook</p>
            </div>

            <div className="flex flex-col gap-1.5">
              <a
                href={token.httpsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold no-underline hover:border-accent/50 hover:text-accent transition-colors self-start"
              >
                <ExternalLink className="w-4 h-4" />
                S&apos;abonner (Google Calendar)
              </a>
              <p className="text-xs text-foreground/40 pl-1">Abonnement live — votre calendrier Google se met à jour automatiquement</p>
            </div>

            <div className="flex flex-col gap-1.5">
              <a
                href={token.httpsUrl}
                download
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold no-underline hover:border-foreground/30 hover:text-foreground transition-colors self-start"
              >
                <Download className="w-4 h-4" />
                Télécharger le flux .ics
              </a>
              <p className="text-xs text-foreground/40 pl-1">Pour les autres applications calendrier (import manuel)</p>
            </div>
          </div>

          <div className="border-t border-border pt-4 flex flex-col gap-3">
            <div className="flex flex-col gap-2">
              <button
                type="button"
                onClick={handleRegenerate}
                disabled={regenerating}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-border text-foreground/70 text-sm font-semibold bg-transparent hover:border-error/50 hover:text-error transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed self-start"
              >
                <RefreshCw className={`w-4 h-4 ${regenerating ? 'animate-spin' : ''}`} />
                {regenerating ? 'Régénération…' : 'Révoquer et régénérer le lien'}
              </button>
              <p className="text-xs text-foreground/40 pl-1">
                ⚠️ Régénérer le lien invalide votre abonnement actuel. Vous devrez vous réabonner dans votre application calendrier.
              </p>
            </div>

            {regenerated && (
              <p className="text-sm text-foreground/60">
                Lien régénéré — pensez à mettre à jour votre abonnement dans votre application calendrier
              </p>
            )}
          </div>
        </>
      )}
    </div>
  )
}
