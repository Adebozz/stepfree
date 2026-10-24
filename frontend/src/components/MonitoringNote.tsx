import { useMonitoringStatus } from '../api/notifications'
import { formatDateTime } from '../utils/time'

/** Says when journeys were last checked, so an empty alert list can't be mistaken for "all clear" if checks stopped. */
export function MonitoringNote() {
  const status = useMonitoringStatus()
  if (!status.data) return null
  const { lastCheckedAt, lastStatus, schedulerEnabled } = status.data
  let text: string
  if (!lastCheckedAt) {
    text = schedulerEnabled ? 'Your saved journeys will be checked against TfL within a few minutes.' : 'Automatic checks are not running on this server.'
  } else if (lastStatus === 'PARTIAL') {
    text = `Last checked against TfL at ${formatDateTime(lastCheckedAt)}. Some TfL feeds were unavailable, so some disruptions may be missing.`
  } else if (lastStatus === 'FAILED') {
    text = `The last check at ${formatDateTime(lastCheckedAt)} couldn't reach TfL. Alerts may be out of date.`
  } else {
    text = `Last checked against TfL at ${formatDateTime(lastCheckedAt)}.`
  }
  return <p className="text-sm text-slate-600">{text}</p>
}
