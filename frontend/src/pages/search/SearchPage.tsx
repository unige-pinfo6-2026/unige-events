import { useCallback, useEffect, useRef, useState } from 'react'
import { Search, SearchIcon } from 'lucide-react'
import EventCard from '@/components/event/EventCard'
import FilterSidebar from '@/components/FilterSidebar'
import { useSearch } from '@/hooks/useSearch'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { SectionHeader, SectionWrapper } from '@/components/utils/Section'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { InfoMessage } from '@/components/utils/InfoMessage'

function SearchPage() {
  const { query, setQuery, filters, setFilters, results, suggestions, loading, error, resetFilters, selectSuggestion, searchNow } =
    useSearch()

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
          title={<>Trouver <mark>un événement</mark></>}
          subtitle={"Explorez les événements du campus par mots-clés, catégorie ou date."}
        />

        {/* Search bar + Search content */}
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
              <FilterSidebar filters={filters} setFilters={setFilters} resetFilters={resetFilters} />
            </div>

            {/* Results */}
            <div className="flex-1 min-w-0">
              <p className="text-sm text-foreground/50 font-medium mb-4">
                {resultCount} événement{resultCount > 1 ? 's' : ''} trouvé{resultCount > 1 ? 's' : ''}
              </p>

              {loading && <LoadingSpinner />}

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
      </SectionWrapper>
    </div>
  )
}

export default SearchPage
