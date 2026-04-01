# PartyScout Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              GOOGLE CLOUD PLATFORM                          │
│                                                                             │
│  ┌──────────────────────────┐    ┌──────────────────────────┐              │
│  │  Cloud Run (us-east1)    │    │  Cloud Run (us-central1) │              │
│  │  ┌────────────────────┐  │    │  ┌────────────────────┐  │              │
│  │  │  Frontend (canary) │  │    │  │  Frontend (prod)   │  │              │
│  │  │  partyscout-       │  │    │  │  partyscout.app    │  │              │
│  │  │  frontend-canary   │  │    │  └────────────────────┘  │              │
│  │  └────────────────────┘  │    │  ┌────────────────────┐  │              │
│  │  ┌────────────────────┐  │    │  │  Backend (prod)    │  │              │
│  │  │  Backend (canary)  │  │    │  │  partyscout.app    │  │              │
│  │  │  partyscout-       │  │    │  │  /api              │  │              │
│  │  │  backend-canary    │  │    │  └────────┬───────────┘  │              │
│  │  └────────────────────┘  │    │           │              │              │
│  └──────────────────────────┘    └───────────┼──────────────┘              │
│                                              │                              │
│                              ┌───────────────▼──────────────┐              │
│                              │        Secret Manager         │              │
│                              │  GOOGLE_PLACES_API_KEY        │              │
│                              │  ANTHROPIC_API_KEY            │              │
│                              │  FIREBASE_SERVICE_ACCOUNT_JSON│              │
│                              │  SMTP_HOST / USERNAME / PW    │              │
│                              │  DB_NAME / DB_USERNAME / PW   │              │
│                              │  CLOUD_SQL_INSTANCE           │              │
│                              └──────────────────────────────┘              │
│                                                                             │
│                              ┌──────────────────────────────┐              │
│                              │   Cloud SQL (PostgreSQL)      │              │
│                              │   venues, searches,           │              │
│                              │   saved_events, users,        │              │
│                              │   outbox_events               │              │
│                              └──────────────────────────────┘              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                     │                              │
                     │ HTTPS                        │ HTTPS
                     ▼                              ▼
         ┌─────────────────────┐      ┌─────────────────────┐
         │   Google Places     │      │   Anthropic API     │
         │     API (New)       │      │   (Claude Haiku)    │
         └─────────────────────┘      └─────────────────────┘
                     │                              │
                     │ SMTP/TLS                     │ Firebase Admin
                     ▼                              ▼
         ┌─────────────────────┐      ┌─────────────────────┐
         │    Zoho SMTP        │      │   Firebase Auth     │
         │ smtp.zoho.com:587   │      │   (token verify)    │
         └─────────────────────┘      └─────────────────────┘
