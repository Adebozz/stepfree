# Phase 0 — Feasibility Report

**Date:** 2026-10-23
**Method:** Every TfL claim below was verified by calling the live Unified API (`api.tfl.gov.uk`) without an app key and by reading the official swagger spec (`/swagger/docs/v1`). Library versions were read from Maven Central and the npm registry on the same date. Anything not verified that way is explicitly marked **UNVERIFIED**.

---

## 1. TfL Unified API — verified capabilities

### 1.1 Authentication and limits

| Item | Finding | Status |
|---|---|---|
| Key required? | No. All endpoints used below returned HTTP 200 anonymously. | Verified |
| Anonymous limit | 50 requests/minute (TfL API portal). | Documented by TfL |
| With free `app_key` | 500 requests/minute; key sent as `app_key` query param. `app_id` no longer needed. | Documented by TfL |
| Over-limit response | Forum reports both `429` and `420`. Client must treat both as rate-limited. | Partially verified |
| Upstream caching | Responses carry `cache-control: max-age=150, s-maxage=300` (journey results). | Verified |

**Decision:** The backend works anonymously for local development; `TFL_APP_KEY` is optional and read from the environment. The key never reaches the browser — all TfL calls go through the backend.

### 1.2 Station search (FR-01) — feasible

`GET /StopPoint/Search/{query}?modes=tube,overground,elizabeth-line,dlr,national-rail,tram`

- "wembley" → 4 matches: *Wembley Central* (`HUBWMB`), *North Wembley* (`HUBNWB`), *Wembley Park* (`940GZZLUWYP`), *Wembley Stadium* (`910GWEMBLSM`).
- Each match has `id`, `name`, `modes`, `zone`, `lat`, `lon`, **`icsId`**.
- No results → HTTP 200 with `"matches": []` (not an error).
- Unknown stop id on `/StopPoint/{id}` → HTTP 404 with a structured `ApiError`.

**Important finding:** search returns *hub* ids (`HUB…`) for multi-mode interchanges. Passing a hub id to the journey planner returns **HTTP 300 + a `DisambiguationResult`**, not journeys. Passing the **ICS code** (`icsId`) works for both hubs and single stations. → We store and plan journeys by ICS code, and keep the NaPTAN/hub id as the stable station identity.

### 1.3 Line status (FR-02) — feasible

`GET /Line/Mode/tube,overground,dlr,elizabeth-line,tram/Status`

- Returns every line with `lineStatuses[]`: `statusSeverity` (int), `statusSeverityDescription`, `reason`, `validityPeriods[]`, `disruption`.
- Observed today: Central *Minor Delays (9)*, Northern *Severe Delays (6)*, Suffragette *Suspended (2)*, Tram *Part Suspended (3)*.
- London Overground is now **six named lines** (`liberty`, `lioness`, `mildmay`, `suffragette`, `weaver`, `windrush`). The UI must not hard-code a single "Overground" line.
- There is **no "last updated" timestamp** for the feed as a whole (`created` is `0001-01-01`). → We show **the time *we* fetched it** and say so in the UI ("Retrieved 17:24"), never "TfL updated at…".
- Severity code `10` = Good Service. A *missing* line or a failed fetch is shown as "Status unavailable", never as Good Service.

### 1.4 Journey planning (FR-03) — feasible

`GET /Journey/JourneyResults/{fromIcs}/to/{toIcs}`

Each journey has `duration`, `startDateTime`, `arrivalDateTime`, `legs[]`, `fare`. Each leg has:
- `mode.id`, `duration`, `instruction.summary`, `instruction.steps` (walking steps)
- `routeOptions[].lineIdentifier.id` → the **actual line used** (e.g. `metropolitan`)
- `path.stopPoints[]` → intermediate stations (NaPTAN ids)
- `path.lineString` → **real route geometry** (a JSON-encoded string of `[lat, lon]` pairs)
- `disruptions[]`, `isDisrupted`, `plannedWorks[]`
- **`obstacles[]`** → `{type: LIFT|STAIRS|ESCALATOR|WALKWAY, incline: UP|DOWN, stopId, position}`

Failure modes observed: HTTP 300 for ambiguous inputs; HTTP 400 with a readable `message` for invalid parameters.

### 1.5 Accessibility preferences (FR-04) — feasible, with exact supported values

Swagger enum for `accessibilityPreference` (comma-separated list):

| TfL value | Our UI label | Verified effect |
|---|---|---|
| `NoRequirements` | (default) | — |
| `StepFreeToPlatform` | Step-free street to platform | Changes results ✔ |
| `StepFreeToVehicle` | Step-free street to train | Accepted ✔ |
| `NoSolidStairs` | Avoid stairs | Accepted, changes results ✔ |
| `NoEscalators` | Avoid escalators | Accepted ✔ |
| `NoElevators` | Avoid lifts | Accepted ✔ |

Also supported: `journeyPreference` = `LeastTime | LeastInterchange | LeastWalking` (covers "minimise walking"), `walkingSpeed` = `Slow | Average | Fast`, `maxWalkingMinutes`.

