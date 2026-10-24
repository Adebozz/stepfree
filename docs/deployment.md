# Deployment

## Shape

One Docker image (`Dockerfile` at the repo root) holds the React build and the Spring Boot API. Spring serves the SPA from `classpath:/static`, falling back to `index.html` for client routes, so **the browser only ever talks to one origin**. The session cookie is first-party, `SameSite=Lax` works, and no CORS is needed (risk R2).

```
browser ──HTTPS──▶ host proxy ──▶ container :8080  (SPA + /api, prod profile)
                                       │
                                       ├──▶ PostgreSQL (DB_URL)
                                       ├──▶ TfL Unified API
                                       └──▶ SMTP (MAIL_*)
GitHub Actions cron ──POST /internal/monitoring/run (token)──▶ wakes the container, runs one cycle
```

Image: multi-stage (Node build → Maven build → JRE 21 Alpine), runs as a non-root user, layered jar, `HEALTHCHECK` on `/actuator/health/liveness`, JSON (ECS) logs. About 420 MB.

Try it locally: `docker compose --profile app up --build`, then http://localhost:8080.

## Environment variables (production)

| Variable | Required | Notes |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | No defaults in the prod profile: a missing value stops startup. For Neon, `jdbc:postgresql://<host>/<db>?sslmode=require` |
| `SESSION_COOKIE_SECURE` | no | Defaults to `true` in prod. Only set `false` for plain-HTTP testing |
| `TFL_APP_KEY` | recommended | Raises TfL's limit from 50 to 500 requests/minute |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_FROM` | for email alerts | Any SMTP provider. Without it, alerts still appear in the app and emails retry, then fail |
| `APP_BASE_URL` | yes | Public URL, used in email links |
| `MONITORING_ENABLED` | no | In-process scheduler (default on). Set `false` on a sleeping free tier and rely on the trigger, see below |
| `MONITORING_TRIGGER_TOKEN` | for the wake-up | Enables `POST /internal/monitoring/run`; `openssl rand -hex 32` |

`SPRING_PROFILES_ACTIVE=prod` is set in the image. The prod profile also turns off Swagger UI, trusts `X-Forwarded-*` only from private-network proxies (`forward-headers-strategy: native`, so the login rate limit sees real client IPs), and is tuned for serverless Postgres (no idle connections, hourly session cleanup).

## Recommended free setup (checked 2026-10-24)

| Piece | Choice | Free-tier terms (from the providers' pages on 2026-10-24) |
|---|---|---|
| App | **Render** web service, Docker runtime, built from this repo | Spins down after 15 min without inbound traffic; ~1 min to wake; 750 instance-hours/month per workspace |
| Database | **Neon** Postgres | Permanent, no card; 1 GB per project; 100 CU-hours/month per project; suspends after 5 min idle |
| Email | Any SMTP provider's free tier | Not chosen: free email tiers change often and need a verified sending domain |

Render's own free Postgres expires after 30 days, so it isn't used.

**Monitoring on a sleeping host (risk R1).** A spun-down service runs no scheduler. `.github/workflows/monitoring-wake.yml` calls the trigger every 15 minutes, which wakes the service and runs one cycle. Set `MONITORING_ENABLED=false` on the host so the in-process 2-minute scheduler doesn't keep the database awake. Budget, as estimated:
- Render: awake at most ~744 h/month, within 750 h.
- Neon: each cycle wakes the database for about 5–6 minutes out of every 15. That is roughly 290 h/month, or ~73 CU-hours at 0.25 CU. **UNVERIFIED:** Neon's page didn't state the free plan's minimum compute size. Watch the usage page after the first week.

Trade-offs to state plainly:
- Alerts are up to ~15 minutes late, or later when GitHub delays scheduled runs, which it does at busy times.
- The first page load after a quiet spell takes about a minute.
- For real users, a paid always-on instance with `MONITORING_ENABLED=true` removes both.

### Steps
1. Neon: create a project, then copy the JDBC connection details into `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
2. Render: create a Web Service from this GitHub repo with the Docker runtime. Set the environment variables above, with `MONITORING_ENABLED=false` and a `MONITORING_TRIGGER_TOKEN`. Set the health check path to `/actuator/health/readiness`.
3. GitHub, under the repo's settings: add an Actions **variable** `APP_URL` (the Render URL) and a **secret** `MONITORING_TRIGGER_TOKEN` with the same value. Run the "Monitoring wake-up" workflow once by hand and check it returns `"status":"SUCCEEDED"`.

**Not done:** this guide has not been carried out against real Render and Neon accounts. That needs the owner's accounts. Everything up to that point was verified locally with the production image: prod profile, SPA fallback, security headers, the trigger, and the full browser flow against live TfL.

## CI and dependency scanning

- `ci.yml`, on push and PR:
  - Backend: `./mvnw verify`, which includes the Testcontainers tests.
  - Frontend: lint, typecheck, unit tests, build.
  - Playwright with the mocked API, at desktop and mobile sizes.
  - A Docker image build.
- `security.yml`: dependency review on PRs (fails on high severity), plus a weekly `npm audit` of runtime dependencies.
- `dependabot.yml`: weekly Maven and npm updates, monthly Actions and Docker base-image updates.

All workflows pass `actionlint`.
