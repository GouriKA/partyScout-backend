# ADR 002: Frontend State Management

## Status
Accepted

## Date
2026-01-28

## Context

The PartyScout frontend needs state management for:
- Wizard step navigation (5 steps)
- Form data across steps
- API response data (venues)
- Compare mode (up to 3 venues)
- Loading and error states

## Decision

**Chosen**: React Context API with useReducer

**Alternatives Considered**:
- Redux: Too heavyweight for this use case
- Zustand: Good, but Context is sufficient
- Jotai/Recoil: Atomic state not needed
- MobX: Overkill for form-based app

## Implementation

```javascript
// Single context with reducer pattern
const PartyPlannerContext = createContext(null);

const initialState = {
  currentStep: 1,
  childInfo: { name: '', age: null, partyDate: null },
  preferences: { partyTypes: [], guestCount: 15, budget: { min: 0, max: 500 } },
  location: { zipCode: '', setting: 'any', maxDistance: 10 },
  venues: [],
  selectedVenue: null,
  compareVenues: [],
  loading: false,
  error: null
};

function reducer(state, action) {
  switch (action.type) {
    case 'SET_STEP': return { ...state, currentStep: action.payload };
    case 'UPDATE_CHILD_INFO': return { ...state, childInfo: { ...state.childInfo, ...action.payload } };
    // ... other actions
  }
}
```

## Rationale

1. **Simplicity**: No additional dependencies
2. **Wizard pattern**: Linear flow with occasional back-navigation
3. **Predictable**: useReducer provides Redux-like predictability
4. **Performance**: Only 5 steps, no deep component trees
5. **Debugging**: Easy to log actions and state

## Consequences

### Positive
- No additional dependencies
- Easy to understand and maintain
- Sufficient for current requirements
- Good performance for this use case

### Negative
- Manual optimization if needed (useMemo, useCallback)
- No built-in devtools (unlike Redux)
- May need migration if app grows significantly

### When to Reconsider
- If we add user accounts (persisted state)
- If we add complex caching requirements
- If performance issues arise with deep trees