```

## Component Details

### Frontend (React)

**Technology Stack**:
- React 19 + Vite 7
- React Context + useReducer (three contexts: PartyPlanner, Auth, SavedEvents)
- Firebase Authentication (Google + email/password)
- nginx (production server)
- Poppins font via Google Fonts

**Directory Structure**:
```
partyScout-frontend/src/
├── App.jsx                          # Root — providers + routing
├── App.css                          # Global design system + CSS variables
├── context/
│   ├── PartyPlannerContext.jsx       # Wizard state, all API calls, venue search
│   ├── AuthContext.jsx               # Firebase auth state
│   └── SavedEventsContext.jsx        # Saved events state
├── components/
│   ├── landing/
│   │   ├── LandingPage.jsx           # Unified search bar + AI chat panel
│   │   └── LandingPage.css
│   ├── wizard/
│   │   ├── WizardContainer.jsx       # Step router
│   │   ├── StepIndicator.jsx         # Progress bar
│   │   ├── Step1_ChildInfo.jsx       # Name, age, party date
│   │   ├── Step2_Preferences.jsx     # Party type, guest count, budget
│   │   ├── Step3_Location.jsx        # City, indoor/outdoor, distance
│   │   ├── Step4_VenueResults.jsx    # Venue list with filter + sort chips
│   │   └── Step5_PartyDetails.jsx    # Selected venue details
│   ├── venue/
│   │   ├── VenueCard.jsx             # Venue card with weather, save, compare
│   │   ├── VenueCompare.jsx          # Side-by-side comparison (max 3)
│   │   └── VenueFilters.jsx          # Rating/price/setting filter selects
│   ├── savedevents/
│   │   ├── SavedEventsPage.jsx       # Saved events list
│   │   └── SaveModal.jsx             # Save event modal
│   └── common/
│       ├── Button.jsx
│       ├── Input.jsx
│       ├── FeedbackModal.jsx         # Feedback form (pre-filled from auth)
│       ├── PartyTypeSelector.jsx     # Multi-select dropdown (max 3)
│       └── Slider.jsx
```

**Design System** (`App.css`):
```css
--primary:  #F0287A   /* Pink — buttons, accents */
--navy:     #1e2d6b   /* Navy — headings, borders */
--bg:       #f8f6ff   /* Soft lavender background */
--text:     #1a1a2e   /* Dark text */
--radius:   12px
```

### Backend (Kotlin/Spring Boot)

**Technology Stack**:
- Kotlin 2.0 / JVM 17
- Spring Boot 3.3
- Spring WebFlux (WebClient for non-blocking HTTP to Google Places + Anthropic)
- Spring Security + Firebase Admin SDK (auth filter)
- Spring Data JPA + Flyway (PostgreSQL / H2)
- Jackson (JSON)
- ShedLock (distributed scheduler for OutboxPoller)

**Directory Structure**:
```
src/main/kotlin/com/partyscout/
├── auth/                        # Firebase auth filter + security config
├── chat/
│   ├── ChatController.kt        # POST /api/chat → SseEmitter
│   ├── AnthropicService.kt      # extractIntent() + streamResponse()
│   └── ChatModels.kt            # ChatRequest, ChatIntent, ChatMessage, KnownVenue
├── feedback/
│   ├── FeedbackController.kt
│   └── FeedbackService.kt       # Zoho SMTP; auto-reply from template file
├── llm/
│   └── LlmFilterService.kt      # Claude Haiku venue age/relevance filter
├── party/
│   ├── model/PartySearchModels.kt
│   └── service/                 # Budget, MatchScore, PartyDetails, PartyType
├── persona/
│   └── PersonaService.kt        # Age → persona + search query sets
├── persistence/
│   ├── entity/                  # Venue, Search, SavedEvent, OutboxEvent
│   ├── repository/              # Spring Data repositories
│   └── service/                 # SearchPersistence, VenueEnrichment
├── search/
│   └── controller/PartySearchController.kt
├── shared/
│   ├── config/                  # CORS, ShedLock
│   └── event/                   # Domain events, outbox pattern, Pub/Sub publisher
└── weather/                     # Weather forecast service
```

**Service Layer**:
```
POST /api/v2/party-wizard/search
    │
    ├── 1. PersonaService — derive persona + search queries from age
    ├── 2. GooglePlacesService — geocode city, run queries in parallel (Flux)
    ├── 3. VenueEnrichmentService — batch DB lookup for saved metadata
    ├── 4. LlmFilterService — Claude Haiku filters unsuitable venues
    ├── 5. MatchScoreService — multi-factor relevance scoring (0-100)
    ├── 6. BudgetEstimationService — estimated total + per-person cost
    ├── 7. PartyDetailsService — included items, add-ons, duration
    └── 8. Setting/distance filter + sort by matchScore

POST /api/chat  (SSE)
    │
    ├── 1. Claude Haiku — extractIntent() → structured JSON
    │         (city, age, occasion, indoor, themes, readyToSearch)
    ├── 2. buildSearchQueries() — outdoor or persona-based query set
    ├── 3. GooglePlacesService — parallel search, dedup, top 20
    └── 4. Claude Haiku — streamResponse() → text tokens + [VENUES] payload
