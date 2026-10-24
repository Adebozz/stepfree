import { useLineStatus } from '../api/queries'
import type { LineStatus } from '../api/types'

/**
 * Line statuses for the lines a journey uses. TfL's journey feed sometimes carries only some of a
 * line's current statuses (e.g. "Minor Delays" but not "Part Suspended"), so the complete status feed
 * is preferred; the journey feed's copy is the fallback when the status feed is unavailable.
 */
export function useJourneyLineStatuses(lineIds: string[], fromJourneyFeed: LineStatus[]): LineStatus[] {
  const status = useLineStatus()
  const wanted = new Set(lineIds)
  const full = status.data?.data.filter((line) => wanted.has(line.id))
  if (full && full.length > 0) {
    // Keep lines the status board doesn't cover (e.g. buses, National Rail) from the journey feed.
    const covered = new Set(full.map((line) => line.id))
    return [...full, ...fromJourneyFeed.filter((line) => wanted.has(line.id) && !covered.has(line.id))]
  }
  return fromJourneyFeed.filter((line) => wanted.has(line.id))
}
