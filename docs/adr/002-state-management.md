# ADR 002: Frontend State Management

## Status
Accepted (updated 2026-03-31)

## Date
2026-01-28

## Context

The PartyScout frontend needs state management for:
- Wizard step navigation (5 steps)
- Form data across steps
- API response data (venues, party types, budget estimates)
- Compare mode (up to 3 venues)
- Loading and error states
- Firebase authentication state (user, sign-in, sign-out)
- Saved events (authenticated users)
- AI chat conversation history and known venues

## Decision

**Chosen**: Multiple React Contexts with useReducer — one per domain

**Alternatives Considered**:
- Redux: Too heavyweight for this use case
- Zustand: Good, but Context is sufficient
- Jotai/Recoil: Atomic state not needed
- MobX: Overkill for form-based app
- Single mega-context: Causes unnecessary re-renders when unrelated state changes

## Implementation

Three separate contexts:

```javascript
// 1. Wizard + venue search state
const PartyPlannerContext = createContext(null);

const initialState = {
  currentStep: 1,
  childInfo: { name: '', age: null, partyDate: null },
  preferences: { partyTypes: [], guestCount: 15, budget: { min: 0, max: 500 } },
  location: { city: '', setting: 'any', maxDistance: 10 },
  venues: [],
  selectedVenue: null,
  compareVenues: [],
  partyTypeSuggestions: [],
  allPartyTypes: [],
  budgetEstimate: null,
  budgetEstimateLoading: false,
  partyDetails: null,
  loading: false,
  error: null
};

// 2. Firebase auth state
const AuthContext = createContext(null);
// Exposes: user, loading, signIn, signOut

// 3. Saved events state
const SavedEventsContext = createContext(null);
// Exposes: savedEvents, saveEvent, deleteEvent, loading
```

## Rationale

1. **Simplicity**: No additional dependencies
2. **Separation of concerns**: Auth, wizard, and saved events are independent domains
3. **Wizard pattern**: Linear flow with occasional back-navigation suits useReducer
4. **Predictable**: useReducer provides Redux-like predictability
5. **Performance**: Only 5 steps, no deep component trees

## Consequences

### Positive
- No additional dependencies
- Easy to understand and maintain
- Sufficient for current requirements
- Auth and saved events isolated from wizard re-renders

### Negative
- Manual optimization if needed (useMemo, useCallback)
- No built-in devtools (unlike Redux)
- Three providers to compose at the root

### When to Reconsider
- If complex caching requirements arise (e.g., SWR/React Query)
- If performance issues arise with deep trees
- If chat state grows complex enough to warrant its own context
