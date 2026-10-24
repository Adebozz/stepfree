import type { JourneyLeg, Place } from '../../api/types'

export type LatLon = [number, number]

export interface MapLeg {
  key: string
  lineId: string | null
  walking: boolean
  /** TfL's geometry only. Never synthesised from the leg's end points. */
  path: LatLon[]
}

export interface MapStop {
  key: string
  name: string
  position: LatLon
  kind: 'start' | 'change' | 'end'
}

const LONDON_BOUNDS = { minLat: 51.0, maxLat: 52.0, minLon: -1.0, maxLon: 0.8 }

function valid([lat, lon]: LatLon): boolean {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= LONDON_BOUNDS.minLat && lat <= LONDON_BOUNDS.maxLat
    && lon >= LONDON_BOUNDS.minLon && lon <= LONDON_BOUNDS.maxLon
}

/** Legs that TfL gave geometry for, with points outside Greater London's surroundings dropped as bad data. */
export function mapLegs(legs: JourneyLeg[]): MapLeg[] {
  return legs
    .map((leg, index) => ({
      key: `leg-${index}`,
      lineId: leg.lineId,
      walking: !leg.lineId && leg.mode === 'walking',
      path: leg.path.filter(valid),
    }))
    .filter((leg) => leg.path.length >= 2)
}

/** How many legs have no usable geometry; the UI says so instead of drawing a straight line. */
export function legsWithoutGeometry(legs: JourneyLeg[]): number {
  return legs.length - mapLegs(legs).length
}

function position(place: Place | null): LatLon | null {
  if (!place || place.latitude === null || place.longitude === null) return null
  const point: LatLon = [place.latitude, place.longitude]
  return valid(point) ? point : null
}

/** Start, interchanges and end, where TfL gave coordinates. */
export function mapStops(legs: JourneyLeg[]): MapStop[] {
  const stops: MapStop[] = []
  legs.forEach((leg, index) => {
    const from = position(leg.from)
    if (from && leg.from?.name) stops.push({ key: `from-${index}`, name: leg.from.name, position: from, kind: index === 0 ? 'start' : 'change' })
  })
  const last = legs.at(-1)
  const end = last ? position(last.to) : null
  if (last?.to?.name && end) stops.push({ key: 'end', name: last.to.name, position: end, kind: 'end' })
  return stops
}

export function bounds(legs: MapLeg[], stops: MapStop[]): [LatLon, LatLon] | null {
  const points = [...legs.flatMap((leg) => leg.path), ...stops.map((stop) => stop.position)]
  if (points.length === 0) return null
  const lats = points.map((p) => p[0])
  const lons = points.map((p) => p[1])
  return [
    [Math.min(...lats), Math.min(...lons)],
    [Math.max(...lats), Math.max(...lons)],
  ]
}
