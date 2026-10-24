import { CircleCheck, RefreshCw } from 'lucide-react'
import { useState } from 'react'

import { useDisruptions, useLineStatus } from '../api/queries'
import { DisruptionAlert } from '../components/DisruptionAlert'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { FreshnessNote } from '../components/FreshnessNote'
import { LineStatusCard } from '../components/LineStatusCard'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { modeLabel } from '../utils/lines'
import { filterLines, type LineFilter } from '../utils/serviceLevel'
import { formatDate } from '../utils/time'


function LineSection() {
  const status = useLineStatus()
  const [filter, setFilter] = useState<LineFilter>('all')
  const [mode, setMode] = useState('all')

  if (status.isPending) return <LoadingSkeleton label="Loading line status" rows={6} />
  if (status.isError) return <ErrorState error={status.error} title="Line status unavailable" onRetry={() => void status.refetch()} />

  const lines = status.data.data
  const modes = [...new Set(lines.map((line) => line.mode))]
  const visible = filterLines(lines, filter, mode)

  return (
    <section aria-labelledby="lines-heading" className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 id="lines-heading" className="text-xl font-semibold text-navy-900">
          Lines
        </h2>
        <div className="flex flex-wrap items-end gap-3">
          <fieldset className="flex rounded-lg border border-navy-200 bg-white p-0.5">
            <legend className="sr-only">Show</legend>
            {(['all', 'disrupted'] as const).map((value) => (
              <label key={value} className={`cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium has-focus-visible:outline-3 has-focus-visible:outline-brand ${filter === value ? 'bg-navy-900 text-white' : 'text-navy-800 hover:bg-navy-50'}`}>
                <input type="radio" name="line-filter" value={value} checked={filter === value} onChange={() => setFilter(value)} className="sr-only" />
                {value === 'all' ? 'All lines' : 'With problems'}
              </label>
            ))}
          </fieldset>
          <div>
            <label htmlFor="mode-filter" className="block text-xs font-medium text-slate-700">
              Mode
            </label>
            <select id="mode-filter" value={mode} onChange={(event) => setMode(event.target.value)} className="mt-0.5 rounded-lg border border-navy-200 bg-white px-3 py-1.5 text-sm">
              <option value="all">All modes</option>
              {modes.map((m) => (
                <option key={m} value={m}>
                  {modeLabel(m)}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
      <FreshnessNote retrievedAt={status.data.retrievedAt} stale={status.data.stale} />
      <p role="status" className="sr-only">
        Showing {visible.length} of {lines.length} lines.
      </p>
      {visible.length === 0 ? (
        <EmptyState icon={CircleCheck} title="No problems reported">
          TfL reports good service on every line matching this filter.
        </EmptyState>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((line) => (
            <li key={line.id}>
              <LineStatusCard line={line} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function StationDisruptionSection() {
  const disruptions = useDisruptions()
  const [accessibilityOnly, setAccessibilityOnly] = useState(true)

  if (disruptions.isPending) return <LoadingSkeleton label="Loading station disruptions" rows={3} />
  if (disruptions.isError) {
    return <ErrorState error={disruptions.error} title="Station disruptions unavailable" onRetry={() => void disruptions.refetch()} />
  }

  const { stationDisruptions, liftOutages, liftDataAvailable } = disruptions.data.data
  const visible = accessibilityOnly ? stationDisruptions.filter((d) => d.mentionsAccessibility) : stationDisruptions

  return (
    <section aria-labelledby="stations-heading" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="stations-heading" className="text-xl font-semibold text-navy-900">
          Station disruptions
        </h2>
        <label className="inline-flex items-center gap-2 text-sm text-navy-900">
          <input type="checkbox" checked={accessibilityOnly} onChange={(event) => setAccessibilityOnly(event.target.checked)} className="size-4 accent-brand" />
          Only lifts, escalators and step-free access
        </label>
      </div>
      <FreshnessNote retrievedAt={disruptions.data.retrievedAt} stale={disruptions.data.stale} />
      <p className="text-sm text-slate-700">
        {liftDataAvailable
          ? `TfL reports ${liftOutages.length} lift outage${liftOutages.length === 1 ? '' : 's'} across the network.`
          : 'Lift outage data is unavailable right now. This does not mean all lifts are working.'}{' '}
        {accessibilityOnly && 'The filter matches keywords in TfL descriptions, so it may miss some relevant notices.'}
      </p>
      <p role="status" className="sr-only">
        Showing {visible.length} station disruptions.
      </p>
      {visible.length === 0 ? (
        <EmptyState icon={CircleCheck} title="No matching station disruptions">
          TfL reports none right now.
        </EmptyState>
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2">
          {visible.map((disruption, index) => (
            <li key={`${disruption.stationId}-${index}`}>
              <DisruptionAlert
                title={`${disruption.stationName ?? 'Station'}${disruption.type ? ` · ${disruption.type}` : ''}`}
                accessibility={disruption.mentionsAccessibility}
                meta={disruption.validTo ? `Until ${formatDate(disruption.validTo)}` : undefined}
              >
                {disruption.description}
              </DisruptionAlert>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function StatusPage() {
  useDocumentTitle('Live status')
  const lines = useLineStatus()
  const disruptions = useDisruptions()
  const refreshing = lines.isFetching || disruptions.isFetching

  return (
    <div className="space-y-10">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-navy-900">Live status</h1>
          <p className="mt-2 text-slate-700">Current service on Underground, Elizabeth line, Overground, DLR and Tram, from TfL.</p>
        </div>
        <button
          type="button"
          onClick={() => {
            void lines.refetch()
            void disruptions.refetch()
          }}
          disabled={refreshing}
          className="inline-flex items-center gap-2 rounded-lg bg-white px-4 py-2 text-sm font-semibold text-navy-900 ring-1 ring-navy-200 hover:bg-navy-50 disabled:opacity-60"
        >
          <RefreshCw aria-hidden="true" className={`size-4 ${refreshing ? 'animate-spin' : ''}`} />
          {refreshing ? 'Refreshing…' : 'Refresh'}
        </button>
      </header>
      <LineSection />
      <StationDisruptionSection />
    </div>
  )
}
