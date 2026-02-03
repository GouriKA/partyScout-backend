# PartyScout Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              GOOGLE CLOUD PLATFORM                          │
│                                                                             │
│  ┌─────────────────────┐         ┌─────────────────────┐                   │
│  │    Cloud Run        │         │    Cloud Run        │                   │
│  │  ┌───────────────┐  │         │  ┌───────────────┐  │                   │
│  │  │   Frontend    │  │  HTTP   │  │   Backend     │  │                   │
│  │  │   (React +    │──┼────────▶│  │   (Kotlin +   │  │                   │
│  │  │    nginx)     │  │         │  │  Spring Boot) │  │                   │
│  │  └───────────────┘  │         │  └───────┬───────┘  │                   │
│  │   Port 8080         │         │          │          │                   │
│  └─────────────────────┘         └──────────┼──────────┘                   │
│                                             │                               │
│                                             ▼                               │
│                                  ┌─────────────────────┐                   │
│                                  │   Secret Manager    │                   │
│                                  │  ┌───────────────┐  │                   │
│                                  │  │ google-places │  │                   │
│                                  │  │   -api-key    │  │                   │
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
│   │   │   ├── Step2_Preferences.jsx
│   │   │   ├── Step3_Location.jsx
│   │   │   ├── Step4_VenueResults.jsx
│   │   │   └── Step5_PartyDetails.jsx
│   │   ├── venue/              # Venue display components
│   │   │   ├── VenueCard.jsx
│   │   │   └── VenueCompare.jsx
│   │   └── common/             # Reusable UI components
│   │       ├── Button.jsx
│   │       ├── Input.jsx
│   │       └── Slider.jsx
│   └── main.jsx                # Entry point
├── Dockerfile
├── nginx.conf
└── cloudbuild.yaml
```

**State Management**:
```javascript
{
  currentStep: 1,
  childInfo: { name, age, partyDate },
  preferences: { partyTypes, guestCount, budget },
  location: { zipCode, setting, maxDistance },
  venues: [],
  selectedVenue: null,
  compareVenues: []
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
│   ├── config/
│   │   ├── CorsConfig.kt           # CORS settings
│   │   ├── WebClientConfig.kt      # HTTP client config
│   │   └── GooglePlacesConfig.kt   # API configuration
│   ├── controller/
│   │   └── PartySearchController.kt # REST endpoints
│   ├── model/
│   │   └── PartySearchModels.kt    # Data classes
│   └── service/
│       ├── PartyTypeService.kt     # Party type taxonomy
│       ├── MatchScoreService.kt    # Venue scoring
│       ├── BudgetEstimationService.kt
│       ├── PartyDetailsService.kt  # Included items
│       └── VenueSearchService.kt   # Google Places integration
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

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  GitHub  │───▶│  Cloud   │───▶│  Container │───▶│  Cloud   │
│   Push   │    │  Build   │    │  Registry  │    │   Run    │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
```

**Backend Deployment**:
1. Push to `main` branch
2. Cloud Build triggered
3. Docker image built with Gradle
4. Image pushed to GCR
5. Cloud Run deploys new revision

**Frontend Deployment**:
1. Push to `main` branch
2. Cloud Build triggered
3. Docker image built with Node + nginx
4. `VITE_API_URL` injected at build time
5. Image pushed to GCR
6. Cloud Run deploys new revision

### Environment Configuration

**Backend (Runtime)**:
| Variable | Source | Description |
|----------|--------|-------------|
| `GOOGLE_PLACES_API_KEY` | Secret Manager | API key |
| `PORT` | Cloud Run | Server port (8080) |

**Frontend (Build-time)**:
| Variable | Source | Description |
|----------|--------|-------------|
| `VITE_API_URL` | Build arg | Backend URL |

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
# View backend logs
gcloud run logs read partyscout-backend --region us-central1

# Real-time tail
gcloud run logs tail partyscout-backend --region us-central1
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
