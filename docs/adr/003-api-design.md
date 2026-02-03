# ADR 003: API Design

## Status
Accepted

## Date
2026-01-28

## Context

We need to design the API for the party planning wizard. Key requirements:
- Single search endpoint that accepts all criteria
- Return venues with match scores
- Support filtering and sorting on client
- Provide party type suggestions by age

## Decision

### API Style: REST with JSON

**Chosen**: RESTful API with JSON payloads

**Alternatives Considered**:
- GraphQL: Overkill for fixed data shapes
- gRPC: No browser client support needed

### Endpoint Design

```
POST /api/v2/party-wizard/search
GET  /api/v2/party-wizard/party-types/{age}
POST /api/v2/party-wizard/estimate-budget
```

### Version Strategy

Use URL versioning (`/api/v2/`) for:
- Clear version identification
- Easy routing
- Cache-friendly

### Search Endpoint

**POST** instead of GET because:
- Complex query parameters
- Request body is more readable
- Easier to extend

```json
{
  "age": 7,
  "partyTypes": ["active_play"],
  "guestCount": 15,
  "budgetMax": 500,
  "zipCode": "94105",
  "setting": "indoor",
  "maxDistanceMiles": 10
}
```

### Response Design

Include computed fields to reduce client logic:
- `matchScore`: Pre-calculated relevance score
- `matchReasons`: Human-readable explanations
- `estimatedTotal`: Calculated from guest count
- `includedItems`: Generated based on venue type

## Rationale

1. **Single round-trip**: All data in one request
2. **Server-side scoring**: Complex logic stays on backend
3. **Client flexibility**: Client can re-sort/filter without new requests
4. **Cacheable**: Same inputs = same outputs

## Consequences

### Positive
- Simple client implementation
- Easy to test
- Good performance (single request)
- Extensible

### Negative
- May return more data than needed
- No partial updates (full request each time)
- Server does all computation

### Future Considerations
- Add pagination if venue lists grow
- Consider caching responses
- Add rate limiting
