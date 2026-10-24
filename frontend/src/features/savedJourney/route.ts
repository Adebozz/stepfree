import type { JourneyOption, RouteStop, SavedJourney, SavedRoute } from '../../api/types'
import type { JourneySearch } from '../journey/search'

const STOP_ID = /^[A-Za-z0-9]{3,20}$/
const LINE_ID = /^[a-z0-9-]{1,40}$/
const MAX_LINES = 20
const MAX_STOPS = 300

/**
 * The route to store with a saved journey: the lines ridden and every stop passed, with its role.
 * Walking legs add no stops. The searched origin and destination are always included, because they
 * may be hub ids (e.g. HUBKGX) while the legs use platform-level ids.
 */
export function routeFromJourney(search: Pick<JourneySearch, 'from' | 'to'>, journey: JourneyOption): SavedRoute {
  const stops: RouteStop[] = [{ id: search.from.id, role: 'ORIGIN' }]
  const transitLegs = journey.legs.filter((leg) => leg.lineId)
  transitLegs.forEach((leg, index) => {
    const first = index === 0
    const last = index === transitLegs.length - 1
    if (leg.from?.id) stops.push({ id: leg.from.id, role: first ? 'ORIGIN' : 'INTERCHANGE' })
    leg.intermediateStops.forEach((stop) => {
      if (stop.id) stops.push({ id: stop.id, role: 'INTERMEDIATE' })
    })
    if (leg.to?.id) stops.push({ id: leg.to.id, role: last ? 'DESTINATION' : 'INTERCHANGE' })
  })
  stops.push({ id: search.to.id, role: 'DESTINATION' })

  const seen = new Set<string>()
  return {
    lineIds: [...new Set(journey.lineIds)].filter((id) => LINE_ID.test(id)).slice(0, MAX_LINES),
    stations: stops.filter((stop) => STOP_ID.test(stop.id) && !seen.has(stop.id) && seen.add(stop.id)).slice(0, MAX_STOPS),
  }
}

/** A suggested name, e.g. "Wembley Park to King's Cross", trimmed to the 80-character limit. */
export function defaultJourneyName(search: Pick<JourneySearch, 'from' | 'to'>): string {
  return `${search.from.name} to ${search.to.name}`.slice(0, 80)
}

/** The search that re-plans a saved journey, or null if TfL gave a station no ICS code. */
export function searchFromSaved(saved: SavedJourney): JourneySearch | null {
  if (!saved.origin.icsCode || !saved.destination.icsCode) return null
  return {
    from: { id: saved.origin.id, icsCode: saved.origin.icsCode, name: saved.origin.name },
    to: { id: saved.destination.id, icsCode: saved.destination.icsCode, name: saved.destination.name },
    accessibility: saved.accessibility,
    preference: saved.preference ?? undefined,
    arriveBy: false,
  }
}
