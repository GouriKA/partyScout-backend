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
│  │  │  partyscout-       │  │    │  │  partyscout.live   │  │              │
│  │  │  frontend-canary   │  │    │  └────────────────────┘  │              │
│  │  └────────────────────┘  │    │  ┌────────────────────┐  │              │
│  │  ┌────────────────────┐  │    │  │  Backend (prod)    │  │              │
│  │  │  Backend (canary)  │  │    │  │  partyscout.live   │  │              │
│  │  │  partyscout-       │  │    │  │  /api              │  │              │
│  │  │  backend-canary    │  │    │  └────────┬───────────┘  │              │
│  │  └────────────────────┘  │    │           │              │              │
│  └──────────────────────────┘    └───────────┼──────────────┘              │
│                                              │                              │
│                                              ▼                              │
│                                  ┌─────────────────────┐                   │
│                                  │   Secret Manager    │                   │
│                                  │  ┌───────────────┐  │                   │
│                                  │  │ google-places │  │                   │
│                                  │  │   -api-key    │  │                   │
│                                  │  ├───────────────┤  │                   │
│                                  │  │ firebase-     │  │                   │
│                                  │  │ service-acct  │  │                   │
│                                  │  └───────────────┘  │                   │
│                                  └─────────────────────┘                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              │ HTTPS
                                              ▼
                                  ┌─────────────────────┐
                                  │   Google Places     │
                                  │     API (New)       │
                                  └─────────────────────┘
```

## Component Details

### Frontend (React)

**Technology Stack**:
- React 19.2.0
- Vite 7.x (build tool)
- CSS custom properties (design system)
- nginx (production server)
- Firebase Auth (Google + email/password sign-in)
- Poppins font via Google Fonts

**Directory Structure**:
```
partyScout-frontend/
├── src/
│   ├── App.jsx                 # Main app component
│   ├── App.css                 # Global styles & design system
│   ├── context/
│   │   └── PartyPlannerContext.jsx  # State management
│   ├── components/
│   │   ├── wizard/             # 5-step wizard components
│   │   │   ├── WizardContainer.jsx
│   │   │   ├── StepIndicator.jsx
│   │   │   ├── Step1_ChildInfo.jsx
│   │   │   ├── Step2_Location.jsx
│   │   │   ├── Step3_Preferences.jsx
│   │   │   ├── Step4_VenueResults.jsx
│   │   │   └── Step5_PartyDetails.jsx
│   │   ├── venue/              # Venue display components
│   │   │   ├── VenueCard.jsx
│   │   │   └── VenueCompare.jsx
│   │   └── common/             # Reusable UI components
│   │       ├── Button.jsx
│   │       ├── Input.jsx
│   │       ├── Logo.jsx
│   │       ├── AuthModal.jsx
│   │       └── Slider.jsx
│   └── main.jsx                # Entry point
├── public/
│   └── logo.jpg
├── Dockerfile
├── nginx.conf
├── cloudbuild.yaml             # Canary deployment
└── cloudbuild-prod.yaml        # Prod promotion
```

**State Management**:
```javascript
{
  currentStep: 1,
  childInfo: { age, partyDate },
  preferences: { partyTypes[], guestCount, budget: { min, max } },
  location: { zipCode, setting, maxDistance, accessibility[] },
  venues: [],
  selectedVenue: null,
  compareVenues: [],
  partyTypeSuggestions: [],
  allPartyTypes: [],
  budgetEstimate: null,
  budgetEstimateLoading: false,
  partyDetails: null,
  weather: null,
  weatherLoading: false,
  loading: false,
  error: null
}
```

### Backend (Kotlin/Spring Boot)

**Technology Stack**:
- Kotlin 2.0.21
- Spring Boot 3.3.5
- Spring WebFlux (WebClient for HTTP)
- Jackson (JSON)

**Directory Structure**:
```
partyScout-backend/
├── src/main/kotlin/com/partyscout/
│   ├── PartyScoutApplication.kt    # Application entry
│   ├── auth/
│   │   ├── config/
│   │   │   ├── FirebaseConfig.kt
│   │   │   └── SecurityConfig.kt
│   │   ├── controller/
│   │   │   └── AuthController.kt
│   │   ├── entity/
│   │   │   └── UserEntity.kt
│   │   ├── filter/
│   │   │   └── FirebaseAuthFilter.kt
│   │   ├── repository/
│   │   │   └── UserRepository.kt
│   │   └── service/
│   │       └── UserService.kt
│   ├── party/
│   │   ├── model/
│   │   │   └── PartySearchModels.kt
│   │   └── service/
│   │       ├── BudgetEstimationService.kt
│   │       ├── MatchScoreService.kt
│   │       ├── PartyDetailsService.kt
│   │       └── PartyTypeService.kt
│   ├── persistence/
│   │   ├── entity/             # OutboxEvent, PartyType, Search, Venue
│   │   ├── repository/         # OutboxEvent, Search, Venue repositories
│   │   └── service/
│   │       └── SearchPersistenceService.kt
│   ├── search/
│   │   └── controller/
│   │       └── PartySearchController.kt
│   └── shared/
│       ├── config/             # CorsConfig, ShedLockConfig
│       └── event/              # DomainEvent, DomainEventPublisher, Events,
│                               # OutboxEventListener, OutboxPoller, PubSubEventPublisher
├── src/main/resources/
│   └── application.yml             # Configuration
├── build.gradle.kts
└── Dockerfile
```

**Service Layer**:
```
┌─────────────────────────────────────────────────────────────┐
│                    PartySearchController                     │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────────┐
│ PartyType     │  │ VenueSearch   │  │ BudgetEstimation  │
│ Service       │  │ Service       │  │ Service           │
└───────────────┘  └───────┬───────┘  └───────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ MatchScore    │  │ PartyDetails  │  │ Google Places │
│ Service       │  │ Service       │  │ API Client    │
└───────────────┘  └───────────────┘  └───────────────┘
```

## Data Flow

### Venue Search Flow

```
1. User fills wizard → Frontend collects data
                              │
                              ▼
