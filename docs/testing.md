# Testing

## Running

```bash
cd backend
./mvnw test        # all backend tests; Docker must be running (Testcontainers)

cd frontend
npm test           # Vitest + Testing Library (jsdom)
```

The suite makes **no live TfL calls**. TfL fixtures in `backend/src/test/resources/tfl/` are real responses captured from the live API on 2026-10-23, so mapping tests run against TfL's actual JSON shape.

## Backend suite (Phase 3): 127 tests, all passing

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

Added in Phase 3:

| Test class | Type | Covers |
|---|---|---|
| `AuthIntegrationTest` (14) | `@SpringBootTest(RANDOM_PORT)` + Testcontainers, real HTTP via `ApiClient` (cookie jar + SPA CSRF) | Register signs in with `HttpOnly`/`SameSite=Lax` cookie; bcrypt hash, none in the session row; CSRF required; login replaces an existing session id (old id gone from `spring_session`); case-insensitive email; wrong password ≡ unknown email; 6th failure → 429 + `Retry-After` even with the right password; duplicate email 409; validation and 72-byte limit; logout; email change needs password and keeps the session; password change signs out other sessions; account deletion removes user and every session |
| `SavedJourneyIntegrationTest` (8) | Same, TfL mocked with captured fixtures | CRUD with `Location`, ICS codes from TfL, duplicate stops collapsed, route unchanged by PATCH; **user B gets 404 on A's journey for read/update/delete, identical to a missing id**; names unique per user, not globally; stations stored once; unknown TfL station → 400; malformed input → 400 with field paths; 401 when signed out; account deletion cascades |
| `LoginRateLimiterTest` (4) | Unit, controllable clock | Per-email block with exact `Retry-After`; window expiry; per-IP spraying; success clears email count but not IP count |

`ApiClient` (test helper) uses the JDK `HttpClient` with a `CookieManager` and echoes `XSRF-TOKEN` exactly as the SPA does, so these tests exercise the real session and CSRF machinery instead of MockMvc's `csrf()` shortcut.

## Frontend suite (Phase 3): 64 tests, all passing

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
| `client.test.ts` (4) | `apiSend` fetches a CSRF token when missing and echoes it; reuses an existing one; problem → `ApiError`; `safeNextPath` rejects off-site redirects |
| `route.test.ts` (6) | Route roles (origin, intermediate, interchange, destination), hub ids kept, invalid ids dropped, walking-only journeys, name suggestion, re-plan needs ICS codes |
| `SignInPage.test.tsx` (4) | Signs in and returns to `next`; shows the server's message; ignores an off-site `next`; validates before calling the server |
| `SaveJourneyPanel.test.tsx` (3) | Signed-out users get a sign-in link back to this page; saves the route and preferences; explains a duplicate name |
| `SavedJourneysPage.test.tsx` (6) | Guard redirects to sign-in; list with re-plan link; delete needs confirmation; empty state; rename keeps the card's accessible name; optimistic monitoring toggle rolls back on error |

## Manual verification performed (Phase 3, 2026-10-24)

Backend + Vite + Docker Postgres, live TfL, headless Chromium driving the real UI:
- `/saved` while signed out → `/sign-in?next=%2Fsaved` → register → returned to `/saved`.
- Cookies: `SESSION` is `HttpOnly`, `SameSite=Lax`; the CSRF token changes on sign-in.
- Planned Wembley Park → King's Cross (step-free), saved from journey details, renamed, monitoring toggled; both survived a reload. "Plan this journey" re-ran the live search with the saved preferences.
- Sign out → `/saved` guarded again; wrong password → "The email or password is incorrect."; sign in → journey still there.
- Profile name changed; account deleted → signed out, `GET /auth/me` → 401, no user, journey or session rows left.

The browser run found three bugs that unit tests had missed, all fixed with regression tests: buttons announced as "DeleteHome to work" (`sr-only` spacing), the card losing its accessible name while being renamed, and the monitoring checkbox not responding until the server answered (now optimistic with rollback).

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
