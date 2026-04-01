# PartyScout API Documentation

## Base URL

**Production**: `https://partyscout.app`
**Canary**: `https://partyscout-backend-canary-*.a.run.app`
**Local Development**: `http://localhost:8080`

## Authentication

Most endpoints are public. Saved-events endpoints require a Firebase ID token:

```
Authorization: Bearer <firebase-id-token>
```

---

## Endpoints

### Party Wizard

#### Search Venues

**Endpoint**: `POST /api/v2/party-wizard/search`

**Request Body**:
```json
{
  "age": 7,
  "partyTypes": ["active_play", "amusement"],
  "guestCount": 15,
  "budgetMin": 100,
  "budgetMax": 500,
  "city": "Boston",
  "setting": "indoor",
  "maxDistanceMiles": 10,
  "date": "2026-06-15T14:00:00",
  "textQuery": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `age` | integer | No | Child's age (used for persona/query set) |
| `partyTypes` | string[] | No | Party type codes |
| `guestCount` | integer | No | Number of guests |
| `budgetMin` | integer | No | Minimum budget in USD |
| `budgetMax` | integer | No | Maximum budget in USD |
| `city` | string | Yes | City name (e.g. "Boston, MA") |
| `setting` | string | No | `indoor`, `outdoor`, or `any` (default: `any`) |
| `maxDistanceMiles` | integer | No | Max travel distance (default: 10) |
| `date` | string | No | Party date ISO 8601 |
| `textQuery` | string | No | Free-text search query (landing page) |

**Response**:
```json
{
  "venues": [
    {
      "id": "ChIJ...",
      "googlePlaceId": "ChIJ...",
      "name": "Sky Zone Trampoline Park",
      "address": "123 Jump St, Boston, MA 02101",
      "phoneNumber": "(617) 555-0123",
      "website": "https://skyzone.com",
      "googleMapsUri": "https://maps.google.com/?cid=...",
      "rating": 4.5,
      "reviewCount": 234,
      "priceLevel": 2,
      "setting": "indoor",
      "distanceInMiles": 2.3,
      "matchScore": 87,
      "matchReasons": ["Great for ages 5-12", "Within your budget", "Highly rated (4.5 stars)"],
      "estimatedTotal": 350,
      "estimatedPricePerPerson": 23,
      "includedItems": ["2 hours jump time", "Private party room", "Party host"],
      "notIncluded": ["Food and beverages", "Cake", "Decorations"],
      "suggestedAddOns": [
        { "name": "Pizza Package", "description": "2 pizzas + drinks", "estimatedCost": 75, "isRecommended": true }
      ],
      "popularForAges": "Best for ages 5-12",
      "typicalPartyDuration": "2 hours",
      "photos": ["https://places.googleapis.com/v1/.../media?key=...&maxWidthPx=800"]
    }
  ],
  "searchCriteria": {
    "location": "Boston, MA",
    "radius": 10,
    "partyTypes": ["active_play"],
    "totalResults": 12
  }
}
```

---

#### Get Party Types (age-filtered)

**Endpoint**: `GET /api/v2/party-wizard/party-types/{age}`

**Example**: `GET /api/v2/party-wizard/party-types/7`

**Response**:
```json
[
  {
    "type": "active_play",
    "displayName": "Active Play",
    "description": "Jump, run, and burn energy with physical fun",
    "icon": "rocket",
    "ageRange": "Ages 3-16",
    "averageCost": "$200-450",
    "popularityScore": 5
  }
]
```

---

#### Get All Party Types

**Endpoint**: `GET /api/v2/party-wizard/party-types`

Returns all 6 party types regardless of age.

---

#### Estimate Budget

**Endpoint**: `POST /api/v2/party-wizard/estimate-budget`

**Request Body**:
```json
{ "partyType": "active_play", "guestCount": 15, "priceLevel": 2 }
```

**Response**:
```json
{
  "estimatedTotal": 350,
  "estimatedPerPerson": 23,
  "breakdown": { "venueBase": 200, "perGuestCost": 150, "typicalAddOns": 75 }
}
```

---

#### Get Party Details

**Endpoint**: `GET /api/v2/party-wizard/party-details`

**Query Parameters**: `types=active_play,amusement`

Returns venue category details including included items, what to bring, and suggested add-ons.

---

### AI Chat

**Endpoint**: `POST /api/chat`

Returns `text/event-stream` (SSE).

**Request Body**:
```json
{
  "message": "Looking for an outdoor birthday party for my 8-year-old in Boston",
  "conversationHistory": [
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." }
  ],
  "existingContext": { "city": null, "persona": null, "occasion": null },
  "knownVenues": []
}
```

**SSE Events**:
```
data: Here's what I found:\n\n
data:  Three\n\n
data:  solid\n\n
data:  options.\n\n
data: [VENUES][{"id":"ChIJ...","name":"...","address":"...","rating":4.5,...}]\n\n
```

Text tokens stream individually. The final event (if venues found) is prefixed with `[VENUES]` and contains a JSON array of up to 3 venues.

---

### Weather

**Endpoint**: `GET /api/v2/weather/forecast`

**Query Parameters**: `zip=02101&date=2026-06-15`

**Response**: Weather forecast object for the given ZIP + date.

---

### Feedback

**Endpoint**: `POST /api/v2/feedback`

**Request Body**:
```json
{
  "name": "Jane Smith",
  "email": "jane@example.com",
  "message": "Love the app! Could use more outdoor venues.",
  "rating": 5
}
```

Sends a notification email to `scout@partyscout.live` and an auto-reply to the submitter.

**Response**: `200 OK`

---

### Authentication

**Endpoint**: `POST /api/v2/auth/me`

Verifies a Firebase ID token and upserts the user record.

**Headers**: `Authorization: Bearer <firebase-id-token>`

**Response**:
```json
{ "uid": "firebase-uid", "email": "user@example.com", "displayName": "Jane Smith" }
```

---

### Saved Events

All saved-events endpoints require `Authorization: Bearer <firebase-id-token>`.

#### List Saved Events

**Endpoint**: `GET /api/v2/saved-events`

**Response**:
```json
[
  {
    "id": 1,
    "googlePlaceId": "ChIJ...",
    "venueName": "Sky Zone",
    "venueAddress": "123 Jump St, Boston",
    "eventDate": "2026-06-15",
    "guestCount": 15,
    "partyTypes": ["active_play"],
    "savedAt": "2026-03-15T10:30:00Z"
  }
]
```

#### Save an Event

**Endpoint**: `POST /api/v2/saved-events`

**Request Body**:
```json
{
  "googlePlaceId": "ChIJ...",
  "venueName": "Sky Zone",
  "venueAddress": "123 Jump St, Boston",
  "eventDate": "2026-06-15",
  "guestCount": 15,
  "partyTypes": ["active_play"]
}
```

**Response**: `201 Created` with the saved event object.

#### Delete a Saved Event

**Endpoint**: `DELETE /api/v2/saved-events/{id}`

**Response**: `204 No Content`

---

## Party Type Codes

| Code | Display Name | Description |
|------|--------------|-------------|
| `active_play` | Active Play | Trampoline, gymnastics, skating, swimming |
| `creative` | Creative | Arts, crafts, cooking, science |
| `amusement` | Amusement | Arcade, movies, escape rooms, bowling |
| `outdoor` | Outdoor | Parks, zoos, farms, adventure |
| `characters_performers` | Characters & Performers | Magicians, princesses, entertainers |
| `social_dining` | Social & Dining | Restaurants, cafes, party rooms |

---

## Error Responses

```json
{ "error": "Bad Request", "message": "city is required", "status": 400 }
{ "error": "Unauthorized", "message": "Invalid or missing token", "status": 401 }
{ "error": "Not Found", "message": "Saved event not found", "status": 404 }
{ "error": "Internal Server Error", "message": "Failed to fetch venues", "status": 500 }
```

---

## CORS

Requests allowed from:
- `http://localhost:5173` / `http://localhost:3000` (local dev)
- `https://*.run.app` (Cloud Run)
- `https://partyscout.app` (production)

