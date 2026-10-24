import type { JourneyAlert, JourneyOption, LineStatus, SavedJourney, StationAccessibility, User } from '../api/types'

/** Shaped like real backend responses for Wembley Park → King's Cross (step-free), 23 Oct 2026. */
export const stepFreeJourney: JourneyOption = {
  departureTime: '2026-10-23T16:24:00Z',
  arrivalTime: '2026-10-23T16:58:00Z',
  durationMinutes: 34,
  interchanges: 0,
  walkingMinutes: 12,
  modes: ['walking', 'tube'],
  lineIds: ['metropolitan'],
  disrupted: false,
  fareTotalPence: 360,
  obstacles: { lifts: 3, stairs: 0, escalators: 0, walkways: 1 },
  legs: [
    {
      mode: 'walking', lineId: null, lineName: null, summary: 'Transfer to Wembley Park Station', detail: 'Transfer to Wembley Park Station',
      departureTime: '2026-10-23T16:24:00Z', arrivalTime: '2026-10-23T16:29:00Z', durationMinutes: 5, distanceMetres: 120,
      from: { id: null, name: 'Wembley Park', latitude: 51.56, longitude: -0.28 },
      to: { id: '940GZZLUWYP', name: 'Wembley Park', latitude: 51.56, longitude: -0.28 },
      intermediateStops: [], path: [], steps: [],
      obstacles: [{ type: 'WALKWAY', incline: 'UP', position: 'IDEST' }, { type: 'LIFT', incline: 'DOWN', position: 'IDEST' }],
      disruptions: [], disrupted: false,
    },
    {
      mode: 'tube', lineId: 'metropolitan', lineName: 'Metropolitan', summary: "Metropolitan line to King's Cross St.Pancras",
      detail: 'Metropolitan line towards Aldgate', departureTime: '2026-10-23T16:29:00Z', arrivalTime: '2026-10-23T16:51:00Z',
      durationMinutes: 22, distanceMetres: null,
      from: { id: '940GZZLUWYP', name: 'Wembley Park', latitude: 51.56, longitude: -0.28 },
      to: { id: '940GZZLUKSX', name: "King's Cross St. Pancras", latitude: 51.53, longitude: -0.12 },
      intermediateStops: [{ id: '940GZZLUFYR', name: 'Finchley Road', latitude: null, longitude: null }],
      path: [[51.56, -0.28], [51.53, -0.12]], steps: [], obstacles: [], disruptions: [], disrupted: false,
    },
  ],
}

export const disruptedJourney: JourneyOption = {
  ...stepFreeJourney,
  disrupted: true,
  interchanges: 1,
  fareTotalPence: null,
  obstacles: { lifts: 0, stairs: 0, escalators: 0, walkways: 0 },
}

export const centralLine: LineStatus = {
  id: 'central',
  name: 'Central',
  mode: 'tube',
  level: 'MINOR_DISRUPTION',
  summary: 'Minor Delays',
  statuses: [
    {
      severityCode: 9, severityDescription: 'Minor Delays', level: 'MINOR_DISRUPTION',
      reason: 'Central Line: Minor delays due to train cancellations.', validFrom: '2026-10-23T14:44:33Z', validTo: '2026-10-24T00:29:00Z',
      stepFreeAccessAffected: false,
    },
  ],
}

export const wembleyCentralAccessibility: StationAccessibility = {
  stationId: 'HUBWMB',
  name: 'Wembley Central',
  hub: true,
  stations: [
    {
      id: '940GZZLUWYC', name: 'Wembley Central', modes: ['tube'],
      accessibility: [
        { key: 'AccessViaLift', label: 'Step-free access via lift', value: null },
        { key: 'Toilet', label: 'Accessible toilet', value: null },
      ],
      additionalInformation: null,
      facilities: [{ key: 'Lifts', label: 'Lifts', value: '2' }],
    },
  ],
  liftOutages: [],
  liftDataAvailable: false,
  disruptions: [
    {
      stationId: '940GZZLUWYC', stationName: 'Wembley Central', type: 'Interchange Message', appearance: 'RealTime',
      description: 'Wembley Central: No Step Free Access - Step free access is not available.', validFrom: null, validTo: null,
      lineIds: [], mentionsAccessibility: true,
    },
  ],
  disruptionDataAvailable: true,
  stationInfoRetrievedAt: '2026-10-23T16:00:00Z',
}

export const signedInUser: User = {
  id: '6f1c2a9e-1111-4c6b-9d55-2b9a2f0c0001',
  fullName: 'Ada Lovelace',
  email: 'ada@example.com',
  createdAt: '2026-10-24T09:00:00Z',
}

export const savedCommute: SavedJourney = {
  id: '0b7d5c1e-2222-4f0a-8a11-5d2e8f9a0002',
  name: 'Home to work',
  origin: { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' },
  destination: { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross & St Pancras International" },
  accessibility: ['STEP_FREE_TO_PLATFORM'],
  preference: null,
  monitoringEnabled: true,
  lineIds: ['metropolitan'],
  stations: [
    { id: '940GZZLUWYP', role: 'ORIGIN' },
    { id: '940GZZLUKSX', role: 'DESTINATION' },
  ],
  createdAt: '2026-10-24T09:05:00Z',
  updatedAt: '2026-10-24T09:05:00Z',
}

export const metropolitanAlert: JourneyAlert = {
  id: '7a1e0c3d-3333-4b2a-9c11-1f2e3d4c0003',
  journeyId: savedCommute.id,
  journeyName: 'Home to work',
  type: 'NEW',
  title: 'Metropolitan: Minor Delays',
  message: 'Metropolitan Line: Minor delays between Harrow-on-the-Hill and Uxbridge due to train cancellations.',
  createdAt: '2026-10-24T14:21:26Z',
  read: false,
}

export const resolvedLiftAlert: JourneyAlert = {
  ...metropolitanAlert,
  id: '7a1e0c3d-4444-4b2a-9c11-1f2e3d4c0004',
  type: 'RESOLVED',
  title: 'No longer reported: Lift out of service',
  message: 'TfL no longer reports this disruption. It previously said: WEMBLEY PARK STATION: no lift service.',
  createdAt: '2026-10-24T13:00:00Z',
  read: true,
}
