import { ArrowLeft, MapPin } from 'lucide-react'
import { Link, useParams } from 'react-router'

import { ApiError } from '../api/client'
import { useStationAccessibility } from '../api/queries'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { FreshnessNote } from '../components/FreshnessNote'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { StationInformation } from '../components/StationInformation'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { modeLabel } from '../utils/lines'

export function StationPage() {
  const { id } = useParams()
  const station = useStationAccessibility(id)
  useDocumentTitle(station.data ? `${station.data.data.name} accessibility` : 'Station accessibility')

  const back = (
    <Link to="/" className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline">
      <ArrowLeft aria-hidden="true" className="size-4" />
      Plan a journey
    </Link>
  )

  if (station.isPending) return <LoadingSkeleton label="Loading station information" rows={4} />
  if (station.isError) {
    return (
      <div className="space-y-4">
        {back}
        {station.error instanceof ApiError && station.error.status === 404 ? (
          <EmptyState icon={MapPin} title="Station not found">
            TfL doesn't recognise this station.
          </EmptyState>
        ) : (
          <ErrorState error={station.error} title="Station information unavailable" onRetry={() => void station.refetch()} />
        )}
      </div>
    )
  }

  const data = station.data.data
  const modes = [...new Set(data.stations.flatMap((section) => section.modes))]
  return (
    <div className="space-y-6">
      {back}
      <header>
        <h1 className="text-3xl font-bold tracking-tight text-navy-900">{data.name}</h1>
        <p className="mt-1 text-slate-700">
          {data.hub ? 'Interchange · ' : ''}
          {modes.map(modeLabel).join(', ') || 'Station'}
        </p>
        <FreshnessNote retrievedAt={station.data.retrievedAt} stale={station.data.stale} className="mt-2" />
      </header>
      <div className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
        <StationInformation station={data} />
      </div>
    </div>
  )
}
