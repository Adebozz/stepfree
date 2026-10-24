import { ArrowRight, Footprints, Repeat, TriangleAlert } from 'lucide-react'
import { Link } from 'react-router'

import type { JourneyOption } from '../api/types'
import { formatDuration, formatFare, formatTime } from '../utils/time'
import { obstacleSummaryText } from '../utils/obstacles'
import { RouteChain } from './RouteChain'

interface JourneyCardProps {
  journey: JourneyOption
  index: number
  /** URL of this journey's details page. */
  detailsHref: string
}

export function JourneyCard({ journey, index, detailsHref }: JourneyCardProps) {
  const fare = formatFare(journey.fareTotalPence)
  const headingId = `journey-${index}-heading`
  return (
    <article aria-labelledby={headingId} className="animate-fade-in rounded-xl border border-navy-100 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h3 id={headingId} className="text-lg font-semibold text-navy-900">
          <span className="sr-only">{`Option ${index + 1}:`}</span>{' '}
          {formatTime(journey.departureTime)} – {formatTime(journey.arrivalTime)}
        </h3>
        <p className="text-lg font-semibold text-navy-900">{formatDuration(journey.durationMinutes)}</p>
      </div>

      <div className="mt-3">
        <RouteChain legs={journey.legs} />
      </div>

      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-700">
        <li className="inline-flex items-center gap-1.5">
          <Repeat aria-hidden="true" className="size-4 text-slate-500" />
          {journey.interchanges === 0 ? 'No changes' : `${journey.interchanges} change${journey.interchanges === 1 ? '' : 's'}`}
        </li>
        <li className="inline-flex items-center gap-1.5">
          <Footprints aria-hidden="true" className="size-4 text-slate-500" />
          {journey.walkingMinutes} min walking
        </li>
        {fare && <li>{fare} pay as you go</li>}
      </ul>

      <p className="mt-2 text-sm text-slate-600">{obstacleSummaryText(journey.obstacles)}</p>

      {journey.disrupted && (
        <p className="mt-3 inline-flex items-center gap-1.5 rounded-md bg-amber-50 px-2 py-1 text-sm font-medium text-amber-900 ring-1 ring-amber-300">
          <TriangleAlert aria-hidden="true" className="size-4" />
          Disruption reported on this route
        </p>
      )}

      <div className="mt-4">
        <Link
          to={detailsHref}
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand hover:text-brand-dark hover:underline"
        >
          View journey details{' '}
          <span className="sr-only">{`for option ${index + 1}`}</span>
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
      </div>
    </article>
  )
}
