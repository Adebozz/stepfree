# Testing

## Running

```bash
cd backend
./mvnw test        # all backend tests; Docker must be running (Testcontainers)

cd frontend
npm test           # Vitest + Testing Library (jsdom)
```

The suite makes **no live TfL calls**. TfL fixtures in `backend/src/test/resources/tfl/` are real responses captured from the live API on 2026-10-23, so mapping tests run against TfL's actual JSON shape.

## Backend suite (Phase 2): 101 tests, all passing

| Test class | Type | Covers |
|---|---|---|
| `TflClientTest` (18) | Unit, `MockRestServiceServer` | Parsing real fixtures; path encoding of user input (no path traversal); app-key handling; retry/backoff on 5xx and timeouts; no retry on 404/400/401/403/429/420; unreadable bodies; latency metric; app-key redaction |
| `LiveDataCacheTest` (8) | Unit, fake clock | TTL hits/misses; stale fallback with original `retrievedAt`; rethrow when nothing cached; non-transient errors not hidden; stale max age |
| `LineStatusMapperTest` (20) | Unit | Real snapshot mapping; missing status → `UNKNOWN`, never Good Service; most-serious-first; step-free flag; validity periods; every severity code |
| `StationMapperTest` (5) | Unit | Real Wembley search; hubs/ICS codes; name cleanup; missing fields |
| `TflDateTimesTest` (3) | Unit | UTC vs London-local timestamps across BST/GMT; TfL placeholder dates |
| `StationControllerTest` (10) | `@WebMvcTest` + real security config | Response shape; validation problems; 503/Retry-After; generic 500 with no leaked details; 401 problem for protected routes; correlation id |
| `LineStatusControllerTest` (2) | `@WebMvcTest` | Stale flag surfaced; outage → 503 |
| `StepfreeLondonApplicationTests` (4) | `@SpringBootTest` + Testcontainers PostgreSQL 17 | Full context boots, Flyway runs, health UP with details hidden, non-public actuator endpoints not exposed, OpenAPI document |

Added in Phase 2:

| Test class | Type | Covers |
|---|---|---|
| `JourneyMapperTest` (7) | Unit | Real step-free and disrupted journey fixtures; legs, obstacles, fares, line ids |
| `JourneyServiceTest` (6) | Unit | Preferences passed to TfL unchanged; caching; no-journey handling |
| `JourneyControllerTest` (7) | `@WebMvcTest` | ICS-code validation; same-station rejection; enum binding; problem responses |
| `StationProfileMapperTest` (3) | Unit | LRAD accessibility fields; missing values stay `null` (unavailable), never "No" |
| `StationProfileServiceTest` (4) | Unit | Hub member merging; only this station's lift outages; live-source failure reported, not hidden; hub-notice and lift-notice de-duplication |
| `DisruptionMapperTest` (4) | Unit | StopPoint disruptions and lift outages; accessibility keyword flag |

## Frontend suite (Phase 2): 41 tests, all passing

| File | Covers |
|---|---|
| `StationSearch.test.tsx` (5) | Debounced search, ARIA combobox keyboard navigation, selection |
| `JourneySearchForm.test.tsx` (5) | Validation, accessibility preferences, swap, submit |
| `JourneyCard.test.tsx` (2) | Times, lines, obstacle wording ("reported by TfL") |
| `StationInformation.test.tsx` (6) | Missing data shown as "Information unavailable"; missing lift data never read as "all working"; cut-off TfL notes marked; "no other disruptions" wording |
| `StatusComponents.test.tsx` (8) | Service-level badges, status summary, freshness notes |
| `useJourneyLineStatuses.test.tsx` (2) | Matching live line status to a journey's lines |
| `search.test.ts` (6) | URL ⇄ form state ⇄ API parameter round-trips |
| `utils.test.ts` (7) | London-time formatting across BST/GMT, line colours/contrast, TfL note truncation |

## Manual verification performed (Phase 2, 2026-10-24)

Backend running on Docker Postgres, frontend on Vite dev server, live TfL API, headless Chromium:
- Wembley Park → King's Cross with step-free preferences → 3 real Metropolitan line options, with the live Metropolitan "Minor Delays" alert shown on the results.
- Journey details, live status, station (`/stations/940GZZLUWYP`) and 404 pages all render with no console errors.
- Station page found two TfL data problems, both now handled: the accessibility note is cut off by TfL at 255 characters, and the Wembley Park lift notice was repeated as a station disruption.
- `GET /api/v1/journeys?from=abc…` → 400 RFC 9457 problem with a field error.

## Manual verification performed (Phase 1)

Against the live TfL API with the app running locally on Docker Postgres:
- `GET /api/v1/stations?query=Wembley` → 4 real stations with ICS codes; a repeat search is served from cache (265 ms → 4 ms).
- `GET /api/v1/lines/status` → 20 lines, including the six named Overground lines, with real disruptions.
- TfL unreachable (base URL pointed at a closed port) → one retry, then 503 problem JSON; no URL or key in the logs.
