import { lazy, Suspense } from 'react'

import type { JourneyLeg } from '../api/types'
import { legsWithoutGeometry, mapLegs } from '../features/map/geometry'

const JourneyMap = lazy(() => import('./JourneyMap'))

export function JourneyMapSection({ legs }: { legs: JourneyLeg[] }) {
  const drawable = mapLegs(legs).length
  const missing = legsWithoutGeometry(legs)

  return (
    <section aria-labelledby="map-heading" className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
      <h2 id="map-heading" className="text-lg font-semibold text-navy-900">
        Route map
      </h2>
      {drawable === 0 ? (
        <p className="mt-2 text-sm text-slate-700">TfL didn't provide a route shape for this journey, so there's no map. The directions below are complete.</p>
      ) : (
        <>
          <div className="mt-3">
            <Suspense fallback={<div className="h-72 w-full animate-pulse rounded-lg bg-navy-50 sm:h-96" aria-hidden="true" />}>
              <JourneyMap legs={legs} />
            </Suspense>
          </div>
          <p className="mt-2 text-xs text-slate-600">
            Route shape from TfL.
            {missing > 0 &&
              ` ${missing} part${missing === 1 ? '' : 's'} of the journey had no shape from TfL and ${missing === 1 ? 'is' : 'are'} not drawn; we don't guess.`}
          </p>
        </>
      )}
    </section>
  )
}
