# ADR 001: Technology Stack Selection

## Status
Accepted (updated 2026-03-31)

## Date
2026-01-27

## Context

We need to choose a technology stack for PartyScout, a party and event venue finder application. The application needs:
- A responsive web frontend
- A REST API backend
- Integration with Google Places API and Anthropic Claude API
- Firebase authentication
- Email notifications via SMTP
- Cloud deployment capability
- Persistent storage for saved events and search history

## Decision

### Frontend: React + Vite

**Chosen**: React 19 with Vite 7

**Alternatives Considered**:
- Next.js: Overkill for a single-page wizard app
- Vue.js: Smaller ecosystem
- Angular: Steeper learning curve, heavier

**Rationale**:
- React has the largest ecosystem and community
- Vite provides fast development experience
- No need for SSR (wizard is client-side)
- Firebase Auth SDK has first-class React support

### Backend: Kotlin + Spring Boot

**Chosen**: Kotlin 2.0 with Spring Boot 3.3

**Alternatives Considered**:
- Node.js/Express: Good, but less type safety
- Go: Fast, but smaller web framework ecosystem
- Python/FastAPI: Good, but JVM better for this use case

**Rationale**:
- Kotlin provides type safety with concise syntax
- Spring Boot has excellent HTTP client (WebClient) for reactive Google Places + Anthropic calls
- Spring Security integrates cleanly with Firebase token verification
- Strong JSON handling (Jackson)
- Easy deployment to Cloud Run

### AI: Anthropic Claude Haiku

**Chosen**: Claude Haiku via Anthropic Messages API

**Rationale**:
- Low latency for real-time intent extraction and SSE streaming
- Structured JSON output for intent fields (city, age, occasion, indoor/outdoor)
- Cost-effective for high-frequency calls

### Database: PostgreSQL via Cloud SQL

**Chosen**: Cloud SQL (PostgreSQL) with Cloud SQL Connector; H2 in-memory for local dev

**Rationale**:
- Managed, low-ops PostgreSQL on GCP
- Cloud SQL Connector avoids VPC complexity
- Flyway migrations for schema management
- H2 in local dev avoids Cloud SQL dependency

### Authentication: Firebase Auth

**Chosen**: Firebase Authentication with FirebaseAuthFilter

**Rationale**:
- Supports Google + email/password sign-in
- ID token verification is simple with Firebase Admin SDK
- Avoids building auth infrastructure from scratch

### Email: Zoho SMTP

**Chosen**: Zoho SMTP (`smtp.zoho.com:587`)

**Rationale**:
- Affordable transactional email
- Supports TLS/STARTTLS
- Feedback notifications + auto-reply template

### Cloud Platform: Google Cloud Platform

**Chosen**: GCP with Cloud Run

**Alternatives Considered**:
- AWS Lambda + API Gateway: More complex setup
- Heroku: Less control, higher cost at scale

**Rationale**:
- Using Google Places API (same ecosystem)
- Cloud Run provides simple container deployment
- Secret Manager for all credentials (API keys, SMTP, Firebase)
- Pay-per-use pricing; scale to zero

## Consequences

### Positive
- Modern, maintainable codebase
- Fast development iteration
- Easy deployment and scaling
- Strong type safety end-to-end
- Zero infrastructure management for auth and secrets

### Negative
- JVM cold start time on Cloud Run
- Multiple external API dependencies (Places, Anthropic, Firebase, Zoho)
- Cloud SQL adds cost vs. pure serverless

### Risks
- Vendor lock-in with GCP and Anthropic
- Cloud Run cold starts affect first request latency
- Anthropic API availability affects chat feature

## Related Decisions
- ADR 002: State Management
- ADR 003: API Design
- ADR 004: Party Type Taxonomy
