# ADR 004: Party Type Taxonomy

## Status
Accepted (Revised)

## Date
2026-01-29 (Revised from 2026-01-28)

## Context

We need to categorize party venues into types that:
- Are meaningful to parents
- Map to searchable Google Places categories
- Cover the full range of birthday party options
- Are simple enough for quick selection

## Decision

### Version 1 (Superseded)
12 specific party types:
- toddler_play, character_party, bounce_house, arcade, sports, arts_crafts, outdoor, escape_room, movies, pool_party, go_karts, adventure_park

### Version 2 (Current)
6 broad party types:

| Type | Display Name | Age Range | Includes |
|------|--------------|-----------|----------|
| `active_play` | Active Play | 3-16 | Trampoline, gymnastics, skating, swimming, ninja courses |
| `creative` | Creative | 4-14 | Art studios, pottery, cooking, science, crafts |
| `amusement` | Amusement | 5-18 | Arcade, movies, escape rooms, bowling, laser tag, go-karts |
| `outdoor` | Outdoor | 3-16 | Parks, zoos, farms, adventure parks, mini golf |
| `characters_performers` | Characters & Performers | 2-10 | Magicians, princesses, superheroes, clowns |
| `social_dining` | Social & Dining | 1-18 | Restaurants, cafes, pizza parties |

## Rationale for Change

### Why 6 instead of 12?

1. **Parent mental model**: Parents think in broad categories, not specific venues
2. **Simpler UI**: Fewer options = faster decisions
3. **Better search**: Broader keywords = more results
4. **Age overlap**: Many specific types serve similar ages

### Why these 6?

1. **Active Play**: Covers the huge "burn energy" category
2. **Creative**: Clear differentiation for arts-focused families
3. **Amusement**: Classic party venue category
4. **Outdoor**: Important for seasonal/weather considerations
5. **Characters & Performers**: Unique category (entertainers, not venues)
6. **Social & Dining**: Covers casual, food-focused parties

### Naming Decision: "Entertainment" → "Amusement"

Original name "Entertainment" was too similar to "Characters & Performers" (which is also entertainment). "Amusement" clearly refers to arcade/gaming venues.

## Consequences

### Positive
- Simpler user experience
- More search results per category
- Easier to maintain
- Clearer mental model

### Negative
- Less precision in matching
- May need sub-categories later
- Some edge cases unclear (e.g., museum = creative or amusement?)

## Migration

- Old types removed from taxonomy
- No database migration needed (stateless)
- Frontend automatically uses new types from API
