# TfL API Integration

All TfL access goes through `com.stepfreelondon.tfl.TflClient`. No other class sends HTTP requests to TfL, and only mappers read TfL's raw response records (`tfl.model`). The frontend never calls TfL and never sees the app key.

## Endpoints in use

| Our endpoint | TfL endpoint | Cache TTL | Phase |
|---|---|---|---|
| `GET /api/v1/stations?query=` | `GET /StopPoint/Search/{query}?modes=…&maxResults=15&includeHubs=true` | 24 h | 1 ✅ |
| `GET /api/v1/lines/status` | `GET /Line/Mode/tube,elizabeth-line,overground,dlr,tram/Status` | 60 s | 1 ✅ |
| `GET /api/v1/journeys` | `GET /Journey/JourneyResults/{fromIcs}/to/{toIcs}` | 60 s | 2 |
| `GET /api/v1/stations/{id}/accessibility` | `GET /StopPoint/{id}` + `GET /Disruptions/Lifts/v2/` (undocumented) | 12 h / 120 s | 2 |
| `GET /api/v1/disruptions` | `GET /Line/Mode/{modes}/Disruption`, `GET /StopPoint/Mode/{modes}/Disruption` | 60 s | 2 |
| Monitoring cycle (no endpoint) | line status, `/StopPoint/Mode/{modes}/Disruption`, lifts, and `/StopPoint/{hub}` to expand hubs | shared with the above | 4 |

## Client behaviour

| Concern | Behaviour |
|---|---|
| Timeouts | connect 3 s, read 8 s (`stepfree.tfl.*`) |
| Retries | Max 2 attempts in total, backoff 250 ms doubling. Only for 5xx responses and network errors/timeouts. |
| Not retried | 404 → `TflNotFoundException`; 400 → `TflBadRequestException`; 429/420 → `TflRateLimitedException` (with `Retry-After`); 401/403 → `TflUnavailableException` with an error log about `TFL_APP_KEY`; unreadable body → `TflUnavailableException` |
| Auth | Optional `TFL_APP_KEY`, added as the `app_key` query param by an interceptor |
| Secrets in logs | Network exceptions embed the full URL, so only their cause type is logged; other text passes through `TflClient.redact()` |
| Metrics | Timer `stepfree.tfl.requests{endpoint,outcome}`; Caffeine cache stats per cache name |
| Redirects | Not followed (TfL uses HTTP 300 for journey disambiguation, which Phase 2 handles explicitly) |

## Freshness and degradation

Every live response is wrapped as `{ data, retrievedAt, stale }`:

- `retrievedAt` is when **we** fetched the data. TfL's status feed has no trustworthy "last updated" field.
- If TfL fails with a transient error and we hold a copy younger than 6 h, it is returned with `stale: true`. The UI must say so.
- With no usable copy, the API returns **503** `application/problem+json` (`/problems/upstream-unavailable` or `/problems/upstream-rate-limited` with `Retry-After`). A line is never reported as Good Service when data is missing.

## Line severity mapping

From `GET /Line/Meta/Severity` (checked 2026-10-23), rail modes:

| Codes | TfL descriptions | Our `ServiceLevel` |
|---|---|---|
| 10, 18 | Good Service, No Issues | `GOOD_SERVICE` |
| 0, 19 | Special Service, Information | `INFORMATION` |
| 7, 9, 12, 14, 15, 17 | Reduced Service, Minor Delays, Exit Only, Change of frequency, Diverted, Issues Reported | `MINOR_DISRUPTION` |
| 3, 5, 6, 8, 11, 13 | Part Suspended, Part Closure, Severe Delays, Bus Service, Part Closed, **No Step Free Access** | `SEVERE_DISRUPTION` |
| 1, 2, 4, 16, 20 | Closed, Suspended, Planned Closure, Not Running, Service Closed | `CLOSED` |
| anything else / no status | — | `UNKNOWN` (ranks above Good Service) |

Code 13 also sets `stepFreeAccessAffected: true`, because that matters most to this app's users.

## Identifier gotcha

Station search returns hub ids (`HUBWMB`) for interchanges. The journey planner answers hub ids with HTTP 300 (disambiguation). Every result therefore carries `icsCode`, which the planner accepts for hubs and single stations alike.

## Attribution

TfL data is used under TfL's Transport Data Service terms. The UI will show "Powered by TfL Open Data".
