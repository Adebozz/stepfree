import { LoaderCircle, MapPin, X } from 'lucide-react'
import { useId, useState, type KeyboardEvent } from 'react'

import { MIN_STATION_QUERY, useStationSearch } from '../api/queries'
import type { StationSummary } from '../api/types'
import type { StationRef } from '../features/journey/search'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { modeLabel } from '../utils/lines'

interface StationSearchProps {
  label: string
  value: StationRef | null
  onChange: (station: StationRef | null) => void
  error?: string
  placeholder?: string
}

/**
 * Station picker implementing the WAI-ARIA 1.2 combobox pattern: arrow keys move through results,
 * Enter selects, Escape closes, and result counts are announced to screen readers.
 *
 * While the user is typing, `typed` holds their text; once a station is chosen it is cleared and the
 * input shows the chosen station's name. This avoids syncing state in an effect when the parent
 * changes `value` (e.g. the swap button).
 */
export function StationSearch({ label, value, onChange, error, placeholder = 'Station name' }: StationSearchProps) {
  const inputId = useId()
  const listId = useId()
  const errorId = useId()
  const [typed, setTyped] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)

  const text = typed ?? value?.name ?? ''
  const debounced = useDebouncedValue(typed ?? '', 250)
  const search = useStationSearch(debounced)
  const results: StationSummary[] = typed !== null && debounced.trim().length >= MIN_STATION_QUERY ? (search.data?.data ?? []) : []
  const showList = open && typed !== null && typed.trim().length >= MIN_STATION_QUERY
  const optionId = (index: number) => `${listId}-option-${index}`

  function select(station: StationSummary) {
    if (!station.icsCode) return
    onChange({ id: station.id, icsCode: station.icsCode, name: station.name })
    setTyped(null)
    setOpen(false)
    setActiveIndex(-1)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((index) => Math.min(index + 1, results.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter' && showList && activeIndex >= 0) {
      event.preventDefault()
      const station = results[activeIndex]
      if (station) select(station)
    } else if (event.key === 'Escape') {
      setOpen(false)
      setActiveIndex(-1)
    }
  }

  let announcement = ''
  if (showList) {
    if (search.isFetching && results.length === 0) announcement = 'Searching stations…'
    else if (search.isError) announcement = 'Station search is unavailable.'
    else if (results.length === 0 && !search.isFetching) announcement = 'No stations found.'
    else announcement = `${results.length} station${results.length === 1 ? '' : 's'} found. Use up and down arrows to choose.`
  }

  return (
    <div className="relative">
      <label htmlFor={inputId} className="block text-sm font-medium text-navy-900">
        {label}
      </label>
      <div className="relative mt-1">
        <MapPin aria-hidden="true" className="pointer-events-none absolute top-1/2 left-3 size-5 -translate-y-1/2 text-slate-400" />
        <input
          id={inputId}
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          aria-autocomplete="list"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={showList && activeIndex >= 0 ? optionId(activeIndex) : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          placeholder={placeholder}
          value={text}
          onChange={(event) => {
            setTyped(event.target.value)
            setOpen(true)
            setActiveIndex(-1)
            if (value) onChange(null)
          }}
          onFocus={() => typed !== null && setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={handleKeyDown}
          className={`block w-full rounded-lg border bg-white py-2.5 pr-10 pl-10 text-base text-navy-900 placeholder:text-slate-500 ${
            error ? 'border-red-600' : 'border-navy-200'
          }`}
        />
        {search.isFetching && typed !== null && (
          <LoaderCircle aria-hidden="true" className="absolute top-1/2 right-3 size-5 -translate-y-1/2 animate-spin text-slate-400" />
        )}
        {value && typed === null && (
          <button
            type="button"
            onClick={() => {
              onChange(null)
              setTyped('')
            }}
            className="absolute top-1/2 right-2 -translate-y-1/2 rounded-md p-1 text-slate-500 hover:bg-navy-50 hover:text-navy-900"
          >
            <X aria-hidden="true" className="size-4" />
            <span className="sr-only">Clear {label.toLowerCase()}</span>
          </button>
        )}
      </div>

      <ul
        id={listId}
        role="listbox"
        aria-label={`${label} suggestions`}
        hidden={!showList || results.length === 0}
        className="absolute z-20 mt-1 max-h-80 w-full overflow-auto rounded-lg border border-navy-100 bg-white py-1 shadow-lg"
      >
        {results.map((station, index) => {
          const disabled = !station.icsCode
          return (
            <li
              key={station.id}
              id={optionId(index)}
              role="option"
              aria-selected={index === activeIndex}
              aria-disabled={disabled || undefined}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => select(station)}
              className={`cursor-pointer px-3 py-2 ${index === activeIndex ? 'bg-navy-100' : 'hover:bg-navy-50'} ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              <span className="block font-medium text-navy-900">{station.name}</span>
              <span className="block text-xs text-slate-600">
                {station.hub ? 'Interchange · ' : ''}
                {station.modes.map(modeLabel).join(', ') || 'Station'}
                {station.zone ? ` · Zone ${station.zone}` : ''}
                {disabled ? ' · Journey planning unavailable' : ''}
              </span>
            </li>
          )
        })}
      </ul>

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {showList && !search.isFetching && (results.length === 0 || search.isError) && (
        <p className="mt-1 text-sm text-slate-600">{search.isError ? 'Station search is unavailable right now.' : 'No stations match that name.'}</p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}
