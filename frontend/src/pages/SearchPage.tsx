import { useCallback, useEffect, useRef, useState } from 'react'
import EventCard from '../components/EventCard'
import FilterSidebar from '../components/FilterSidebar'
import { useSearch } from '../hooks/useSearch'

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
    <div
      style={{
        maxWidth: 1100,
        margin: '0 auto',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.5rem',
      }}
    >
      {/* Search bar */}
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <input
            ref={inputRef}
            type='text'
            value={query}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => {
              if (suggestions.length > 0) setShowSuggestions(true)
            }}
            placeholder='Rechercher un événement…'
            aria-label='Rechercher'
            style={{
              width: '100%',
              padding: '0.75rem 1rem',
              borderRadius: 12,
              border: '1px solid var(--input-border)',
              background: 'var(--input-bg)',
              color: 'var(--text-primary)',
              fontSize: '1rem',
              outline: 'none',
              boxSizing: 'border-box',
              transition: 'border-color 0.15s',
            }}
          />

          {showSuggestions && (
            <div
              ref={suggestionsRef}
              role='listbox'
              style={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                background: 'var(--card-bg)',
                border: '1px solid var(--card-border)',
                borderRadius: 12,
                marginTop: 4,
                zIndex: 10,
                overflow: 'hidden',
                boxShadow: '0 4px 16px rgba(0,0,0,0.1)',
              }}
            >
              {suggestions.slice(0, 5).map((s, i) => (
                <button
                  key={i}
                  type='button'
                  role='option'
                  aria-selected={false}
                  onClick={() => handleSuggestionClick(s)}
                  style={{
                    display: 'block',
                    width: '100%',
                    padding: '0.65rem 1rem',
                    textAlign: 'left',
                    background: 'none',
                    border: 'none',
                    borderBottom: '1px solid var(--card-border-subtle)',
                    cursor: 'pointer',
                    fontSize: '0.9375rem',
                    color: 'var(--text-primary)',
                  }}
                >
                  {s}
                </button>
              ))}
            </div>
          )}
        </div>

        <button
          type='button'
          aria-label='Lancer la recherche'
          onClick={searchNow}
          style={{
            padding: '0.75rem 1.25rem',
            background: '#CF0063',
            color: 'white',
            border: 'none',
            borderRadius: 12,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <svg
            width='20'
            height='20'
            viewBox='0 0 24 24'
            fill='none'
            stroke='currentColor'
            strokeWidth='2.5'
            strokeLinecap='round'
            strokeLinejoin='round'
            aria-hidden='true'
          >
            <circle cx='11' cy='11' r='8' />
            <line x1='21' y1='21' x2='16.65' y2='16.65' />
          </svg>
        </button>
      </div>

      {/* Layout: sidebar + results */}
      <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'flex-start' }}>
        {/* Sidebar */}
        <div style={{ width: 240, flexShrink: 0 }}>
          <FilterSidebar filters={filters} setFilters={setFilters} resetFilters={resetFilters} />
        </div>

        {/* Results */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {/* Result counter — always visible */}
          <div
            style={{
              fontSize: '0.875rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              fontWeight: 500,
            }}
          >
            {resultCount} événement{resultCount !== 1 ? 's' : ''} trouvé{resultCount !== 1 ? 's' : ''}
          </div>

          {/* Loading */}
          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem 0' }}>
              <div className='spinner' />
            </div>
          )}

          {/* Error */}
          {!loading && error && (
            <div
              style={{
                background: 'var(--card-bg)',
                border: '1px solid var(--card-border)',
                borderRadius: 12,
                padding: '2rem',
                textAlign: 'center',
                color: '#dc2626',
                fontSize: '0.9375rem',
              }}
            >
              {error}
            </div>
          )}

          {/* Empty state */}
          {!loading && !error && resultCount === 0 && (
            <div
              style={{
                background: 'var(--card-bg)',
                border: '1px solid var(--card-border)',
                borderRadius: 12,
                padding: '2rem',
                textAlign: 'center',
                color: 'var(--text-muted)',
                fontSize: '0.9375rem',
              }}
            >
              Aucun résultat — essayez de modifier vos filtres ou votre recherche
            </div>
          )}

          {/* Results grid */}
          {!loading && !error && resultCount > 0 && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
                gap: '1rem',
              }}
            >
              {results.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default SearchPage
