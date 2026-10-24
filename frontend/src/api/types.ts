// TypeScript mirrors of the backend DTOs (see /v3/api-docs). Keep in sync with the Java records.

export interface LiveData<T> {
  data: T
  /** ISO instant at which the backend fetched this from TfL. */
  retrievedAt: string
  /** True when TfL was unreachable and this is an older cached copy. */
  stale: boolean
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  correlationId?: string
  errors?: { field: string; message: string }[]
}

// ---- Stations ----

export interface StationSummary {
  id: string
  icsCode: string | null
  name: string
  modes: string[]
  zone: string | null
  latitude: number | null
  longitude: number | null
  hub: boolean
}

export interface StationDetail {
  id: string
  name: string
  icsCode: string | null
  modes: string[]
  lines: { id: string; name: string }[]
  latitude: number | null
  longitude: number | null
  hub: boolean
}

export interface Fact {
  key: string
  label: string
  /** null means TfL publishes nothing for this field. */
  value: string | null
}

export interface StationSection {
  id: string
  name: string
  modes: string[]
  accessibility: Fact[]
  additionalInformation: string | null
  facilities: Fact[]
}

export interface LiftOutage {
  stationId: string
  liftIds: string[]
  message: string | null
}

export interface StationDisruption {
  stationId: string
  stationName: string | null
  type: string | null
  appearance: string | null
  description: string
  validFrom: string | null
  validTo: string | null
  lineIds: string[]
  mentionsAccessibility: boolean
}

export interface StationAccessibility {
  stationId: string
  name: string
  hub: boolean
  stations: StationSection[]
  liftOutages: LiftOutage[]
  liftDataAvailable: boolean
  disruptions: StationDisruption[]
  disruptionDataAvailable: boolean
  stationInfoRetrievedAt: string
}

export interface DisruptionsOverview {
  stationDisruptions: StationDisruption[]
  liftOutages: LiftOutage[]
  liftDataAvailable: boolean
}

// ---- Lines ----

export type ServiceLevel =
  | 'GOOD_SERVICE'
  | 'INFORMATION'
  | 'UNKNOWN'
  | 'MINOR_DISRUPTION'
  | 'SEVERE_DISRUPTION'
  | 'CLOSED'

export interface LineStatusEntry {
  severityCode: number | null
  severityDescription: string | null
  level: ServiceLevel
  reason: string | null
  validFrom: string | null
  validTo: string | null
  stepFreeAccessAffected: boolean
}

export interface LineStatus {
  id: string
  name: string
  mode: string
  level: ServiceLevel
  summary: string
  statuses: LineStatusEntry[]
}

// ---- Journeys ----

export type AccessibilityPreference =
  | 'STEP_FREE_TO_PLATFORM'
  | 'STEP_FREE_TO_VEHICLE'
  | 'NO_SOLID_STAIRS'
  | 'NO_ESCALATORS'
  | 'NO_ELEVATORS'

export type JourneyPreference = 'LEAST_TIME' | 'LEAST_INTERCHANGE' | 'LEAST_WALKING'
export type WalkingSpeed = 'SLOW' | 'AVERAGE' | 'FAST'

export interface Place {
  id: string | null
  name: string | null
  latitude: number | null
  longitude: number | null
}

export interface Obstacle {
  type: string
  incline: string | null
  position: string | null
}

export interface LegDisruption {
  category: string | null
  description: string
  lastUpdated: string | null
}

export interface JourneyLeg {
  mode: string | null
  lineId: string | null
  lineName: string | null
  summary: string | null
  detail: string | null
  departureTime: string | null
  arrivalTime: string | null
  durationMinutes: number
  distanceMetres: number | null
  from: Place | null
  to: Place | null
  intermediateStops: Place[]
  path: [number, number][]
  steps: { heading: string | null; description: string | null }[]
  obstacles: Obstacle[]
  disruptions: LegDisruption[]
  disrupted: boolean
}

export interface JourneyOption {
  departureTime: string | null
  arrivalTime: string | null
  durationMinutes: number
  interchanges: number
  walkingMinutes: number
  modes: string[]
  lineIds: string[]
  disrupted: boolean
  fareTotalPence: number | null
  obstacles: { lifts: number; stairs: number; escalators: number; walkways: number }
  legs: JourneyLeg[]
}

export interface JourneyPlan {
  journeys: JourneyOption[]
  lineStatuses: LineStatus[]
  appliedAccessibility: AccessibilityPreference[]
  stopMessages: string[]
}

// ---- Account ----

export interface User {
  id: string
  fullName: string
  email: string
  createdAt: string
}

// ---- Saved journeys ----

export type RouteStationRole = 'ORIGIN' | 'INTERCHANGE' | 'INTERMEDIATE' | 'DESTINATION'

export interface RouteStop {
  id: string
  role: RouteStationRole
}

export interface SavedRoute {
  lineIds: string[]
  stations: RouteStop[]
}

export interface SavedJourney {
  id: string
  name: string
  origin: { id: string; icsCode: string | null; name: string }
  destination: { id: string; icsCode: string | null; name: string }
  accessibility: AccessibilityPreference[]
  preference: JourneyPreference | null
  monitoringEnabled: boolean
  lineIds: string[]
  stations: RouteStop[]
  createdAt: string
  updatedAt: string
}

export interface CreateSavedJourney {
  name: string
  originId: string
  destinationId: string
  accessibility: AccessibilityPreference[]
  preference?: JourneyPreference
  route: SavedRoute
}
