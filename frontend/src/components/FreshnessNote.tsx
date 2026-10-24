import { Clock, TriangleAlert } from 'lucide-react'

import { formatTime } from '../utils/time'

interface FreshnessNoteProps {
  retrievedAt: string
  stale: boolean
  className?: string
}

/**
 * Says when the data was fetched from TfL. If it's an old cached copy, says so prominently: cached
 * data must never be presented as current.
 */
export function FreshnessNote({ retrievedAt, stale, className = '' }: FreshnessNoteProps) {
  const time = formatTime(retrievedAt)
  if (stale) {
    return (
      <p role="status" className={`flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900 ring-1 ring-amber-300 ${className}`}>
        <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <span>
          <strong>Live TfL data is unavailable.</strong> Showing information retrieved at {time}, which may be out of date.
        </span>
      </p>
    )
  }
  return (
    <p className={`flex items-center gap-1.5 text-sm text-slate-600 ${className}`}>
      <Clock aria-hidden="true" className="size-4" />
      <span>
        Retrieved from TfL at <time dateTime={retrievedAt}>{time}</time>
      </span>
    </p>
  )
}
