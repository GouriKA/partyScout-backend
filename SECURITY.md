# Security Policy

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please report it responsibly.

### How to Report

**DO NOT** open a public GitHub issue for security vulnerabilities.

Instead, please email: [security contact email]

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
| Search queries | Not stored | None |
| Personal info | Not collected | None |
| API keys | Secret Manager | Rotated quarterly |
| Logs | Cloud Logging | 30 days |

**We do not**:
- Store user personal information
- Track users across sessions
- Share data with third parties
- Use cookies for tracking

### API Security

| Measure | Implementation |
|---------|----------------|
| HTTPS | Enforced by Cloud Run |
| API Keys | Stored in Secret Manager |
| CORS | Restricted to known origins |
| Input Validation | Server-side validation |

### Infrastructure Security

| Layer | Protection |
|-------|------------|
| Compute | Cloud Run (managed, isolated) |
| Secrets | Google Secret Manager (encrypted) |
| Network | HTTPS only, no direct DB access |
| Auth | Currently public (no auth required) |

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
- API keys
- Passwords
- Service account keys
- Any credentials

**Use instead**:
- Environment variables
- Secret Manager
- `.gitignore` for local configs

---

## Known Limitations

### Current Security Gaps

| Gap | Risk | Mitigation Plan |
|-----|------|-----------------|
| No authentication | Public access | Future: Add user accounts |
| No rate limiting | DoS possible | Future: Implement limits |
| No audit logging | Limited forensics | Future: Add audit trail |

### Accepted Risks

- Public API: Intentional for MVP
- No user accounts: Reduces data liability

---

## Compliance

### GDPR Considerations

- No EU user data stored
- No cookies requiring consent
- No tracking or profiling

### COPPA Considerations

- No data collected from children
- Parents are the users (not children)
- No account creation required

---

## Security Contacts

| Role | Contact |
|------|---------|
| Security Lead | [email] |
| Project Owner | gouri.alampalli@gmail.com |

---

## Changelog

| Date | Change |
|------|--------|
| 2026-01-29 | Initial security policy |

---

## Acknowledgments

We appreciate security researchers who help keep PartyScout safe. Contributors will be acknowledged here (with permission).

*No vulnerabilities reported yet.*