---

## Examples

### cURL — Search Venues

```bash
curl -X POST https://partyscout.app/api/v2/party-wizard/search \
  -H "Content-Type: application/json" \
  -d '{
    "age": 7,
    "partyTypes": ["active_play"],
    "guestCount": 15,
    "budgetMax": 500,
    "city": "Boston",
    "setting": "indoor",
    "maxDistanceMiles": 10
  }'
```

### cURL — AI Chat (SSE)

```bash
curl -X POST https://partyscout.app/api/chat \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "message": "outdoor birthday for 8 year old in Boston",
    "conversationHistory": [],
    "existingContext": {},
    "knownVenues": []
  }'
```

### JavaScript — Saved Events

```javascript
const token = await firebase.auth().currentUser.getIdToken();

const res = await fetch('/api/v2/saved-events', {
  headers: { 'Authorization': `Bearer ${token}` }
});
const events = await res.json();
```

---

## Changelog

### v3.0.0 (2026-03-31)
- Added `POST /api/chat` — SSE streaming AI chat
- Added `POST /api/v2/feedback`
- Added `POST /api/v2/auth/me`
- Added `GET/POST/DELETE /api/v2/saved-events`
- Changed `zipCode` → `city` in search request
- Added `textQuery` field for landing page free-text search

### v2.0.0 (2026-01-29)
- Simplified party types from 12 to 6 broad categories
- Added match score algorithm
- Added "what's included" and "what to bring" fields

### v1.0.0 (2026-01-28)
- Initial release with basic venue search
