import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Search, SearchIcon } from 'lucide-react'
import { Skeleton } from 'boneyard-js/react'
import EventCard from '@/components/event/EventCard'
import EventSearchSidebar from '@/components/event/EventSearchSidebar'
import UserResultCard from '@/components/user/UserResultCard'
import { useSearch } from '@/hooks/useEventSearch'
import { useUserSearch } from '@/hooks/useUserSearch'
import { useAuth } from '@/hooks'
import { useTheme } from '@/contexts/ThemeContext'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionHeader, SectionWrapper } from '@/components/utils/Section'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { ButtonPrimary } from '@/components/utils/Buttons'

type SearchTab = 'events' | 'users'

function SearchResultsFixture() {
  return (
    <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <article key={i} className="relative bg-background border border-border rounded-3xl overflow-hidden">
          <div className="relative h-52 bg-foreground/10">
            <span className="absolute top-4 left-4 h-6 w-24 rounded-full bg-foreground/20" />
            <div className="absolute bottom-0 left-0 right-0 px-5 pb-5 flex flex-col gap-2">
              <div className="h-6 w-4/5 rounded-md bg-foreground/25" />
              <div className="h-4 w-1/2 rounded-md bg-foreground/20" />
            </div>
          </div>
          <div className="p-5 flex flex-col gap-3">
            <div className="flex flex-col gap-2.5">
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-40 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-32 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-24 rounded bg-foreground/10" />
              </div>
            </div>
            <div className="border-t border-border" />
            <div className="flex flex-col gap-1.5">
              <div className="h-3.5 w-full rounded bg-foreground/10" />
              <div className="h-3.5 w-5/6 rounded bg-foreground/10" />
            </div>
          </div>
        </article>
      ))}
    </div>
  )
}

function UserResultsFixture() {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 p-3 rounded-2xl border border-border">
          <div className="size-12 rounded-full bg-foreground/15 shrink-0" />
          <div className="flex-1 flex flex-col gap-2">
            <div className="h-4 w-40 rounded bg-foreground/15" />
            <div className="h-3 w-24 rounded bg-foreground/10" />
          </div>
        </div>
      ))}
    </div>
  )
}

