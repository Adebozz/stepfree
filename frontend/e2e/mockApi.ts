import type { Page, Route } from '@playwright/test'

import type { JourneyAlert, NotificationPreferences, SavedJourney, StationSummary, User } from '../src/api/types'
import {
  centralLine,
  metropolitanAlert,
  resolvedLiftAlert,
  savedCommute,
  signedInUser,
  stepFreeJourney,
  wembleyCentralAccessibility,
} from '../src/test/fixtures'

const RETRIEVED = '2026-10-24T14:00:00Z'
const live = <T>(data: T) => ({ data, retrievedAt: RETRIEVED, stale: false })

const STATIONS: StationSummary[] = [
  { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park', modes: ['tube'], zone: '4', latitude: 51.5636, longitude: -0.2795, hub: false },
  { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross & St Pancras International", modes: ['tube', 'national-rail'], zone: '1', latitude: 51.5308, longitude: -0.1238, hub: true },
]

/** A real-looking route shape (Metropolitan line, Wembley Park → King's Cross), so the map draws. */
const JOURNEY = {
  ...stepFreeJourney,
  legs: stepFreeJourney.legs.map((leg) =>
    leg.lineId ? { ...leg, path: [[51.5636, -0.2795], [51.5465, -0.1803], [51.5226, -0.1571], [51.5308, -0.1238]] as [number, number][] } : leg,
  ),
}

/**
 * The backend, in the browser: just enough state (session, saved journeys, alerts, preferences) for
 * the UI's flows. Shapes come from the same fixtures as the unit tests, which mirror real responses.
 */
export class MockApi {
  user: User | null
  journeys: SavedJourney[]
  alerts: JourneyAlert[]
  preferences: NotificationPreferences = { emailEnabled: false, monitoringEnabled: true }

  constructor({ signedIn = false } = {}) {
    this.user = signedIn ? signedInUser : null
    this.journeys = signedIn ? [savedCommute] : []
    this.alerts = signedIn ? [metropolitanAlert, resolvedLiftAlert] : []
  }

  async install(page: Page) {
    // Never fetch real map tiles from tests (OSM tile policy); a blank tile is enough.
    await page.route('https://tile.openstreetmap.org/**', (route) => route.fulfill({ status: 204 }))
    await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: 'http://localhost:5173' }])
    await page.route('**/api/v1/**', (route) => this.handle(route))
  }

  private async handle(route: Route) {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()
    const body = request.postDataJSON() as Record<string, unknown> | null
    const json = (data: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    const problem = (status: number, detail: string) =>
      route.fulfill({ status, contentType: 'application/problem+json', body: JSON.stringify({ status, title: 'Error', detail }) })
    const empty = () => route.fulfill({ status: 204 })

    if (method !== 'GET' && request.headers()['x-xsrf-token'] !== 'test-token') return problem(403, 'CSRF token missing')

    switch (`${method} ${path}`) {
      case 'GET /auth/csrf':
        return empty()
      case 'GET /auth/me':
        return this.user ? json(this.user) : problem(401, 'Sign in to continue.')
      case 'POST /auth/login':
        if (body?.password !== 'correct horse battery') return problem(401, 'The email or password is incorrect.')
        this.user = { ...signedInUser, email: String(body.email) }
        this.journeys = [savedCommute]
        this.alerts = [metropolitanAlert, resolvedLiftAlert]
        return json(this.user)
      case 'POST /auth/logout':
        this.user = null
        return empty()
      case 'GET /stations': {
        const query = (url.searchParams.get('query') ?? '').toLowerCase().replace(/['’]/g, '')
        return json(live(STATIONS.filter((s) => s.name.toLowerCase().replace(/['’]/g, '').includes(query))))
      }
      case 'GET /lines/status':
        return json(live([centralLine, { ...centralLine, id: 'metropolitan', name: 'Metropolitan', level: 'GOOD_SERVICE', summary: 'Good Service', statuses: [] }]))
      case 'GET /disruptions':
        return json(live({ stationDisruptions: [], liftOutages: [], liftDataAvailable: true }))
      case 'GET /journeys':
        return json(live({ journeys: [JOURNEY], lineStatuses: [], appliedAccessibility: ['STEP_FREE_TO_PLATFORM'], stopMessages: [] }))
      case 'GET /saved-journeys':
        return this.user ? json(this.journeys) : problem(401, 'Sign in to continue.')
      case 'POST /saved-journeys': {
        const saved = { ...savedCommute, id: crypto.randomUUID(), name: String(body?.name) }
        this.journeys = [saved, ...this.journeys]
        return json(saved, 201)
      }
      case 'GET /alerts':
        return json({ alerts: this.alerts, unreadCount: this.alerts.filter((a) => !a.read).length })
      case 'POST /alerts/read-all':
        this.alerts = this.alerts.map((a) => ({ ...a, read: true }))
        return empty()
      case 'GET /notification-preferences':
        return json(this.preferences)
      case 'PUT /notification-preferences':
        this.preferences = body as unknown as NotificationPreferences
        return json(this.preferences)
      case 'GET /monitoring/status':
        return json({ schedulerEnabled: true, lastCheckedAt: '2026-10-24T14:21:26Z', lastStatus: 'SUCCEEDED' })
    }
    const read = path.match(/^\/alerts\/([^/]+)\/read$/)
    if (method === 'POST' && read) {
      this.alerts = this.alerts.map((a) => (a.id === read[1] ? { ...a, read: true } : a))
      return empty()
    }
    if (method === 'GET' && path.startsWith('/stations/') && path.endsWith('/accessibility')) return json(live(wembleyCentralAccessibility))
    return problem(404, `Not mocked: ${method} ${path}`)
  }
}
