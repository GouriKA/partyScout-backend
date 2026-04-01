# ADR 003: API Design

## Status
Accepted (updated 2026-03-31)

## Date
2026-01-28

## Context

We need to design the API for the party planning wizard, AI chat, saved events, feedback, auth, and weather. Key requirements:
- Single search endpoint that accepts all criteria
- Return venues with match scores
- Support filtering and sorting on client
- Provide party type suggestions by age
- SSE streaming for AI chat
- JWT-authenticated endpoints for saved events
- Feedback submission with email notification

## Decision

### API Style: REST with JSON + SSE for streaming

**Chosen**: RESTful API with JSON payloads; Server-Sent Events for AI chat stream

**Alternatives Considered**:
- GraphQL: Overkill for fixed data shapes
- WebSockets for chat: SSE is simpler and sufficient for unidirectional streaming
- gRPC: No browser client support needed

### Endpoint Design

```
POST /api/v2/party-wizard/search        # Venue search (wizard + landing page)
GET  /api/v2/party-wizard/party-types   # All party types
GET  /api/v2/party-wizard/party-types/{age}  # Age-filtered suggestions
POST /api/v2/party-wizard/estimate-budget
GET  /api/v2/party-wizard/party-details

POST /api/chat                          # SSE streaming AI chat

GET  /api/v2/weather/forecast           # Weather forecast by ZIP + date

POST /api/v2/feedback                   # Submit feedback (triggers email)

POST /api/v2/auth/me                    # Firebase token → user record

GET  /api/v2/saved-events               # List saved events (auth required)
POST /api/v2/saved-events               # Save an event (auth required)
DELETE /api/v2/saved-events/{id}        # Delete saved event (auth required)
```

### Version Strategy

Use URL versioning (`/api/v2/`) for clear identification, easy routing, and cache-friendliness. Chat (`/api/chat`) is unversioned to match Anthropic API convention.

### Search Endpoint

**POST** instead of GET because:
- Complex query parameters including city name, setting, distance
- Request body is more readable and easier to extend

```json
{
  "age": 7,
  "partyTypes": ["active_play"],
  "guestCount": 15,
  "budgetMax": 500,
  "city": "Boston",
  "setting": "indoor",
  "maxDistanceMiles": 10
}
```

Note: `city` (string) replaced `zipCode` to support natural-language city input from the landing page and chat.

### Chat Endpoint

**POST /api/chat** returns `text/event-stream` (SSE):
- Each text token: `data: <token>\n\n`
- After text stream ends: `data: [VENUES]<json array>\n\n`
- Client accumulates tokens; renders venue cards when `[VENUES]` prefix detected

### Auth Strategy

Firebase ID tokens verified server-side via Firebase Admin SDK. Token passed as `Authorization: Bearer <token>`. Protected endpoints (`/api/v2/saved-events/**`) require a valid token via `FirebaseAuthFilter`.

### Response Design

Include computed fields to reduce client logic:
- `matchScore`: Pre-calculated relevance score (0-100)
- `matchReasons`: Human-readable explanations
- `estimatedTotal`: Calculated from guest count
- `includedItems`: Generated based on venue type
- `setting`: Inferred from Google Places types + venue name

## Rationale

1. **Single round-trip**: All data in one request for venue search
2. **Server-side scoring**: Complex LLM filter + match scoring stays on backend
3. **Client flexibility**: Client can re-sort/filter without new requests
4. **SSE for chat**: Simpler than WebSockets; sufficient for unidirectional AI stream

## Consequences

### Positive
- Simple client implementation
- Easy to test (REST + SSE both well-supported)
- Good performance (parallel Google Places queries)
- Auth on only the endpoints that need it

### Negative
- SSE requires special handling for reconnection
- No partial updates for venue search (full request each time)

### Future Considerations
- Add pagination if venue lists grow
- Rate limiting on search and chat endpoints
