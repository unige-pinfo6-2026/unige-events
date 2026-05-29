import { useState, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'

export interface ProfileTab {
  /** Clé stable de l'onglet (utilisée pour le state + les `id`). */
  key: string
  label: string
  icon: LucideIcon
  /** Contenu rendu quand l'onglet est actif. */
  content: ReactNode
}

interface ProfileTabsProps {
  tabs: ProfileTab[]
}

/**
 * Sélecteur de sections type Instagram pour le bas d'une page profil
 * (SCRUM-141 redesign) : une barre d'onglets (icône + label) avec un seul
 * onglet actif à la fois, dont le contenu est rendu en dessous. Le `label`
 * de l'onglet actif fait office de titre — les sections passent donc
 * `hideHeading` pour éviter le doublon.
 */
export default function ProfileTabs({ tabs }: Readonly<ProfileTabsProps>) {
  const [activeKey, setActiveKey] = useState(tabs[0]?.key)
  const activeTab = tabs.find(t => t.key === activeKey) ?? tabs[0]

  if (!activeTab) return null

  return (
    <div>
      <div role="tablist" aria-label="Sections du profil" className="flex items-stretch border-t border-border">
        {tabs.map(tab => {
          const isActive = tab.key === activeTab.key
          const Icon = tab.icon
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              id={`profile-tab-${tab.key}`}
              aria-selected={isActive}
              aria-controls={`profile-tabpanel-${tab.key}`}
              onClick={() => setActiveKey(tab.key)}
              className={`-mt-px flex flex-1 items-center justify-center gap-2 px-4 lg:px-6 py-4 border-t-2 text-xs font-bold uppercase tracking-widest transition-colors cursor-pointer bg-transparent ${
                isActive
                  ? 'border-accent text-accent'
                  : 'border-transparent text-foreground/40 hover:text-foreground/70'
              }`}
            >
              <Icon className="size-4 shrink-0" aria-hidden="true" />
              <span className="hidden sm:inline">{tab.label}</span>
            </button>
          )
        })}
      </div>

      <div
        role="tabpanel"
        id={`profile-tabpanel-${activeTab.key}`}
        aria-labelledby={`profile-tab-${activeTab.key}`}
        className="mt-6"
      >
        {activeTab.content}
      </div>
    </div>
  )
}
