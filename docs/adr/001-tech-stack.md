# ADR 001: Technology Stack Selection

## Status
Accepted

## Date
2026-01-27

## Context

We need to choose a technology stack for PartyScout, a birthday party venue finder application. The application needs:
- A responsive web frontend
- A REST API backend
- Integration with Google Places API
- Cloud deployment capability

## Decision

### Frontend: React + Vite

**Chosen**: React 19 with Vite

**Alternatives Considered**:
- Next.js: Overkill for a single-page wizard app
- Vue.js: Smaller ecosystem
- Angular: Steeper learning curve, heavier

**Rationale**:
- React has the largest ecosystem and community
- Vite provides fast development experience
- No need for SSR (wizard is client-side)
- Easy to deploy as static files

### Backend: Kotlin + Spring Boot

**Chosen**: Kotlin 2.0 with Spring Boot 3.3

**Alternatives Considered**:
- Node.js/Express: Good, but less type safety
- Go: Fast, but smaller web framework ecosystem
- Python/FastAPI: Good, but JVM better for this use case

**Rationale**:
- Kotlin provides type safety with concise syntax
- Spring Boot has excellent HTTP client (WebClient)
- Strong JSON handling (Jackson)
- Easy deployment to Cloud Run
- Team familiarity with JVM

### Cloud Platform: Google Cloud Platform

**Chosen**: GCP with Cloud Run

**Alternatives Considered**:
- AWS Lambda + API Gateway: More complex setup
- Heroku: Less control, higher cost at scale
- Vercel: Frontend only

**Rationale**:
- Using Google Places API (same ecosystem)
- Cloud Run provides simple container deployment
- Secret Manager for API keys
- Pay-per-use pricing
- Good free tier

## Consequences

### Positive
- Modern, maintainable codebase
- Fast development iteration
- Easy deployment and scaling
- Strong type safety

### Negative
- JVM cold start time on Cloud Run
- Need to learn Kotlin if unfamiliar
- React context may need upgrade to Redux for complex state

### Risks
- Vendor lock-in with GCP
- Cloud Run cold starts affect first request latency

## Related Decisions
- ADR 002: State Management
- ADR 003: API Design
