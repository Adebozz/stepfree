import { ArrowDown, ArrowUp, Info } from 'lucide-react'
import { Link } from 'react-router'

import type { JourneyLeg, JourneyOption, Obstacle } from '../api/types'
import { modeLabel } from '../utils/lines'
import { formatDuration, formatTime } from '../utils/time'
import { DisruptionAlert } from './DisruptionAlert'
import { LineBadge } from './LineBadge'
import { ModeIcon } from './ModeIcon'

const OBSTACLE_LABELS: Record<string, string> = {
  LIFT: 'Lift',
  STAIRS: 'Stairs',
  ESCALATOR: 'Escalator',
  WALKWAY: 'Walkway',
}

function obstacleText(obstacle: Obstacle): string {
  const base = OBSTACLE_LABELS[obstacle.type] ?? obstacle.type.toLowerCase()
  if (obstacle.incline === 'UP') return `${base} up`
  if (obstacle.incline === 'DOWN') return `${base} down`
  return base
}

function StationLink({ place }: { place: JourneyLeg['from'] }) {
  if (!place?.name) return <span>Unknown location</span>
  // Only TfL stop ids (not street addresses) have an accessibility page.
  if (place.id && /^[A-Za-z0-9]{3,20}$/.test(place.id)) {
    return (
      <Link to={`/stations/${place.id}`} className="font-medium text-brand hover:underline">
        {place.name}
      </Link>
    )
  }
  return <span className="font-medium">{place.name}</span>
}

function Leg({ leg }: { leg: JourneyLeg }) {
  const walking = leg.mode === 'walking'
  return (
    <li className="group relative pb-6 pl-10 last:pb-0">
      <span aria-hidden="true" className="absolute top-0 left-0 flex size-7 items-center justify-center rounded-full bg-navy-100 text-navy-800">
        <ModeIcon mode={leg.mode} />
      </span>
      <span aria-hidden="true" className="absolute top-8 bottom-0 left-3.5 w-px bg-navy-200 group-last:hidden" />

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold text-navy-900 tabular-nums">{formatTime(leg.departureTime)}</span>
        {walking ? (
          <span className="text-sm font-medium text-navy-900">Walk</span>
        ) : (
          <LineBadge lineId={leg.lineId} name={leg.lineName ?? modeLabel(leg.mode)} size="md" />
        )}
        <span className="text-sm text-slate-600">{formatDuration(leg.durationMinutes)}</span>
      </div>

      <p className="mt-1 text-sm text-navy-900">{leg.detail ?? leg.summary}</p>
      <p className="mt-1 text-sm text-slate-700">
        <StationLink place={leg.from} /> <span aria-hidden="true">→</span>
        <span className="sr-only">to</span> <StationLink place={leg.to} />
      </p>

      {leg.intermediateStops.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-slate-700">
            {leg.intermediateStops.length} stop{leg.intermediateStops.length === 1 ? '' : 's'} in between
          </summary>
          <ol className="mt-1 list-disc pl-5 text-slate-600">
            {leg.intermediateStops.map((stop, index) => (
              <li key={`${stop.id ?? stop.name}-${index}`}>{stop.name}</li>
            ))}
          </ol>
        </details>
      )}

      {leg.steps.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-slate-700">Walking directions</summary>
          <ol className="mt-1 list-decimal space-y-1 pl-5 text-slate-600">
            {leg.steps.map((step, index) => (
              <li key={index}>{[step.description, step.heading].filter(Boolean).join(' ')}</li>
            ))}
          </ol>
        </details>
      )}

      {leg.obstacles.length > 0 && (
        <ul aria-label="Reported obstacles" className="mt-2 flex flex-wrap gap-1.5">
          {leg.obstacles.map((obstacle, index) => (
            <li key={index} className="inline-flex items-center gap-1 rounded-md bg-navy-50 px-2 py-0.5 text-xs text-navy-800 ring-1 ring-navy-200">
              {obstacle.incline === 'UP' && <ArrowUp aria-hidden="true" className="size-3" />}
              {obstacle.incline === 'DOWN' && <ArrowDown aria-hidden="true" className="size-3" />}
              {obstacleText(obstacle)}
            </li>
          ))}
        </ul>
      )}

      {leg.disruptions.map((disruption, index) => (
        <div key={index} className="mt-3">
          <DisruptionAlert title="Disruption on this leg">{disruption.description}</DisruptionAlert>
        </div>
      ))}
    </li>
  )
}

/** Step-by-step itinerary. Text-first: it works without a map and for screen-reader users. */
export function JourneyDetails({ journey }: { journey: JourneyOption }) {
  const last = journey.legs[journey.legs.length - 1]
  return (
    <section aria-labelledby="itinerary-heading">
      <h2 id="itinerary-heading" className="text-lg font-semibold text-navy-900">
        Step by step
      </h2>
      <ol className="mt-4">
        {journey.legs.map((leg, index) => (
          <Leg key={index} leg={leg} />
        ))}
      </ol>
      {last?.to && (
        <p className="mt-4 flex items-center gap-2 pl-10 text-sm text-navy-900">
          <span className="font-semibold tabular-nums">{formatTime(journey.arrivalTime)}</span>
          Arrive at <StationLink place={last.to} />
        </p>
      )}
      <p className="mt-6 flex gap-2 rounded-lg bg-navy-50 p-3 text-sm text-slate-700">
        <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        Lifts, stairs and escalators are listed only where TfL reports them. Check each station's accessibility details
        before you travel.
      </p>
    </section>
  )
}
