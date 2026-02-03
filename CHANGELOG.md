# Changelog

All notable changes to PartyScout will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- User accounts and saved searches
- Direct venue booking integration
- Party checklist generator
- Mobile app

---

## [2.1.0] - 2026-01-29

### Changed
- Simplified party types from 12 specific categories to 6 broad categories
  - Active Play (trampoline, gymnastics, skating, swimming)
  - Creative (arts, crafts, cooking, science)
  - Amusement (arcade, movies, escape rooms, bowling)
  - Outdoor (parks, zoos, farms, adventure)
  - Characters & Performers (magicians, princesses, entertainers)
  - Social & Dining (restaurants, cafes, party rooms)

### Added
- Comprehensive documentation suite
  - Product specification (SPEC.md)
  - API documentation (API.md)
  - Architecture documentation (ARCHITECTURE.md)
  - Deployment guide (DEPLOYMENT.md)

---

## [2.0.0] - 2026-01-28

### Added
- **5-Step Party Planning Wizard**
  - Step 1: Child information (name, age, party date)
  - Step 2: Party preferences (type, guest count, budget)
  - Step 3: Location (ZIP code, indoor/outdoor, distance)
  - Step 4: Venue results with smart matching
  - Step 5: Party details with included items

- **Smart Venue Matching**
  - Match score algorithm (0-100 points)
  - Age appropriateness scoring
  - Budget fit calculation
  - Distance-based ranking

- **Venue Details**
  - "What's included" list
  - "What to bring" list
  - Suggested add-ons with pricing
  - Contact and booking information

- **Compare Mode**
  - Select up to 3 venues to compare side-by-side

- **New API Endpoints**
  - `POST /api/v2/party-wizard/search` - Search venues
  - `GET /api/v2/party-wizard/party-types/{age}` - Get party types
  - `POST /api/v2/party-wizard/estimate-budget` - Budget estimation

### Changed
- Complete frontend redesign with wizard-based UI
- New React Context-based state management
- Updated design system with CSS custom properties

### Technical
- Added Cloud Run deployment configuration
- Added GitHub Actions CI/CD workflows
- Integrated Google Secret Manager for API keys
- Added CORS support for Cloud Storage origins

---

## [1.0.0] - 2026-01-27

### Added
- Initial release
- Basic venue search by location
- Google Places API integration
- Simple search results display

---

## Version History Summary

| Version | Date | Highlights |
|---------|------|------------|
| 2.1.0 | 2026-01-29 | Simplified to 6 party types, added docs |
| 2.0.0 | 2026-01-28 | 5-step wizard, smart matching, compare mode |
| 1.0.0 | 2026-01-27 | Initial release |

---

[Unreleased]: https://github.com/GouriKA/partyScout-backend/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/GouriKA/partyScout-backend/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/GouriKA/partyScout-backend/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/GouriKA/partyScout-backend/releases/tag/v1.0.0
