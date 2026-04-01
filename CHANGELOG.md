# Changelog

All notable changes to PartyScout Backend will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Direct venue booking integration
- Party checklist generator
- Mobile app

---

## [3.0.0] - 2026-03-31

### Added
- **AI Chat (Scout)** — SSE streaming chat assistant (`POST /api/chat`)
  - Claude Haiku extracts structured intent (city, age, occasion, indoor/outdoor, themes)
  - Parallel Google Places searches via Flux (up to 5 concurrent queries)
  - Venue setting inference and filtering before returning top 3 results
  - `[VENUES]` payload appended after text stream for inline venue cards
  - Conversation history sanitization (strips `[VENUES]` payloads from context)
  - Fallback to broader query on empty results

- **LLM Venue Filter** (`LlmFilterService`) — Claude Haiku filters venues by age/persona appropriateness before scoring

- **PersonaService** — maps child age to persona + generates 15-20 age-appropriate search query sets; used in both wizard and chat

- **Comprehensive venue search**
  - Outdoor-specific query sets when `setting=outdoor` (venue-type queries, not prefixed indoor queries)
  - 3 outdoor queries always appended to every search to ensure outdoor filter is never empty
  - `inferSetting` uses exact Place-type Set + word-boundary regex for park/field/yard; amusement_park no longer mis-classified as outdoor

- **Saved Events** — authenticated CRUD endpoints
  - `GET /api/v2/saved-events` — list user's saved events
  - `POST /api/v2/saved-events` — save a venue as an event
  - `DELETE /api/v2/saved-events/{id}` — delete a saved event

- **Firebase Authentication**
  - `POST /api/v2/auth/me` — verifies Firebase ID token, creates/updates user record
  - `FirebaseAuthFilter` — authenticates Bearer tokens on protected endpoints
  - `SecurityConfig` — permits public endpoints, requires auth for saved-events

- **Feedback** (`POST /api/v2/feedback`)
  - Sends notification email to `scout@partyscout.live` via Zoho SMTP
  - Sends auto-reply to submitter; template in `src/main/resources/templates/feedback-autoreply.txt`
  - Auto-reply text is editable without code changes (use `{greeting}` placeholder)

- **Weather Forecast** (`GET /api/v2/weather/forecast`) — forecast by ZIP + date

- **Outbox pattern** — domain events persisted to `outbox_events` table; `OutboxPoller` publishes to GCP Pub/Sub

- **PostgreSQL / Cloud SQL** — production database via Cloud SQL Connector; H2 in-memory for local dev

- **Flyway migrations** — schema managed in `src/main/resources/db/migration/`

- **VenueEnrichmentService** — batch DB lookup to hydrate saved metadata for returned venues

- **SearchPersistenceService** — persists search requests and venue results for analytics

### Changed
- Venue search now always appends outdoor queries to every request
- `buildSearchQueries` in ChatController uses persona-based queries for indoor/unspecified; outdoor-specific queries when `indoor=false`
- Feedback auto-reply text extracted to external template file

---

## [2.1.0] - 2026-01-29

### Changed
- Simplified party types from 12 specific categories to 6 broad categories
  - Active Play (trampoline, gymnastics, skating, swimming)
  - Creative (arts, crafts, cooking, science)
  - Amusement (arcade, movies, escape rooms, bowling)
  - Outdoor (parks, zoos, farms, adventure)
  - Characters & Performers (magicians, princesses, entertainers)
  - Social & Dining (restaurants, cafes, party rooms)

### Added
- Comprehensive documentation suite

---

## [2.0.0] - 2026-01-28

### Added
- 5-Step Party Planning Wizard
- Smart Venue Matching with match score algorithm (0-100)
- Venue Details (what's included, what to bring, suggested add-ons)
- Compare Mode — select up to 3 venues side-by-side
- `POST /api/v2/party-wizard/search`, `GET /api/v2/party-wizard/party-types/{age}`, `POST /api/v2/party-wizard/estimate-budget`

### Changed
- Complete frontend redesign with wizard-based UI
- React Context-based state management
- Added Cloud Run deployment + GCP Secret Manager

---

## [1.0.0] - 2026-01-27

### Added
- Initial release — basic venue search, Google Places API integration

---

## Version History Summary

| Version | Date | Highlights |
|---------|------|------------|
| 3.0.0 | 2026-03-31 | AI chat, saved events, Firebase auth, feedback email, comprehensive search |
| 2.1.0 | 2026-01-29 | Simplified to 6 party types, docs |
| 2.0.0 | 2026-01-28 | 5-step wizard, smart matching, compare mode |
| 1.0.0 | 2026-01-27 | Initial release |

---

[Unreleased]: https://github.com/GouriKA/partyScout-backend/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/GouriKA/partyScout-backend/compare/v2.1.0...v3.0.0
[2.1.0]: https://github.com/GouriKA/partyScout-backend/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/GouriKA/partyScout-backend/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/GouriKA/partyScout-backend/releases/tag/v1.0.0
