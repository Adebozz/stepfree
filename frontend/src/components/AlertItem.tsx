import { BellRing, CircleCheck, RefreshCw } from 'lucide-react'
import { useId } from 'react'

import { useMarkAlertRead } from '../api/notifications'
import type { AlertType, JourneyAlert } from '../api/types'
import { formatDateTime } from '../utils/time'

const TYPE_LABELS: Record<AlertType, { label: string; icon: typeof BellRing; className: string }> = {
  NEW: { label: 'New disruption', icon: BellRing, className: 'bg-amber-100 text-amber-950' },
  UPDATED: { label: 'Updated', icon: RefreshCw, className: 'bg-sky-100 text-sky-950' },
  RESOLVED: { label: 'No longer reported', icon: CircleCheck, className: 'bg-green-100 text-green-950' },
}

/** `headingLevel` keeps the page outline valid: h2 on the Alerts page, h3 under a dashboard section. */
export function AlertItem({ alert, compact = false, headingLevel = 2 }: { alert: JourneyAlert; compact?: boolean; headingLevel?: 2 | 3 }) {
  const headingId = useId()
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  const markRead = useMarkAlertRead()
  const type = TYPE_LABELS[alert.type]
  const Icon = type.icon

  return (
    <article
      aria-labelledby={headingId}
      className={`rounded-xl border p-4 shadow-sm ${alert.read ? 'border-navy-100 bg-white' : 'border-amber-300 bg-amber-50/40'}`}
    >
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-semibold ${type.className}`}>
          <Icon aria-hidden="true" className="size-3.5" />
          {type.label}
        </span>
        {!alert.read && <span className="rounded-full bg-navy-900 px-2 py-0.5 font-semibold text-white">Unread</span>}
        <span className="text-slate-600">{formatDateTime(alert.createdAt)}</span>
      </div>
      <Heading id={headingId} className="mt-2 font-semibold text-navy-900">
        {alert.title}
      </Heading>
      <p className="text-sm text-slate-700">Saved journey: {alert.journeyName}</p>
      {!compact && <p className="mt-2 whitespace-pre-line text-sm text-navy-900">{alert.message}</p>}
      {!alert.read && (
        <button
          type="button"
          onClick={() => markRead.mutate(alert.id)}
          disabled={markRead.isPending}
          aria-label={`Mark "${alert.title}" as read`}
          className="mt-3 rounded-lg border border-navy-200 px-3 py-1.5 text-sm font-medium text-navy-900 hover:bg-navy-50 disabled:opacity-60"
        >
          Mark as read
        </button>
      )}
    </article>
  )
}