```

## Venue Search: Indoor/Outdoor Strategy

Google Places API does not filter by indoor/outdoor. The backend infers setting from venue types and name:

```
inferSetting(types, name):
  outdoorTypes = {park, zoo, botanical_garden, campground, ...}
  if types contains exact outdoorType → "outdoor"
  if name contains outdoor keyword (farm, garden, beach, ...) → "outdoor"
  if name matches \bpark\b, \bfield\b, \byard\b (word boundary) → "outdoor"
  if types contains swimming_pool, or name has "pool"/"aquatic" → "both"
  else → "indoor"
```

Query strategy:
- `setting=outdoor` → 6 outdoor-specific queries (no "outdoor" prefix; Google ignores prefixes)
- Otherwise → persona queries + 3 outdoor queries always appended (ensures outdoor filter is never empty)

## Deployment Architecture

### CI/CD Pipeline

Push to `main` deploys to **canary only** (`us-east1`). Promotion to prod (`us-central1`) is always manual — prod always runs the exact canary image; it is never rebuilt.

```
GitHub push (main)
    → Cloud Build (auto) → Container Registry → Cloud Run canary (us-east1)

Manual promotion:
    gcloud run services describe partyscout-backend-canary ... → IMAGE
    gcloud run deploy partyscout-backend --image $IMAGE --region us-central1
```

**Cloud Run Services**:
| Service | Region | Purpose |
|---------|--------|---------|
| `partyscout-frontend-canary` | us-east1 | Staging |
| `partyscout-backend-canary` | us-east1 | Staging |
| `partyscout-frontend` | us-central1 | Production |
| `partyscout-backend` | us-central1 | Production |

**Domain**: `partyscout.app` — HTTPS load balancer routes `/api/*` → backend, `/*` → frontend.

### Environment Configuration

**Backend (injected from Secret Manager)**:
| Variable | Description |
|----------|-------------|
| `GOOGLE_PLACES_API_KEY` | Google Places API (New) key |
| `ANTHROPIC_API_KEY` | Anthropic API key for AI chat + LLM filter |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase Admin SDK credentials |
| `FIREBASE_PROJECT_ID` | Firebase project ID |
| `SMTP_HOST` | e.g. `smtp.zoho.com` |
| `SMTP_USERNAME` | SMTP sender address |
| `SMTP_PASSWORD` | SMTP password |
| `DB_NAME` | PostgreSQL database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `CLOUD_SQL_INSTANCE` | Cloud SQL connection name |
| `GCP_PROJECT_ID` | Google Cloud project ID |
| `SPRING_PROFILES_ACTIVE` | `prod` in production |

**Frontend (build-time)**:
| Variable | Description |
|----------|-------------|
| `VITE_API_URL` | Backend base URL |
| `VITE_FIREBASE_*` | Firebase config keys |

## Security

### Auth
- Firebase ID tokens verified by `FirebaseAuthFilter` on protected routes
- Saved-events endpoints require `Authorization: Bearer <token>`
- Public endpoints: venue search, party types, chat, weather, feedback

### API Key Protection
- All secrets in Google Secret Manager — injected at runtime, never in code
- Rotatable without redeployment

### CORS
```kotlin
allowedOriginPatterns = listOf(
    "http://localhost:5173",
    "http://localhost:3000",
    "https://*.run.app",
    "https://partyscout.app"
)
```

## Scalability

| Setting | Value |
|---------|-------|
| Min instances | 0 (scale to zero) |
| Max instances | 10 |
| Concurrency | 80 requests/instance |
| Memory | 512Mi (backend), 256Mi (frontend) |

## Monitoring

```bash
# Tail logs in real-time
gcloud run services logs tail partyscout-backend --region us-central1

# View recent logs (canary)
gcloud run services logs read partyscout-backend-canary --region us-east1 --limit 50

# Check service status
gcloud run services describe partyscout-backend --region us-central1
```

## Disaster Recovery

| Component | Recovery Strategy |
|-----------|-------------------|
| Frontend | Redeploy from Git |
| Backend | Redeploy from Git |
| Secrets | Rotate in Secret Manager |
| Database | Cloud SQL automated backups |
| Pub/Sub events | Outbox re-publishes on poller restart |