2. POST /api/v2/party-wizard/search
                              │
                              ▼
3. PartySearchController receives request
                              │
                              ▼
4. PartyTypeService → Get search keywords for party types
                              │
                              ▼
5. VenueSearchService → Call Google Places API
   - searchNearby with location + radius
   - Filter by place types
                              │
                              ▼
6. For each venue:
   ├── MatchScoreService → Calculate 0-100 score
   ├── BudgetEstimationService → Estimate costs
   └── PartyDetailsService → Generate included/not included
                              │
                              ▼
7. Sort by matchScore, return to frontend
                              │
                              ▼
8. Frontend displays VenueCards with scores
```

## Deployment Architecture

### CI/CD Pipeline

Push to `main` deploys to **canary only** (`us-east1`). Promotion to prod (`us-central1`) requires a manual trigger.

```
┌──────────┐    ┌────────────────────────┐    ┌──────────────┐    ┌─────────────────┐
│  GitHub  │───▶│  Cloud Build (auto)    │───▶│  Container   │───▶│  Cloud Run      │
│  push    │    │  partyscout-frontend-  │    │  Registry    │    │  canary         │
│  main    │    │  canary /              │    │              │    │  (us-east1)     │
│          │    │  partyscout-backend-   │    │              │    │                 │
│          │    │  canary                │    │              │    │                 │
└──────────┘    └────────────────────────┘    └──────────────┘    └─────────────────┘

           Manual trigger required for prod promotion:
┌──────────────────────────────┐    ┌──────────────┐    ┌─────────────────┐
│  Cloud Build (manual)        │───▶│  Container   │───▶│  Cloud Run      │
│  partyscout-frontend-        │    │  Registry    │    │  prod           │
│  promote-prod /              │    │              │    │  (us-central1)  │
│  partyscout-backend-         │    │              │    │                 │
│  promote-prod                │    │              │    │                 │
└──────────────────────────────┘    └──────────────┘    └─────────────────┘
```

**Cloud Run Services**:
| Service | Region | URL |
|---------|--------|-----|
| `partyscout-frontend-canary` | us-east1 | `https://partyscout-frontend-canary-3f6x32ha2a-ue.a.run.app` |
| `partyscout-backend-canary` | us-east1 | `https://partyscout-backend-canary-3f6x32ha2a-ue.a.run.app` |
| `partyscout-frontend` | us-central1 | `https://partyscout.live` |
| `partyscout-backend` | us-central1 | `https://partyscout.live/api` |

