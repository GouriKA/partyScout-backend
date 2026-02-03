# PartyScout API Documentation

## Base URL

**Production**: `https://partyscout-backend-869352526308.us-central1.run.app`
**Local Development**: `http://localhost:8080`

## Authentication

Currently, the API is public and does not require authentication.

---

## Endpoints

### 1. Search Party Venues

Search for venues based on party criteria.

**Endpoint**: `POST /api/v2/party-wizard/search`

**Request Body**:
```json
{
  "age": 7,
  "partyTypes": ["active_play", "amusement"],
  "guestCount": 15,
  "budgetMin": 100,
  "budgetMax": 500,
  "zipCode": "94105",
  "setting": "indoor",
  "maxDistanceMiles": 10,
  "date": "2026-03-15T14:00:00"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `age` | integer | Yes | Child's age (1-18) |
| `partyTypes` | string[] | Yes | List of party type codes |
| `guestCount` | integer | Yes | Number of guests expected |
| `budgetMin` | integer | No | Minimum budget in USD |
| `budgetMax` | integer | No | Maximum budget in USD |
| `zipCode` | string | Yes | ZIP code for location search |
| `setting` | string | No | `indoor`, `outdoor`, or `any` (default: `any`) |
| `maxDistanceMiles` | integer | No | Maximum travel distance (default: 10) |
| `date` | string | No | Party date in ISO 8601 format |

**Response**:
```json
{
  "venues": [
    {
      "id": "ChIJ...",
      "name": "Sky Zone Trampoline Park",
      "address": "123 Jump St, San Francisco, CA 94105",
      "phoneNumber": "(415) 555-0123",
      "website": "https://skyzone.com",
      "rating": 4.5,
      "reviewCount": 234,
      "priceLevel": 2,
      "setting": "indoor",
      "distanceInMiles": 2.3,
      "matchScore": 87,
      "matchReasons": [
        "Great for ages 5-12",
        "Within your budget",
        "Highly rated (4.5 stars)"
      ],
      "estimatedTotal": 350,
      "estimatedPricePerPerson": 23,
      "includedItems": [
        "2 hours jump time",
        "Private party room",
        "Party host",
        "Grip socks for all guests"
      ],
      "notIncluded": [
        "Food and beverages",
        "Cake",
        "Decorations",
        "Goody bags"
      ],
      "suggestedAddOns": [
        {
          "name": "Pizza Package",
          "description": "2 pizzas + drinks for all guests",
          "estimatedCost": 75,
          "isRecommended": true
        }
      ],
      "popularForAges": "Best for ages 5-12",
      "typicalPartyDuration": "2 hours"
    }
  ],
  "searchCriteria": {
    "location": "San Francisco, CA",
    "radius": 10,
    "partyTypes": ["active_play"],
    "totalResults": 12
  },
  "partyTypeSuggestions": []
}
```

**Response Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `venues` | Venue[] | List of matching venues |
| `searchCriteria` | object | Echo of search parameters |
| `partyTypeSuggestions` | Suggestion[] | Additional party type recommendations |

**Venue Object**:

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Google Places ID |
| `name` | string | Venue name |
| `address` | string | Full address |
| `phoneNumber` | string | Contact phone |
| `website` | string | Venue website URL |
| `rating` | number | Google rating (0-5) |
| `reviewCount` | integer | Number of Google reviews |
| `priceLevel` | integer | Price level (1-4) |
| `setting` | string | `indoor` or `outdoor` |
| `distanceInMiles` | number | Distance from ZIP code |
| `matchScore` | integer | Match score (0-100) |
| `matchReasons` | string[] | Why this venue matches |
| `estimatedTotal` | integer | Estimated party cost |
| `estimatedPricePerPerson` | integer | Per-person cost |
| `includedItems` | string[] | What's included |
| `notIncluded` | string[] | What to bring |
| `suggestedAddOns` | AddOn[] | Optional upgrades |
| `popularForAges` | string | Age recommendation |
| `typicalPartyDuration` | string | Typical party length |

---

### 2. Get Party Types for Age

Get party type suggestions appropriate for a specific age.

**Endpoint**: `GET /api/v2/party-wizard/party-types/{age}`

**Path Parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `age` | integer | Child's age (1-18) |

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
  },
  {
    "type": "creative",
    "displayName": "Creative",
    "description": "Arts, crafts, cooking, and hands-on activities",
    "icon": "palette",
    "ageRange": "Ages 4-14",
    "averageCost": "$250-500",
    "popularityScore": 4
  }
]
```

**Party Type Suggestion Fields**:

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Party type code |
| `displayName` | string | Human-readable name |
| `description` | string | Brief description |
| `icon` | string | Icon identifier |
| `ageRange` | string | Suitable age range |
| `averageCost` | string | Typical cost range |
| `popularityScore` | integer | Popularity for this age (1-5) |

---

### 3. Estimate Budget

Get a budget estimate for a party configuration.

**Endpoint**: `POST /api/v2/party-wizard/estimate-budget`

**Request Body**:
```json
{
  "partyType": "active_play",
  "guestCount": 15,
  "priceLevel": 2
}
```

**Response**:
```json
{
  "estimatedTotal": 350,
  "estimatedPerPerson": 23,
  "breakdown": {
    "venueBase": 200,
    "perGuestCost": 150,
    "typicalAddOns": 75
  }
}
```

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

### 400 Bad Request
```json
{
  "error": "Bad Request",
  "message": "Invalid ZIP code format",
  "status": 400
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "No venues found matching criteria",
  "status": 404
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "Failed to fetch venues from Google Places",
  "status": 500
}
```

---

## Rate Limits

Currently no rate limits are enforced. Future versions may implement:
- 100 requests per minute per IP
- 1000 requests per day per IP

---

## CORS

The API allows requests from:
- `http://localhost:5173` (local development)
- `http://localhost:3000` (local development)
- `https://*.run.app` (Cloud Run services)
- `https://storage.googleapis.com` (Cloud Storage)

---

## Examples

### cURL - Search Venues

```bash
curl -X POST https://partyscout-backend-869352526308.us-central1.run.app/api/v2/party-wizard/search \
  -H "Content-Type: application/json" \
  -d '{
    "age": 7,
    "partyTypes": ["active_play"],
    "guestCount": 15,
    "budgetMax": 500,
    "zipCode": "94105",
    "setting": "indoor",
    "maxDistanceMiles": 10
  }'
```

### cURL - Get Party Types

```bash
curl https://partyscout-backend-869352526308.us-central1.run.app/api/v2/party-wizard/party-types/7
```

### JavaScript Fetch

```javascript
const response = await fetch('/api/v2/party-wizard/search', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    age: 7,
    partyTypes: ['active_play'],
    guestCount: 15,
    zipCode: '94105'
  })
});

const data = await response.json();
console.log(data.venues);
```

---

## Changelog

### v2.0.0 (2026-01-29)
- Simplified party types from 12 to 6 broad categories
- Added match score algorithm
- Added "what's included" and "what to bring" fields
- Added suggested add-ons

### v1.0.0 (2026-01-28)
- Initial release with basic venue search