// User search tab (bug ⑦) — backed by `GET /api/users/search` (@Authenticated).
function UserSearchTab({ skeletonColor }: Readonly<{ skeletonColor: string }>) {
  const { isAuthenticated, login } = useAuth()
  const { query, setQuery, results, loading, error, searched } = useUserSearch(isAuthenticated)

  if (!isAuthenticated) {
    return (
      <div className="mt-8 py-12 flex flex-col items-center gap-4 text-center">
        <p className="text-foreground/60 text-base">
          Connecte-toi pour rechercher des utilisateurs.
        </p>
        <ButtonPrimary onClick={() => login()}>Se connecter</ButtonPrimary>
      </div>
    )
  }

  return (
    <div className="mt-8 flex flex-col gap-6">
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Rechercher un utilisateur…"
        aria-label="Rechercher un utilisateur"
        className="w-full px-5 py-4 rounded-2xl border border-border bg-background/80 backdrop-blur-sm text-foreground placeholder:text-foreground/40 text-base outline-none focus:border-accent/60 transition-colors"
      />

      <div>
        {loading && (
          <Skeleton name="user-search-results" loading={true} animate="pulse" color={skeletonColor}>
            <UserResultsFixture />
          </Skeleton>
        )}
        {!loading && error && <InfoMessage type="error" message={error} />}
        {!loading && !error && searched && results.length === 0 && (
          <InfoMessage icon={SearchIcon} type="info" message="Aucun utilisateur trouvé." />
        )}
        {!loading && !error && results.length > 0 && (
          <div className="flex flex-col gap-3">
            {results.map((user) => (
              <UserResultCard key={user.id} user={user} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

const SEARCH_TABS: ReadonlyArray<{ key: SearchTab; label: string }> = [
  { key: 'events', label: 'Événements' },
  { key: 'users', label: 'Utilisateurs' },
]

function SearchPage() {
  const { query, setQuery, filters, setFilters, results, suggestions, loading, error, resetFilters, selectSuggestion, searchNow } =
    useSearch()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'

  const [searchParams, setSearchParams] = useSearchParams()
  const tab: SearchTab = searchParams.get('tab') === 'users' ? 'users' : 'events'
  const setTab = useCallback((next: SearchTab) => {
    setSearchParams(prev => {
      const params = new URLSearchParams(prev)
      if (next === 'events') params.delete('tab')
      else params.set('tab', next)
      return params
    }, { replace: true })
  }, [setSearchParams])

  const [showSuggestions, setShowSuggestions] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const suggestionsRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setShowSuggestions(suggestions.length > 0)
  }, [suggestions])

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setQuery(e.target.value)
    },
    [setQuery],
  )

  const handleSuggestionClick = useCallback(
    (text: string) => {
      selectSuggestion(text)
      setShowSuggestions(false)
    },
    [selectSuggestion],
  )

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setShowSuggestions(false)
    }
  }, [])

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        inputRef.current &&
        !inputRef.current.contains(e.target as Node) &&
        suggestionsRef.current &&
        !suggestionsRef.current.contains(e.target as Node)
      ) {
        setShowSuggestions(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const resultCount = results.length

  return (
    <div>
      <SectionWrapper 
        padding="sm" 
        background={<BlobsSubtle />}
      >
        <SectionHeader
          align='left'
          title={tab === 'users'
            ? <>Trouver <mark>un utilisateur</mark></>
            : <>Trouver <mark>un événement</mark></>}
          subtitle={tab === 'users'
            ? "Recherchez les membres de la communauté par nom ou nom d'utilisateur."
            : "Explorez les événements du campus par mots-clés, catégorie ou date."}
        />

        {/* Tab switcher — Événements | Utilisateurs */}
        <div className="mt-8 flex gap-2">
          {SEARCH_TABS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              aria-pressed={tab === key}
              className={`h-9 px-4 rounded-xl text-sm font-medium transition-colors cursor-pointer border-0 ${
                tab === key
                  ? 'bg-accent text-white'
                  : 'bg-foreground/5 text-foreground/60 hover:text-foreground hover:bg-foreground/10'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === 'users' && <UserSearchTab skeletonColor={skeletonColor} />}

        {/* Search bar + Search content */}
        {tab === 'events' && (
        <div className="flex flex-col gap-8">
          {/* Search bar */}
          <div className="mt-8 relative flex gap-3">
            <div className="relative flex-1">
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                onFocus={() => { if (suggestions.length > 0) setShowSuggestions(true) }}
                placeholder="Rechercher un événement…"
                aria-label="Rechercher"
                className="w-full px-5 py-4 rounded-2xl border border-border bg-background/80 backdrop-blur-sm text-foreground placeholder:text-foreground/40 text-base outline-none focus:border-accent/60 transition-colors"
              />

              {showSuggestions && (
                <div
                  ref={suggestionsRef}
                  role="listbox"
                  className="absolute top-full left-0 right-0 mt-2 z-20 bg-background/95 backdrop-blur-xl border border-border rounded-2xl overflow-hidden shadow-2xl shadow-black/20"
                >
                  {suggestions.slice(0, 5).map((s) => (
                    <button
                      key={s}
                      type="button"
                      role="option"
                      aria-selected={false}
                      onClick={() => handleSuggestionClick(s)}
                      className="flex w-full items-center gap-3 px-5 py-3 text-left text-sm text-foreground/80 hover:bg-foreground/5 border-b border-border/50 last:border-0 transition-colors cursor-pointer"
                    >
                      <Search className="w-3.5 h-3.5 text-foreground/30 shrink-0" />
                      {s}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              type="button"
              aria-label="Lancer la recherche"
              onClick={searchNow}
              className="inline-flex items-center justify-center px-6 py-4 rounded-2xl bg-linear-to-r from-accent to-pink-600 hover:from-accent/90 hover:to-pink-600/90 text-white shadow-xl shadow-accent/30 transition-all shrink-0 cursor-pointer"
            >
              <Search className="w-5 h-5" />
            </button>
          </div>

          {/* Search content */}
          <div className="flex flex-col lg:flex-row gap-6 items-start">
            {/* Sidebar */}
            <div className="w-full lg:w-60 shrink-0">
              <EventSearchSidebar filters={filters} setFilters={setFilters} resetFilters={resetFilters} />
            </div>

            {/* Results */}
            <div className="flex-1 min-w-0">
              <p className="text-sm text-foreground/50 font-medium mb-4">
                {resultCount} événement{resultCount > 1 ? 's' : ''} trouvé{resultCount > 1 ? 's' : ''}
              </p>

              {loading && (
                <Skeleton
                  name="search-results"
                  loading={true}
                  animate="pulse"
                  color={skeletonColor}
                ><SearchResultsFixture /></Skeleton>
              )}

              {!loading && error && (
                <InfoMessage type='error' message={error}/>
              )}

              {!loading && !error && resultCount === 0 && (
                <InfoMessage icon={SearchIcon} type='info' message="Aucun résultat. Essayez de modifier vos filtres ou votre recherche"/>
              )}

              {!loading && !error && resultCount > 0 && (
                <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-4">
                  {results.map((event) => (
                    <EventCard key={event.id} event={event} />
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
        )}
      </SectionWrapper>
    </div>
  )
}

export default SearchPage
