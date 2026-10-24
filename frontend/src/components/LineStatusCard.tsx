import { Accessibility } from 'lucide-react'

import type { LineStatus } from '../api/types'
import { lineColour } from '../utils/lines'
import { isDisrupted } from '../utils/serviceLevel'
import { formatDateTime } from '../utils/time'
import { ServiceLevelBadge } from './ServiceLevelBadge'

export function LineStatusCard({ line }: { line: LineStatus }) {
  const disrupted = isDisrupted(line.level)
  const reasons = line.statuses.filter((status) => status.reason)
  const stepFreeAffected = line.statuses.some((status) => status.stepFreeAccessAffected)

  return (
    <article className="flex overflow-hidden rounded-xl border border-navy-100 bg-white shadow-sm" aria-labelledby={`line-${line.id}`}>
      <div aria-hidden="true" className="w-2 shrink-0" style={{ backgroundColor: lineColour(line.id) }} />
      <div className="min-w-0 flex-1 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id={`line-${line.id}`} className="font-semibold text-navy-900">
            {line.name}
          </h3>
          <ServiceLevelBadge level={line.level} text={line.level === 'UNKNOWN' ? undefined : line.summary} />
        </div>
        {stepFreeAffected && (
          <p className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-red-800">
            <Accessibility aria-hidden="true" className="size-4" />
            Step-free access affected
          </p>
        )}
        {disrupted && reasons.length > 0 && (
          <details className="mt-2 text-sm text-slate-700">
            <summary className="cursor-pointer font-medium text-navy-800">Details</summary>
            <ul className="mt-2 space-y-2">
              {reasons.map((status, index) => (
                <li key={index} className="break-words">
                  {status.reason}
                  {status.validTo && <span className="block text-xs text-slate-500">Until {formatDateTime(status.validTo)}</span>}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </article>
  )
}
