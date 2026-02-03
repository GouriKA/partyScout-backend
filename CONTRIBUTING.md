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

---

## Development Workflow

### Branch Naming

Use descriptive branch names:

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
- [ ] Added tests
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
    private val placesClient: GooglePlacesClient,
    private val scoreService: MatchScoreService
) {
    fun searchVenues(request: SearchRequest): List<Venue> {
        val places = placesClient.searchNearby(request.location)
        return places.map { scoreService.scoreVenue(it, request) }
    }
}

// Bad: Unclear naming, doing too much
class Helper {
    fun doStuff(x: Any): Any { ... }
}
```

**Best Practices**:
- Use `data class` for DTOs
- Prefer immutability (`val` over `var`)
- Use meaningful names
- Keep functions small and focused
- Add KDoc for public APIs

### Frontend (React/JavaScript)

**Style Guide**: Follow [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)

```javascript
// Good: Functional component, clear props
function VenueCard({ venue, onSelect, isComparing }) {
  const handleClick = useCallback(() => {
    onSelect(venue);
  }, [venue, onSelect]);

  return (
    <div className="venue-card" onClick={handleClick}>
      <h3>{venue.name}</h3>
      <p>{venue.address}</p>
    </div>
  );
}

// Bad: Class component, unclear props
class Card extends Component {
  render() {
    return <div onClick={() => this.props.cb(this.props.d)}>{this.props.d.n}</div>;
  }
}
```

**Best Practices**:
- Use functional components with hooks
- Destructure props
- Use meaningful component names
- Keep components small and focused
- Use CSS modules or BEM naming

### CSS

```css
/* Good: BEM naming, CSS variables */
.venue-card {
  padding: var(--spacing-md);
  border-radius: var(--radius-lg);
}

.venue-card__title {
  font-size: var(--font-lg);
}

.venue-card--highlighted {
  border-color: var(--primary);
}

/* Bad: Generic names, magic numbers */
.card {
  padding: 16px;
  border-radius: 8px;
}

.title {
  font-size: 18px;
}
```

---

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code change, no new feature or fix |
| `test` | Adding tests |
| `chore` | Maintenance tasks |

### Examples

```bash
# Good
feat(wizard): add step indicator component
fix(venue-card): correct price formatting for large numbers
docs(api): add examples for search endpoint
refactor(services): extract scoring logic to separate service

# Bad
update code
fix bug
WIP
```

---

## Testing

### Backend Testing

```kotlin
@Test
fun `should return party types for age 7`() {
    val types = partyTypeService.getPartyTypesForAge(7)

    assertThat(types).isNotEmpty()
    assertThat(types).allMatch { it.minAge <= 7 && it.maxAge >= 7 }
}

@Test
fun `should calculate match score correctly`() {
    val venue = createTestVenue(rating = 4.5, priceLevel = 2)
    val request = createTestRequest(budgetMax = 500)

    val score = matchScoreService.calculateScore(venue, request)

    assertThat(score).isBetween(0, 100)
}
```

**Run tests**:
```bash
./gradlew test
```

### Frontend Testing

```javascript
describe('VenueCard', () => {
  it('renders venue name', () => {
    const venue = { name: 'Sky Zone', address: '123 Main St' };
    render(<VenueCard venue={venue} />);

    expect(screen.getByText('Sky Zone')).toBeInTheDocument();
  });

  it('calls onSelect when clicked', () => {
    const onSelect = jest.fn();
    const venue = { id: '1', name: 'Sky Zone' };

    render(<VenueCard venue={venue} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole('button'));

    expect(onSelect).toHaveBeenCalledWith(venue);
  });
});
```

**Run tests**:
```bash
npm test
```

---

## Documentation

### When to Update Docs

- Adding new API endpoints → Update `docs/API.md`
- Changing architecture → Update `docs/ARCHITECTURE.md`
- Adding features → Update `docs/SPEC.md`
- Changing setup → Update `README.md`

### Documentation Style

- Use clear, concise language
- Include code examples
- Add diagrams for complex concepts
- Keep docs up to date with code

---

## Questions?

- Open an issue for bugs or feature requests
- Start a discussion for questions
- Email: [maintainer email]

Thank you for contributing! 🎉