**Cloud Build Triggers**:
| Trigger | Type | Purpose |
|---------|------|---------|
| `partyscout-frontend-canary` | Auto (push to main) | Deploy frontend to canary |
| `partyscout-backend-canary` | Auto (push to main) | Deploy backend to canary |
| `partyscout-frontend-promote-prod` | Manual | Promote frontend to prod |
| `partyscout-backend-promote-prod` | Manual | Promote backend to prod |

Config files: `cloudbuild.yaml` (canary), `cloudbuild-prod.yaml` (prod promotion).

**Backend Deployment (canary)**:
1. Push to `main` branch
2. Cloud Build triggered automatically
3. Docker image built with Gradle
4. Image pushed to GCR
5. Cloud Run deploys new revision to canary (`us-east1`)

**Frontend Deployment (canary)**:
1. Push to `main` branch
2. Cloud Build triggered automatically
3. Docker image built with Node + nginx
4. `VITE_API_URL` and Firebase env vars injected at build time
5. Image pushed to GCR
6. Cloud Run deploys new revision to canary (`us-east1`)

### Environment Configuration

**Backend (Runtime — from Secret Manager)**:
| Variable | Source | Description |
|----------|--------|-------------|
| `GOOGLE_PLACES_API_KEY` | Secret Manager | Google Places API key |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Secret Manager | Firebase Admin SDK credentials |
| `PORT` | Cloud Run | Server port (8080) |

**Frontend (Build-time)**:
| Variable | Source | Description |
|----------|--------|-------------|
| `VITE_API_URL` | Build arg | Backend URL |
| `VITE_FIREBASE_API_KEY` | Build arg | Firebase project API key |
| `VITE_FIREBASE_AUTH_DOMAIN` | Build arg | Firebase auth domain |
| `VITE_FIREBASE_PROJECT_ID` | Build arg | Firebase project ID |
| `VITE_FIREBASE_STORAGE_BUCKET` | Build arg | Firebase storage bucket |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | Build arg | Firebase messaging sender ID |
| `VITE_FIREBASE_APP_ID` | Build arg | Firebase app ID |

## Security

### API Key Protection
- Stored in Google Secret Manager
- Injected at runtime, never in code
- Rotatable without redeployment

### CORS Policy
```kotlin
allowedOriginPatterns = listOf(
    "http://localhost:5173",
    "http://localhost:3000",
    "https://*.run.app",
    "https://storage.googleapis.com"
)
```

### Network Security
- HTTPS only (enforced by Cloud Run)
- No direct database access
- Stateless design (no sessions)

## Scalability

### Cloud Run Auto-scaling

| Setting | Value |
|---------|-------|
| Min instances | 0 (scale to zero) |
| Max instances | 10 |
| Concurrency | 80 requests/instance |
| CPU | 1 vCPU |
| Memory | 512Mi (backend), 256Mi (frontend) |

### Performance Optimizations

**Frontend**:
- Static assets cached 1 year
- Gzip compression
- Code splitting by route

**Backend**:
- WebClient connection pooling
- JVM container optimizations
- Response caching (future)

## Monitoring

### Logs
```bash
# View backend logs (prod)
gcloud run services logs read partyscout-backend --region us-central1

# View backend logs (canary)
gcloud run services logs read partyscout-backend-canary --region us-east1

# Real-time tail
gcloud run services logs tail partyscout-backend --region us-central1
```

### Metrics (Cloud Run Dashboard)
- Request count
- Request latency
- Container instance count
- Memory utilization
- CPU utilization

## Disaster Recovery

| Component | Recovery Strategy |
|-----------|-------------------|
| Frontend | Redeploy from Git |
| Backend | Redeploy from Git |
| API Key | Rotate in Secret Manager |
| Data | No persistent data stored |
