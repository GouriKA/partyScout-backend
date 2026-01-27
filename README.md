# PartyScout Backend 🎉

Kotlin Spring Boot REST API for finding birthday party venues using Google Places API.

## Features

- 🎂 Age-based venue recommendations (kids, teens, adults)
- 📍 ZIP code to location geocoding
- 🔍 Real-time venue search using Google Places API (New/v1)
- ⭐ Venue ratings, reviews, and details
- 🗺️ Distance calculations from search location
- 💰 Price range estimates
- 🎪 Kid-friendly feature detection

## Tech Stack

- **Kotlin** 2.0.21
- **Spring Boot** 3.3.5
- **Spring WebFlux** for reactive HTTP client
- **Google Geocoding API**
- **Google Places API (New/v1)**
- **Gradle** with Kotlin DSL

## Prerequisites

- Java 17 or higher
- Google Cloud Platform account
- Google Places API key

## Setup

### 1. Get Google API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable these APIs:
   - **Geocoding API**
   - **Places API (New)**
4. Create an API key from Credentials section

### 2. Configure API Key

Update `src/main/resources/application.yml`:

```yaml
google:
  places:
    api-key: "YOUR_API_KEY_HERE"
```

Or use environment variable:
```bash
export GOOGLE_PLACES_API_KEY="your_api_key"
```

### 3. Build and Run

```bash
./gradlew bootRun
```

The API will start on `http://localhost:8080`

## API Endpoints

### POST /api/birthdays/search

Search for birthday party venues.

**Request:**
```json
{
  "age": 8,
  "areaCode": "94102",
  "time": "2026-02-07T14:00:00"
}
```

**Response:**
```json
{
  "venueOptions": [
    {
      "name": "Mission Dolores Park",
      "address": "Dolores St & 19th St, San Francisco, CA 94114, USA",
      "rating": 4.7,
      "distanceInMiles": 1.2,
      "priceRange": "$200-$500",
      "estimatedCapacity": 100,
      "description": "Park - Rated 4.7 stars with great amenities for celebrations",
      "phoneNumber": "+1 415-554-9521",
      "website": "https://sfrecpark.org/...",
      "kidFriendlyFeatures": {
        "isKidFriendly": true,
        "ageRange": "3-12",
        "hasPlayArea": true,
        "hasKidsMenu": false,
        "entertainmentOptions": ["Various entertainment options"],
        "safetyFeatures": ["Supervised area", "Safe environment"]
      }
    }
  ],
  "totalResults": 20,
  "searchParameters": {
    "age": 8,
    "areaCode": "94102",
    "time": "2026-02-07T14:00:00"
  }
}
```

### POST /api/v1/party-options

Simplified venue search endpoint.

**Request:**
```json
{
  "age": 25,
  "areaCode": "10001",
  "time": "2026-02-07T18:00:00"
}
```

## Project Structure

```
src/main/kotlin/com/partyscout/
├── PartyScoutApplication.kt       # Main application
├── config/
│   ├── CorsConfig.kt             # CORS configuration
│   ├── GooglePlacesConfig.kt     # API configuration
│   └── WebClientConfig.kt        # HTTP client setup
├── controller/
│   ├── BirthdayController.kt     # Birthday search endpoint
│   └── PartyOptionsController.kt # Party options endpoint
├── dto/
│   └── GooglePlacesDto.kt        # Google API response models
├── exception/
│   └── GlobalExceptionHandler.kt # Error handling
├── model/
│   ├── BirthdayModels.kt         # Domain models
│   ├── PartyOptionsRequest.kt
│   └── PartyOptionsResponse.kt
└── service/
    ├── GooglePlacesService.kt    # Google API client
    └── VenueSearchService.kt     # Business logic
```

## Age-Based Keywords

The system selects venue types based on age:

- **Kids (≤12)**: playground, amusement_park, bowling_alley
- **Teens (13-18)**: arcade, movie_theater, sports_complex
- **Adults (18+)**: restaurant, bar, banquet_hall

## CORS Configuration

CORS is configured to allow requests from:
- `http://localhost:5173` (Vite default)
- `http://localhost:3000` (Create React App default)

Update `config/CorsConfig.kt` to modify allowed origins.

## Building for Production

```bash
./gradlew clean build
```

The JAR file will be in `build/libs/`

Run the JAR:
```bash
java -jar build/libs/partyScout-0.0.1-SNAPSHOT.jar
```

## Environment Variables

- `SERVER_PORT` - Server port (default: 8080)
- `GOOGLE_PLACES_API_KEY` - Google Places API key

## Frontend

The frontend React app is in a separate repository: [partyScout-frontend](https://github.com/GouriKA/partyScout-frontend)

## License

MIT License
