# StepFree London

Accessible journey planning and disruption monitoring for London, built on the official TfL Unified API.

> **Status: Phase 3 of 6 complete.** Step-free journey planning, station accessibility (including lift outages), live line status and disruptions, plus accounts and saved journeys, work end to end. Disruption alerts for saved journeys are next; see [`docs/implementation-checklist.md`](docs/implementation-checklist.md).

## The problem

Lift outages, station closures and line disruptions can make a London journey impossible for wheelchair users and people with reduced mobility, and the information is spread across several places. StepFree London plans journeys with TfL's own step-free preferences, shows what is actually known about station accessibility, and (in later phases) watches saved journeys for disruptions.

The app never claims a journey is accessible beyond what TfL's data supports. Missing data is shown as *unavailable*, never as "fine".

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · Caffeine · springdoc-openapi · JUnit 5 / Testcontainers. Frontend: React 19 · TypeScript · Vite · Tailwind.

## Run locally

Prerequisites: Java 21, Docker. Maven is not needed; the wrapper downloads it.

```bash
cp .env.example .env          # optional; defaults work for local dev
docker compose up -d          # PostgreSQL on localhost:5432
cd backend
./mvnw spring-boot:run        # http://localhost:8080
```

In a second terminal, start the frontend (Node 22+):

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173 (proxies /api to :8080)
```

Try the API directly:

```bash
curl "http://localhost:8080/api/v1/stations?query=Wembley"
curl "http://localhost:8080/api/v1/lines/status"
```

- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

No TfL key is needed for development (anonymous limit: 50 requests/min). For more headroom, set `TFL_APP_KEY` in `.env` with a free key from the [TfL API portal](https://api-portal.tfl.gov.uk).

## Tests

```bash
cd backend && ./mvnw test     # needs Docker running
cd frontend && npm test && npm run lint && npm run typecheck
```

## Documentation

- [Feasibility report](docs/feasibility.md): what the TfL API can and can't do, verified against live calls
- [Architecture](docs/architecture.md)
- [TfL integration](docs/api-integration.md)
- [Database](docs/database.md)
- [Testing](docs/testing.md)

Powered by TfL Open Data.
