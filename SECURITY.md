# Security Policy

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please report it responsibly.

### How to Report

**DO NOT** open a public GitHub issue for security vulnerabilities.

Instead, please email: scout@partyscout.live

Or use GitHub's private vulnerability reporting:
1. Go to the repository's Security tab
2. Click "Report a vulnerability"
3. Fill out the form with details

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response Timeline

| Action | Timeline |
|--------|----------|
| Acknowledgment | 48 hours |
| Initial assessment | 1 week |
| Fix development | 2-4 weeks |
| Public disclosure | After fix deployed |

---

## Security Measures

### Data Handling

| Data Type | Storage | Retention |
|-----------|---------|-----------|
| Search queries | Persisted for analytics | 90 days |
| Firebase UID + email | Cloud SQL (users table) | Until account deleted |
| Saved events | Cloud SQL | Until user deletes |
| API keys | Secret Manager | Rotated quarterly |
| Logs | Cloud Logging | 30 days |

**We do not**:
- Store payment information
- Track users across sessions beyond their own saved events
- Share personal data with third parties
- Use cookies for tracking

### API Security

| Measure | Implementation |
|---------|----------------|
| HTTPS | Enforced by Cloud Run + load balancer |
| API Keys | Stored in Google Secret Manager |
| Auth | Firebase ID token verification (FirebaseAuthFilter) |
| CORS | Restricted to known origins |
| Input Validation | Server-side validation on all endpoints |

### Infrastructure Security

| Layer | Protection |
|-------|------------|
| Compute | Cloud Run (managed, isolated containers) |
| Secrets | Google Secret Manager (encrypted at rest) |
| Database | Cloud SQL with private IP; Cloud SQL Connector |
| Auth | Firebase Admin SDK token verification |
| Network | HTTPS only; no direct DB access from internet |

---

## Secure Development

### Code Review Requirements

- All changes require pull request
- At least one approval before merge
- Security-sensitive changes flagged

### Dependency Management

- Dependencies reviewed before adding
- Automated vulnerability scanning (Dependabot)
- Regular updates scheduled

### Secrets Management

**Never commit secrets to git**:
- API keys (Google Places, Anthropic)
- SMTP credentials
- Firebase service account JSON
- Database passwords
- Any credentials

**Use instead**:
- Google Secret Manager (production)
- Environment variables (local dev)
- `.gitignore` for local configs

---

## Known Limitations

### Current Security Gaps

| Gap | Risk | Mitigation Plan |
|-----|------|-----------------|
| No rate limiting on search/chat | DoS possible | Future: Implement limits |
| No audit logging | Limited forensics | Future: Add audit trail |
| Chat endpoint is public | Prompt injection possible | Claude prompt design mitigates |

### Accepted Risks

- Venue search and chat are public (intentional — no login required for core features)
- Saved events require Firebase auth

---

## Compliance

### GDPR Considerations

- Minimal personal data stored (Firebase UID, email for saved events)
- Users can delete saved events at any time
- No tracking or profiling beyond auth state

### COPPA Considerations

- Parents are the users, not children
- No data collected directly from children
- No account creation required for core venue search

---

## Security Contacts

| Role | Contact |
|------|---------|
| Security Lead | scout@partyscout.live |
| Project Owner | gouri.alampalli@gmail.com |

---

## Changelog

| Date | Change |
|------|--------|
| 2026-03-31 | Updated contact email; added auth, Cloud SQL, SMTP to security measures |
| 2026-01-29 | Initial security policy |

---

## Acknowledgments

We appreciate security researchers who help keep PartyScout safe. Contributors will be acknowledged here (with permission).
