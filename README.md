# PartyScout Backend

Kotlin + Spring Boot REST API powering the PartyScout party planning app. Handles venue search, AI-powered chat, budget estimation, weather forecasts, saved events, and Firebase authentication.

## Tech Stack

- **Language**: Kotlin 2.0 / JVM 17
- **Framework**: Spring Boot 3.3
- **Build**: Gradle (Kotlin DSL)
- **Database**: Cloud SQL (PostgreSQL) via Cloud SQL Connector; H2 in-memory for local dev
- **Migrations**: Flyway
- **Tests**: JUnit 5 + MockK + Spring Boot Test + MockWebServer

## Quick Start

### Prerequisites

- JDK 17+
- Google Places API key
- Anthropic API key (for AI chat and LLM venue filtering)

### Local Development

1. **Set environment variables**:
   ```bash
   export GOOGLE_PLACES_API_KEY=your_key_here
   export ANTHROPIC_API_KEY=your_key_here
   ```

2. **Run**:
   ```bash
   ./gradlew bootRun
   ```

3. **Test**:
   ```bash
   curl http://localhost:8080/api/v2/party-wizard/party-types/7
   ```

The app starts on port `8080` using an in-memory H2 database by default.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v2/party-wizard/search` | Venue search (wizard + landing page) |
| GET | `/api/v2/party-wizard/party-types` | All party types |
| GET | `/api/v2/party-wizard/party-types/{age}` | Age-filtered party types |
| POST | `/api/v2/party-wizard/estimate-budget` | Budget estimate |
| GET | `/api/v2/party-wizard/party-details` | Party type details |
| POST | `/api/chat` | AI chat (SSE streaming) |
| GET | `/api/v2/weather/forecast` | Weather forecast by ZIP + date |
| POST | `/api/v2/feedback` | Submit user feedback |
| POST | `/api/v2/auth/me` | Firebase token verification |
| GET | `/api/v2/saved-events` | List saved events (auth required) |
| POST | `/api/v2/saved-events` | Save an event (auth required) |
| DELETE | `/api/v2/saved-events/{id}` | Delete a saved event (auth required) |

## Project Structure

```
src/main/kotlin/com/partyscout/
├── auth/                        # Firebase auth filter + security config
├── chat/
│   ├── ChatController.kt        # SSE streaming chat endpoint
│   ├── AnthropicService.kt      # Intent extraction + response streaming
│   └── ChatModels.kt            # Request/response models
├── feedback/
│   ├── FeedbackController.kt
│   └── FeedbackService.kt       # Email via Zoho SMTP; auto-reply template
├── llm/
│   └── LlmFilterService.kt      # Claude-based venue age/relevance filter
├── party/
│   ├── model/PartySearchModels.kt
│   └── service/                 # Budget, MatchScore, PartyDetails, PartyType
├── persona/
│   └── PersonaService.kt        # Age → persona + search query sets
├── persistence/
│   ├── entity/                  # JPA entities (Venue, Search, SavedEvent, OutboxEvent)
│   ├── repository/              # Spring Data repositories
│   └── service/                 # SearchPersistence, VenueEnrichment
├── search/
│   └── controller/PartySearchController.kt  # Main venue search endpoint
├── shared/
│   ├── config/                  # CORS, ShedLock
│   ├── event/                   # Domain events, outbox pattern, Pub/Sub publisher
│   └── logging/                 # Request logging filter, log sanitizer
└── weather/                     # Weather forecast service
```

## Venue Search Pipeline

```
POST /api/v2/party-wizard/search
    │
    ├── 1. PersonaService — derive persona + search queries from age
    ├── 2. GooglePlacesService — geocode city, run queries in parallel
    ├── 3. VenueEnrichmentService — batch DB lookup for saved metadata
    ├── 4. LlmFilterService — Claude filters unsuitable venues by age/persona
    ├── 5. MatchScoreService — multi-factor relevance scoring
    ├── 6. BudgetEstimationService — estimated total + per-person cost
    ├── 7. PartyDetailsService — included items, add-ons, duration
    └── 8. Setting/distance filter + sort by matchScore
```

## AI Chat Pipeline

```
POST /api/chat  (SSE)
    │
    ├── 1. Claude Haiku — extractIntent() → structured JSON
    │         (city, age, occasion, indoor, themes, readyToSearch)
    ├── 2. buildSearchQueries() — outdoor or persona-based query set
    ├── 3. GooglePlacesService — parallel search, dedup, top 20
    └── 4. Claude Haiku — streamResponse() → conversational text + [VENUES] payload
```

Claude Haiku is also used in `LlmFilterService` to filter venue results by age-appropriateness before scoring.

## Email

Feedback submissions send two emails via Zoho SMTP (`smtp.zoho.com:587`):
- **Notification** to `scout@partyscout.live` with submitter details
- **Auto-reply** to submitter using the template at `src/main/resources/templates/feedback-autoreply.txt`

To update the auto-reply text, edit `feedback-autoreply.txt` — no code change required. Use `{greeting}` as the placeholder for the personalised greeting line.

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GOOGLE_PLACES_API_KEY` | Google Places API (New) key |
| `ANTHROPIC_API_KEY` | Anthropic API key for AI features |
| `SMTP_HOST` | SMTP host (e.g. `smtp.zoho.com`) |
| `SMTP_USERNAME` | SMTP sender address |
| `SMTP_PASSWORD` | SMTP password |
| `DB_NAME` | PostgreSQL database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `CLOUD_SQL_INSTANCE` | Cloud SQL connection name |
| `GCP_PROJECT_ID` | Google Cloud project ID |
| `FIREBASE_PROJECT_ID` | Firebase project ID |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase service account JSON (prod) |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production |

## Commands

```bash
./gradlew bootRun                                              # Start locally (port 8080)
./gradlew test                                                 # Run all tests
./gradlew test --tests "com.partyscout.unit.*"                 # Unit tests only
./gradlew test --tests "com.partyscout.integration.*"          # Integration tests only
./gradlew test --tests "com.partyscout.e2e.*"                  # E2E tests only
./gradlew build                                                # Full build + test
```

## Deployment

Deployments go through Cloud Build → Cloud Run canary (us-east1), then are promoted to prod (us-central1).

```bash
# Deploy to canary
COMMIT_SHA=$(git rev-parse HEAD)
gcloud builds submit --config cloudbuild.yaml --substitutions=COMMIT_SHA=$COMMIT_SHA

# Promote canary image to prod
IMAGE=$(gcloud run services describe partyscout-backend-canary --region us-east1 --format="value(spec.template.spec.containers[0].image)")
gcloud run deploy partyscout-backend --image $IMAGE --region us-central1 --quiet
```

Secrets are managed in GCP Secret Manager and injected at container startup.

## License

Private — All rights reserved
