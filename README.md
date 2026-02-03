# PartyScout Backend

Kotlin/Spring Boot API for the PartyScout birthday party planning application.

## Tech Stack

- **Language**: Kotlin 2.0.21
- **Framework**: Spring Boot 3.3.5
- **Build**: Gradle 8.5
- **Java**: 17
- **External API**: Google Places API (New)

## Quick Start

### Prerequisites

- JDK 17+
- Google Places API key

### Local Development

1. **Set environment variable**:
   ```bash
   export GOOGLE_PLACES_API_KEY=your_api_key_here
   ```

2. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```

3. **Test the API**:
   ```bash
   curl http://localhost:8080/api/v2/party-wizard/party-types/7
   ```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v2/party-wizard/search` | Search venues by criteria |
| GET | `/api/v2/party-wizard/party-types/{age}` | Get party types for age |
| POST | `/api/v2/party-wizard/estimate-budget` | Estimate party cost |

See [API Documentation](../docs/API.md) for details.

## Project Structure

```
src/main/kotlin/com/partyscout/
├── PartyScoutApplication.kt     # Entry point
├── config/
│   ├── CorsConfig.kt            # CORS settings
│   ├── WebClientConfig.kt       # HTTP client
│   └── GooglePlacesConfig.kt    # API config
├── controller/
│   └── PartySearchController.kt # REST endpoints
├── model/
│   └── PartySearchModels.kt     # Data classes
└── service/
    ├── PartyTypeService.kt      # Party taxonomy
    ├── MatchScoreService.kt     # Venue scoring
    ├── BudgetEstimationService.kt
    ├── PartyDetailsService.kt
    └── VenueSearchService.kt    # Google Places
```

## Party Types

| Code | Name | Ages |
|------|------|------|
| `active_play` | Active Play | 3-16 |
| `creative` | Creative | 4-14 |
| `amusement` | Amusement | 5-18 |
| `outdoor` | Outdoor | 3-16 |
| `characters_performers` | Characters & Performers | 2-10 |
| `social_dining` | Social & Dining | 1-18 |

## Configuration

### application.yml

```yaml
server:
  port: ${PORT:8080}

google:
  places:
    api-key: ${GOOGLE_PLACES_API_KEY:}
```

## Deployment

### Cloud Run

```bash
gcloud run deploy partyscout-backend \
  --source . \
  --region us-central1 \
  --set-secrets="GOOGLE_PLACES_API_KEY=google-places-api-key:latest"
```

### Docker

```bash
docker build -t partyscout-backend .
docker run -p 8080:8080 -e GOOGLE_PLACES_API_KEY=xxx partyscout-backend
```

## Testing

```bash
# Run tests
./gradlew test

# Build without tests
./gradlew build -x test
```

## License

Private - All rights reserved
