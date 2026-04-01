# Contributing to PartyScout

Thank you for your interest in contributing to PartyScout! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Commit Messages](#commit-messages)
- [Testing](#testing)
- [Documentation](#documentation)

---

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Welcome newcomers and help them learn
- Accept constructive criticism gracefully
- Focus on what's best for the project
- Show empathy towards others

### Unacceptable Behavior

- Harassment or discrimination
- Trolling or insulting comments
- Publishing others' private information
- Other unprofessional conduct

---

## Getting Started

### Prerequisites

- **Backend**: JDK 17+, Gradle 8.5+
- **Frontend**: Node.js 20+, npm 10+
- **Tools**: Git, Docker (optional)

### Setting Up Development Environment

1. **Clone the repositories**:
   ```bash
   git clone https://github.com/GouriKA/partyScout-backend.git
   git clone https://github.com/GouriKA/partyScout-frontend.git
   ```

2. **Backend setup**:
   ```bash
   cd partyScout-backend
   export GOOGLE_PLACES_API_KEY=your_key_here
   export ANTHROPIC_API_KEY=your_key_here
   ./gradlew bootRun
   ```

3. **Frontend setup**:
   ```bash
   cd partyScout-frontend
   npm install
   npm run dev
   ```

4. **Verify setup**:
   - Backend: http://localhost:8080/api/v2/party-wizard/party-types/7
   - Frontend: http://localhost:5173

**Note**: Firebase and SMTP features are optional for local dev. The app falls back gracefully when these are not configured.

---

## Development Workflow

### Branch Naming

| Type | Format | Example |
|------|--------|---------|
| Feature | `feature/short-description` | `feature/add-favorites` |
| Bug fix | `fix/short-description` | `fix/venue-card-overflow` |
| Docs | `docs/short-description` | `docs/update-api-docs` |
| Refactor | `refactor/short-description` | `refactor/extract-service` |

### Workflow Steps

1. **Create a branch**:
   ```bash
   git checkout -b feature/my-feature
   ```

2. **Make changes** with small, focused commits

3. **Test your changes**:
   ```bash
   # Backend
   ./gradlew test

   # Frontend
   npm run lint
   npm run build
   npm run test:run
   ```

4. **Push and create PR**:
   ```bash
   git push origin feature/my-feature
   ```

---

## Pull Request Process

### Before Submitting

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Linting passes (no warnings)
- [ ] Documentation updated if needed
- [ ] Commit messages follow conventions
- [ ] Tests committed separately from code (see Testing Convention)

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe how you tested the changes

## Screenshots (if applicable)
Add screenshots for UI changes

## Checklist
- [ ] Self-reviewed code
- [ ] Added tests (separate commit)
- [ ] Updated documentation
```

### Review Process

1. Create PR against `main` branch
2. Request review from maintainers
3. Address feedback
4. Squash and merge when approved

---

## Coding Standards

### Backend (Kotlin)

**Style Guide**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

```kotlin
// Good: Clear naming, single responsibility
class VenueSearchService(
    private val placesClient: GooglePlacesService,
    private val scoreService: MatchScoreService
) {
    fun searchVenues(request: SearchRequest): List<Venue> {
        val places = placesClient.searchText(request.query, request.location, request.radius)
        return places.map { scoreService.scoreVenue(it, request) }
    }
}
```

**Best Practices**:
- Use `data class` for DTOs
- Prefer immutability (`val` over `var`)
- Keep functions small and focused
- All business logic in `service/` classes — controllers are thin

### Frontend (React/JavaScript)

```javascript
// Good: Functional component, clear props
function VenueCard({ venue, onSelect, isComparing }) {
  const handleClick = useCallback(() => onSelect(venue), [venue, onSelect]);
  return (
    <div className="venue-card" onClick={handleClick}>
      <h3>{venue.name}</h3>
      <p>{venue.address}</p>
    </div>
  );
}
```

**Best Practices**:
- Use functional components with hooks
- All API calls in context files — never fetch directly from components
- CSS co-located with each component (`.css` alongside `.jsx`)
- Unit tests in `__tests__/` subdirectory

### CSS

```css
/* Good: BEM naming, CSS variables */
.venue-card {
  padding: var(--spacing-md);
  border-radius: var(--radius);
}
```

---

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code change, no new feature or fix |
| `test` | Adding tests |
| `chore` | Maintenance tasks |

**Examples**:
```bash
feat(chat): add SSE streaming AI chat endpoint
fix(search): correct outdoor venue inference for amusement_park type
docs(api): add saved-events endpoints to API.md
test(chat): add unit tests for AnthropicService intent extraction
```

---

## Testing

### Testing Convention

**After every code commit, create a separate follow-up commit with tests.** Never bundle test changes in the same commit as feature/fix code.

### Backend Testing

```kotlin
@Test
fun `should return party types for age 7`() {
    val types = partyTypeService.getPartyTypesForAge(7)
    assertThat(types).isNotEmpty()
    assertThat(types).allMatch { it.minAge <= 7 && it.maxAge >= 7 }
}
```

```bash
./gradlew test
./gradlew test --tests "com.partyscout.unit.*"
./gradlew test --tests "com.partyscout.integration.*"
./gradlew test --tests "com.partyscout.e2e.*"
```

**Rules**:
- Use MockK for unit tests
- Never mock the database in integration tests — use real H2
- Use MockWebServer to stub Google Places and Anthropic APIs in integration tests

### Frontend Testing

```javascript
describe('VenueCard', () => {
  it('renders venue name', () => {
    render(<VenueCard venue={{ name: 'Sky Zone', address: '123 Main St' }} />);
    expect(screen.getByText('Sky Zone')).toBeInTheDocument();
  });
});
```

```bash
npm run test:run       # Vitest single run
npm run test:e2e       # Playwright headless
```

**E2E Rules**:
- Always call `setupApiMocks(page)` in `beforeEach` — never hit a real backend
- Wait for party-types API response before interacting with `PartyTypeSelector`

---

## Documentation

### When to Update Docs

- Adding new API endpoints → Update `docs/API.md`
- Changing architecture → Update `docs/ARCHITECTURE.md`
- Adding features → Update `CHANGELOG.md`
- Changing setup → Update `README.md` and `CONTRIBUTING.md`

---

## Questions?

- Open an issue for bugs or feature requests
- Email: scout@partyscout.live

Thank you for contributing!
