import 'leaflet/dist/leaflet.css'

import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip } from 'react-leaflet'

import type { JourneyLeg } from '../api/types'
import { bounds, mapLegs, mapStops } from '../features/map/geometry'
import { lineColour } from '../utils/lines'

const STOP_COLOURS = { start: '#0f766e', change: '#0b1f3a', end: '#b91c1c' }

/**
 * Draws TfL's route geometry on OpenStreetMap tiles. Loaded lazily (Leaflet is ~150 kB), and only
 * a visual aid: the itinerary below is the accessible description of the same route.
 */
export default function JourneyMap({ legs }: { legs: JourneyLeg[] }) {
  const lines = mapLegs(legs)
  const stops = mapStops(legs)
  const area = bounds(lines, stops)
  if (!area) return null

  return (
    <MapContainer
      bounds={area}
      boundsOptions={{ padding: [24, 24] }}
      scrollWheelZoom={false}
      className="h-72 w-full rounded-lg sm:h-96"
      // Leaflet makes the map focusable; give it a name so keyboard and screen-reader users know what it is.
      aria-label="Map of this route. The step-by-step directions below describe the same route."
    >
      <TileLayer
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        maxZoom={19}
      />
      {lines.map((leg) => (
        <Polyline
          key={leg.key}
          positions={leg.path}
          pathOptions={leg.walking ? { color: '#475569', weight: 4, dashArray: '6 8' } : { color: lineColour(leg.lineId), weight: 6 }}
        />
      ))}
      {stops.map((stop) => (
        <CircleMarker
          key={stop.key}
          center={stop.position}
          radius={7}
          pathOptions={{ color: '#ffffff', weight: 2, fillColor: STOP_COLOURS[stop.kind], fillOpacity: 1 }}
        >
          <Tooltip>{stop.name}</Tooltip>
        </CircleMarker>
      ))}
    </MapContainer>
  )
}