Real example: Wembley Park → King's Cross with no preference gives a 20-minute single tube leg; with `StepFreeToPlatform` it gives 34 minutes including walking legs to the step-free entrances. The preference is genuinely honoured by TfL.

Invalid values return HTTP 400 → we validate against a Java enum **before** calling TfL, so users never see a TfL error for this.

**Requirement not supported by TfL:** none of the five requested preferences is missing. We do **not** invent anything beyond these enum values.

### 1.6 Station accessibility (FR-05) — partially feasible

`GET /StopPoint/{naptanId}` → `additionalProperties[]`:

- Category `Accessibility` (source `LRAD`): `AccessViaLift`, `LimitedCapacityLift`, `SpecificEntranceRequired`, `BlueBadgeCarParkSpaces`, `Toilet`, `TaxiRankOutsideStation`, `AddtionalInformation` (sic — TfL's spelling; free text, sometimes truncated).
- Category `Facility`: `Lifts`, `Escalators`, `Toilets`, `Boarding Ramps`, `Gates`, `Help Points`, `WiFi`, etc.

**Not exposed:** a definitive "step-free: yes/no" classification per platform; per-lift live operational status for every lift.

**Coverage varies by station** → each field is shown as *Yes / No / Information unavailable*. A missing property is never treated as "No".

### 1.7 Lift disruptions — available but UNDOCUMENTED

`GET /Disruptions/Lifts/v2/` returned 18 entries, each `{stationUniqueId, disruptedLiftUniqueIds[], message}` — e.g. Wembley Park lift 5 out of service until autumn 2026.

This endpoint **does not appear in the official swagger**. It works today but has no stability guarantee. → It is implemented behind its own client method, failures degrade to "Lift status unavailable", and the UI labels it as "Reported lift outages", never "All lifts working".

### 1.8 Disruptions (FR-06) — feasible, with a key limitation

- `GET /Line/Mode/{modes}/Disruption` → `{category, type, description, affectedRoutes, affectedStops}`, **no id, no timestamps**.
- `GET /StopPoint/Mode/{modes}/Disruption` → `{atcoCode, stationAtcoCode, fromDate, toDate, description, type, appearance, concernedLines[]}`, **no id**.

**Implication:** TfL gives no stable disruption id. We must build our own identity:
- **Identity key** = source + affected entity (line id or station id) + provider category/type + validity start (when supplied).
- **Content hash** = SHA-256 of normalised description + severity.
- Same identity key with a new content hash → *updated* event. Key no longer present in a cycle → *resolved*. This is unit-tested in Phase 4.

### 1.9 Map (FR-12) — feasible without inventing geometry

Journey legs include `path.lineString` (real polyline) and stations include `lat`/`lon`. We draw TfL's geometry only. If a leg has no `lineString` we show markers + the text itinerary, never a straight line. OpenStreetMap tiles require visible attribution and fair use ("© OpenStreetMap contributors"). Heavy production traffic should move to a tile provider. **UNVERIFIED:** current OSM tile-usage policy wording — re-check in Phase 5.

---

## 2. Contradictions, risks and unrealistic assumptions in the requirements

| # | Issue | Impact | Resolution |
|---|---|---|---|
| R1 | **Free backend hosts usually sleep when idle.** A sleeping backend runs no scheduler, so "receive a notification when conditions change" (success criterion 10) would silently stop. | High | Phase 6: verify current free tiers; options are an always-on free tier (if one exists), an external free cron hitting a protected `/internal/monitoring/run` endpoint, or GitHub Actions scheduled workflow. Documented as a known limitation if none is reliable. |
| R2 | **Cross-site cookies.** HttpOnly session cookies (FR-07) break if frontend and backend are on different sites — browsers block third-party cookies. | High | Serve the API **same-origin** via the frontend host's rewrite/proxy (`/api/*` → backend), or one container serving both. Decided in Phase 6; local dev uses the Vite proxy so it behaves the same. |
| R3 | No stable TfL disruption ids (§1.8). | Medium | Fingerprinting design above. |
| R4 | Lift endpoint undocumented (§1.7). | Medium | Isolated, optional, clearly labelled. |
| R5 | No feed-level "updated at" timestamp (§1.3). | Low | Show retrieval time, labelled as such. |
| R6 | "Do not claim a route is affected merely because one of its possible lines is disrupted" vs. saved journeys being just origin + destination. | Medium | A saved journey stores **the specific route option the user chose** (its lines and stations). Matching is done against that route, not against every line serving the stations. |
| R7 | Anonymous limit of 50 req/min is low for a public demo. | Medium | Caffeine caching (see §4), plus a free app key in production. |
| R8 | "Station" entity in requirements implies a full station table, but TfL is the source of truth. | Low | `station` stores only stations referenced by saved journeys (a cache for stable ids), not a mirror of the whole network. |
| R9 | Email sending in production needs a provider; free tiers change often. | Medium | Mailpit (Docker) locally. Production provider chosen and verified in Phase 4/6. |
| R10 | Boot 4 is a major version (Jackson 3, modular starters). | Low | Generated via start.spring.io to get correct starter names; documented. |

Nothing requested is outright impossible. The only items that **cannot** be done exactly as written are: a feed "last updated" time (R5) and a guaranteed per-lift live status (§1.6/1.7).

---

## 3. Final technology versions

Versions verified against Maven Central / npm on 2026-10-23. Backend library versions are **managed by the Spring Boot BOM** unless listed as overridden — we don't pin them by hand.

### Backend

| Component | Version | Notes |
|---|---|---|
| Java | 21 (installed: 21.0.12) | LTS, as required |
| Spring Boot | **4.1.2** | Latest stable 4.1.x |
| Spring Framework / Security | 7.0.10 / 7.1.2 | via Boot BOM |
| Hibernate ORM | 7.4.13 | via Boot BOM |
| Jackson | 3.1.7 | Boot 4 uses Jackson 3 (`tools.jackson` packages) |
| Flyway | 12.4.0 | via Boot BOM (Maven Central has 13.x; we stay on the version Boot tests against) |
| PostgreSQL JDBC | 42.7.14 | via Boot BOM |
| Caffeine | 3.2.4 | via Boot BOM |
| Testcontainers | 2.0.5 | via Boot BOM |
| springdoc-openapi | **3.1.1** | Built against Boot 4.1.0 — the only explicit version |
| Maven | via Maven Wrapper | Maven isn't installed on this machine; the wrapper makes the build reproducible |
| PostgreSQL server | 17 (Docker image) | |

### Frontend

| Component | Version | Notes |
|---|---|---|
| Node.js | 22 (installed: 22.19) | |
| React / React DOM | 19.3.x | |
| Vite | 8.3.x + `@vitejs/plugin-react` 6.1 | |
| TypeScript | **6.0.3** (not 7.0) | `typescript-eslint` 8.72 supports TS `<6.1`; TS 7 would break linting |
| Tailwind CSS | 4.3.x via `@tailwindcss/vite` | |
| React Router | 8.5.x | |
| TanStack Query | 5.104.x | |
| React Hook Form / Zod | 7.90.x / 4.6.x | |
| Leaflet / React Leaflet | 1.9.4 / 5.0.0 | React Leaflet 5 requires React 19 ✔ |
| Lucide React | 1.61.x | |
| Recharts | 3.10.x | Only if a chart earns its place |
| Vitest / RTL / jsdom | 5.0.x / 16.3.x / 30.x | |
| Playwright | 1.64.x | |
| ESLint | 10.x + typescript-eslint 8.72 | |

Exact versions are pinned in `package-lock.json` when the frontend is created in Phase 2.

---

## 4. Caching plan (Caffeine)

| Data | TTL | Reason |
|---|---|---|
| Station search results | 24 h | Station names change rarely |
| Station detail / accessibility properties | 12 h | Static-ish LRAD data |
| Line status | 60 s | Live data; protects the 50/min limit |
| Line & stop disruptions | 60 s | Same |
| Lift disruptions | 120 s | Same |
| Journey results | 60 s, keyed on full request | Short; TfL itself says max-age 150 s |

Every cached response DTO carries `retrievedAt`. If TfL fails and a stale entry exists, we return it with `stale: true` and the UI says "Showing information from 17:24 — live data currently unavailable".

---

## 5. Authentication decision (FR-07)

**Chosen:** server-side sessions (Spring Security + Spring Session JDBC), `SESSION` cookie that is `HttpOnly`, `Secure` (prod), `SameSite=Lax`; CSRF protection on with Spring Security's SPA cookie pattern (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header). Passwords hashed with BCrypt (via `DelegatingPasswordEncoder`, so the algorithm can change later).

**Why not JWT in localStorage:** any XSS bug would expose the token; tokens can't be revoked without extra machinery. **Why JDBC-backed sessions:** they survive restarts/redeploys of a single free-tier instance and allow real logout/"delete account" invalidation. Trade-off: one DB lookup per authenticated request — negligible at this scale.

---

## 6. MVP definition

**MVP (public, no account needed) = Phases 1–2:**
1. Station search with ambiguity handled (hubs vs. stations).
2. Live line status with retrieval time and honest "unavailable" states.
3. Journey planning with the six verified accessibility/journey preferences.
4. Journey results: legs, lines, duration, interchanges, walking, obstacles (lifts/stairs/escalators), disruptions on the journey.
5. Station accessibility panel (LRAD properties + reported lift outages), with "Information unavailable" where TfL is silent.
6. Graceful degradation when TfL is down.

**Full product = Phases 3–6:** accounts, saved journeys (with the chosen route), scheduled monitoring, alert matching, email notifications, notification centre, map, CI/CD, deployment.

---

## 7. Assumptions to revisit

1. TfL keeps the `icsId`-based planning behaviour (it's been stable for years, but it's not contractually guaranteed).
2. The undocumented lift endpoint keeps working; if not, the lift panel shows "unavailable".
3. A free, always-on (or externally triggerable) backend host exists at deployment time — **checked in Phase 6, not assumed.**
4. A free transactional email tier exists at deployment time — **checked in Phase 4/6.**
