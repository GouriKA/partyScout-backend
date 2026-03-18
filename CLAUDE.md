# PartyScout Backend — CLAUDE.md

## Project Overview

Kotlin + Spring Boot 3 REST API for the PartyScout birthday party planning app. Handles party type suggestions, venue search, budget estimation, party details, weather forecasts, and Firebase-based authentication.

## Tech Stack

- **Language**: Kotlin 2.0 / JVM
- **Framework**: Spring Boot 3.3
- **Build**: Gradle (Kotlin DSL)
- **Tests**: JUnit 5 + MockK + Spring Boot Test + MockWebServer

## Project Structure

```
src/
  main/kotlin/com/partyscout/
    auth/                        # Firebase auth filter, security config, user entity
    party/
      model/PartySearchModels.kt # Shared request/response models
      service/                   # BudgetEstimation, MatchScore, PartyDetails, PartyType
    persistence/
      entity/                    # JPA entities (Venue, Search, PartyType, OutboxEvent)
      repository/                # Spring Data repositories
      service/SearchPersistenceService.kt
    search/
      controller/PartySearchController.kt
    shared/
      config/                    # CORS, ShedLock
      event/                     # Domain events, outbox pattern, Pub/Sub publisher
  test/kotlin/com/partyscout/
    unit/                        # Unit tests with MockK
    integration/                 # Spring integration tests with MockWebServer
    e2e/                         # Full end-to-end API tests
```

## Commands

```bash
./gradlew bootRun          # Start server (default port 8080)
./gradlew test             # Run all tests
./gradlew test --tests "com.partyscout.unit.*"        # Unit tests only
./gradlew test --tests "com.partyscout.integration.*" # Integration tests only
./gradlew test --tests "com.partyscout.e2e.*"         # E2E tests only
./gradlew build            # Full build + test
```

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v2/party-wizard/party-types` | All party types |
| GET | `/api/v2/party-wizard/party-types/:age` | Age-filtered party types |
| POST | `/api/v2/party-wizard/estimate-budget` | Budget estimate |
| GET | `/api/v2/party-wizard/party-details` | Party type details |
| POST | `/api/v2/party-wizard/search` | Venue search |
| GET | `/api/v2/weather/forecast` | Weather forecast by ZIP + date |

## Key Conventions

- All business logic lives in `service/` classes — controllers are thin.
- Use MockK for unit tests; never mock the database in integration tests.
- Integration tests use `MockWebServer` to stub external APIs (Google Places, weather).
- E2E tests hit the full Spring context with a real (test) database.

## Testing Convention

After every code commit, create a separate follow-up commit with tests:
- **Unit tests**: `src/test/kotlin/com/partyscout/unit/`
- **Integration tests**: `src/test/kotlin/com/partyscout/integration/`
- **E2E tests**: `src/test/kotlin/com/partyscout/e2e/`

Never bundle test changes in the same commit as feature/fix code.
