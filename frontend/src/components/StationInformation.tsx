import { CircleCheck, CircleHelp, CircleX, Info } from 'lucide-react'

import type { Fact, StationAccessibility, StationSection } from '../api/types'
import { modeLabel } from '../utils/lines'
import { isCutOffByTfl } from '../utils/notes'
import { formatDate, formatDateTime } from '../utils/time'
import { DisruptionAlert } from './DisruptionAlert'

const UNAVAILABLE = 'Information unavailable'

/**
 * Renders one fact. "Yes"/"No" get an icon as well as text; null is shown explicitly as
 * "Information unavailable", never as "No".
 */
function FactValue({ value }: { value: string | null }) {
  if (value === null) {
    return (
      <span className="inline-flex items-center gap-1 text-slate-600">
        <CircleHelp aria-hidden="true" className="size-4" />
        {UNAVAILABLE}
      </span>
    )
  }
  const normalised = value.trim().toLowerCase()
  if (normalised === 'yes') {
    return (
      <span className="inline-flex items-center gap-1 font-medium text-green-800">
        <CircleCheck aria-hidden="true" className="size-4" />
        Yes
      </span>
    )
  }
  if (normalised === 'no') {
    return (
      <span className="inline-flex items-center gap-1 font-medium text-navy-900">
        <CircleX aria-hidden="true" className="size-4" />
        No
      </span>
    )
  }
  return <span className="text-navy-900">{value}</span>
}

function FactList({ facts, label }: { facts: Fact[]; label: string }) {
  return (
    <dl aria-label={label} className="divide-y divide-navy-100 rounded-lg border border-navy-100">
      {facts.map((fact) => (
        <div key={fact.key} className="grid gap-1 px-3 py-2 text-sm sm:grid-cols-2 sm:gap-4">
          <dt className="text-slate-700">{fact.label}</dt>
          <dd>
            <FactValue value={fact.value} />
          </dd>
        </div>
      ))}
    </dl>
  )
}

function Section({ section, showHeading }: { section: StationSection; showHeading: boolean }) {
  const noAccessibilityData = section.accessibility.every((fact) => fact.value === null)
  // Under the h2; one level deeper when each hub member gets its own h3.
  const SubHeading = showHeading ? 'h4' : 'h3'
  return (
    <section aria-label={`${section.name} ${section.modes.map(modeLabel).join(', ')}`} className="space-y-4">
      {showHeading && (
        <h3 className="text-base font-semibold text-navy-900">
          {section.name} <span className="font-normal text-slate-600">· {section.modes.map(modeLabel).join(', ') || 'Station'}</span>
        </h3>
      )}
      <div>
        <SubHeading className="mb-2 text-sm font-semibold text-navy-900">Accessibility</SubHeading>
        {noAccessibilityData && (
          <p className="mb-2 flex gap-2 text-sm text-slate-700">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            TfL doesn't publish accessibility details for this station. Contact the station or TfL before travelling.
          </p>
        )}
        <FactList facts={section.accessibility} label={`Accessibility at ${section.name}`} />
        {section.additionalInformation && (
          <p className="mt-2 text-sm text-slate-700">
            <span className="font-medium text-navy-900">TfL notes: </span>
            {section.additionalInformation}
            {isCutOffByTfl(section.additionalInformation) && (
              <>
                …<span className="text-slate-500"> (TfL&apos;s text ends here)</span>
              </>
            )}
          </p>
        )}
      </div>
      <div>
        <SubHeading className="mb-2 text-sm font-semibold text-navy-900">Facilities</SubHeading>
        {section.facilities.length > 0 ? (
          <FactList facts={section.facilities} label={`Facilities at ${section.name}`} />
        ) : (
          <p className="text-sm text-slate-600">{UNAVAILABLE}</p>
        )}
      </div>
    </section>
  )
}

export function StationInformation({ station }: { station: StationAccessibility }) {
  return (
    <div className="space-y-8">
      <section aria-labelledby="lifts-heading">
        <h2 id="lifts-heading" className="text-lg font-semibold text-navy-900">
          Lift outages
        </h2>
        <div className="mt-3 space-y-3">
          {!station.liftDataAvailable ? (
            <p className="text-sm text-slate-700">Lift status is unavailable right now. This does not mean all lifts are working.</p>
          ) : station.liftOutages.length === 0 ? (
            <p className="text-sm text-slate-700">TfL reports no lift outages at this station.</p>
          ) : (
            station.liftOutages.map((outage, index) => (
              <DisruptionAlert key={index} title="Lift out of service" accessibility>
                {outage.message ?? 'TfL reports a lift outage at this station.'}
              </DisruptionAlert>
            ))
          )}
        </div>
      </section>

      <section aria-labelledby="disruptions-heading">
        <h2 id="disruptions-heading" className="text-lg font-semibold text-navy-900">
          Station disruptions
        </h2>
        <div className="mt-3 space-y-3">
          {!station.disruptionDataAvailable ? (
            <p className="text-sm text-slate-700">Disruption information is unavailable right now.</p>
          ) : station.disruptions.length === 0 ? (
            <p className="text-sm text-slate-700">
              {station.liftOutages.length > 0
                ? 'TfL reports no other disruptions at this station.'
                : 'TfL reports no disruptions at this station.'}
            </p>
          ) : (
            station.disruptions.map((disruption, index) => (
              <DisruptionAlert
                key={index}
                title={disruption.type ?? 'Disruption'}
                accessibility={disruption.mentionsAccessibility}
                meta={disruption.validTo ? `Until ${formatDate(disruption.validTo)}` : undefined}
              >
                {disruption.description}
              </DisruptionAlert>
            ))
          )}
        </div>
      </section>

      <section aria-labelledby="facilities-heading" className="space-y-6">
        <h2 id="facilities-heading" className="text-lg font-semibold text-navy-900">
          Accessibility and facilities
        </h2>
        {station.stations.map((section) => (
          <Section key={section.id} section={section} showHeading={station.stations.length > 1} />
        ))}
        <p className="text-xs text-slate-600">Station information retrieved from TfL at {formatDateTime(station.stationInfoRetrievedAt)}.</p>
      </section>
    </div>
  )
}
