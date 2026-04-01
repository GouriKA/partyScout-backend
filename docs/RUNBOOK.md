# PartyScout Runbook

Operational procedures for managing PartyScout in production.

---

## Table of Contents

1. [Service Overview](#service-overview)
2. [Health Checks](#health-checks)
3. [Common Issues](#common-issues)
4. [Deployment Procedures](#deployment-procedures)
5. [Rollback Procedures](#rollback-procedures)
6. [Secret Rotation](#secret-rotation)
7. [Scaling](#scaling)
8. [Incident Response](#incident-response)

---

## Service Overview

| Service | Region | Purpose |
|---------|--------|---------|
| `partyscout-backend` | us-central1 | Production backend |
| `partyscout-frontend` | us-central1 | Production frontend |
| `partyscout-backend-canary` | us-east1 | Canary/staging backend |
| `partyscout-frontend-canary` | us-east1 | Canary/staging frontend |

**Production URL**: `https://partyscout.app` (HTTPS load balancer; `/api/*` → backend, `/*` → frontend)

### Dependencies

| Dependency | Type | Impact if Down |
|------------|------|----------------|
| Google Places API | External | Venue search fails |
| Anthropic API | External | AI chat + LLM filter unavailable |
| Firebase Auth | External | Login + saved events fail |
| Zoho SMTP | External | Feedback emails not sent |
| Cloud SQL (PostgreSQL) | GCP | Saved events + search history fail |
| Secret Manager | GCP | Backend fails to start |

---

## Health Checks

### Quick Health Check

```bash
# Production backend
curl -s https://partyscout.app/api/v2/party-wizard/party-types/7 | head -c 100

# Canary backend
curl -s https://partyscout-backend-canary-<hash>-ue.a.run.app/api/v2/party-wizard/party-types/7

# Production frontend
curl -s -o /dev/null -w "%{http_code}" https://partyscout.app
```

### Expected Results
- Backend: JSON array of party types
- Frontend: HTTP 200

### Service Status

```bash
gcloud run services describe partyscout-backend --region us-central1 \
  --format='value(status.conditions[0].status)'

gcloud run services describe partyscout-backend-canary --region us-east1 \
  --format='value(status.conditions[0].status)'
```

---

## Common Issues

### Issue 1: 502 Bad Gateway

**Symptoms**: Users see 502 error

**Possible Causes**:
1. Backend crashed during startup (missing secret)
2. Memory limit exceeded
3. Cloud SQL connection failure

**Diagnosis**:
```bash
gcloud run services logs read partyscout-backend --region us-central1 --limit 50
gcloud run services logs read partyscout-backend --region us-central1 --limit 50 | grep -i "memory\|oom\|killed\|secret\|sql"
```

**Resolution**:
```bash
# If secret missing — inject it
gcloud run services update partyscout-backend --region us-central1 \
  --update-secrets=ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest

# If memory issue
gcloud run services update partyscout-backend --region us-central1 --memory 1Gi
```

---

### Issue 2: Chat "API key not configured"

**Symptoms**: Chat returns "I'm not able to assist right now — API key not configured"

**Cause**: `ANTHROPIC_API_KEY` not injected into the Cloud Run revision.

**Resolution**:
```bash
gcloud run services update partyscout-backend --region us-central1 \
  --update-secrets=ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest
```

---

### Issue 3: Feedback Emails Not Received

**Symptoms**: `POST /api/v2/feedback` succeeds but no email arrives at `scout@partyscout.live`

**Cause**: SMTP secrets not injected.

**Resolution**:
```bash
gcloud run services update partyscout-backend --region us-central1 \
  --update-secrets=SMTP_HOST=smtp-host:latest,SMTP_USERNAME=smtp-username:latest,SMTP_PASSWORD=smtp-password:latest
```

---

### Issue 4: No Venues Returned

**Symptoms**: Search returns empty results

**Possible Causes**:
1. Google Places API key invalid or quota exceeded
2. City geocoding failed
3. All search queries returned empty (very small city)

**Diagnosis**:
```bash
gcloud run services logs read partyscout-backend --region us-central1 --limit 50 | grep -i "error\|exception\|api\|geocode"
```

**Resolution**:
- If quota exceeded: Wait or increase quota in GCP Console
- If key invalid: Rotate the secret (see Secret Rotation)
- If small city: Expected behavior — fallback query runs automatically

---

### Issue 5: Outdoor Filter Shows Blank Results

**Symptoms**: Setting filter "Outdoor" returns no venues

**Cause**: All returned venues classified as indoor (older inferSetting logic bug).

**Note**: Fixed in v3.0.0 — outdoor queries always appended. If this resurfaces, check `PartySearchController.inferSetting` and `buildSearchQueries`.

---

### Issue 6: Slow Response Times

**Symptoms**: API takes > 5 seconds

**Possible Causes**:
1. Cold start (scale from zero)
2. Google Places API slow
3. Anthropic API slow (LLM filter)

**Resolution**:
```bash
# Set minimum instances to avoid cold starts
gcloud run services update partyscout-backend --region us-central1 --min-instances 1
```

---

### Issue 7: CORS Errors

**Symptoms**: Browser console shows CORS errors

**Resolution**: Update `CorsConfig.kt` with the new origin, then redeploy.

---

## Deployment Procedures

### Deploy to Canary (auto on push to main)

Push to `main` triggers Cloud Build automatically for both frontend and backend canary services.

```bash
# Manual trigger if needed
COMMIT_SHA=$(git rev-parse HEAD)
gcloud builds submit --config cloudbuild.yaml --substitutions=COMMIT_SHA=$COMMIT_SHA
```

### Promote Canary to Production

**IMPORTANT**: Prod always runs the exact canary image — never rebuild for prod.

```bash
# Backend
IMAGE=$(gcloud run services describe partyscout-backend-canary \
  --region us-east1 \
  --format="value(spec.template.spec.containers[0].image)")
gcloud run deploy partyscout-backend --image $IMAGE --region us-central1 --quiet

# Frontend
IMAGE=$(gcloud run services describe partyscout-frontend-canary \
  --region us-east1 \
  --format="value(spec.template.spec.containers[0].image)")
gcloud run deploy partyscout-frontend --image $IMAGE --region us-central1 --quiet
```

### Verify Deployment

```bash
gcloud run revisions list --service partyscout-backend --region us-central1
gcloud run services describe partyscout-backend --region us-central1 --format='yaml(status.traffic)'
```

---

## Rollback Procedures

### Rollback to Previous Revision

```bash
# List revisions
gcloud run revisions list --service partyscout-backend --region us-central1

# Route 100% traffic to previous revision
gcloud run services update-traffic partyscout-backend \
  --region us-central1 \
  --to-revisions=partyscout-backend-00006-abc=100
```

---

## Secret Rotation

### Rotate Google Places API Key

```bash
echo -n "NEW_API_KEY" | gcloud secrets versions add google-places-api-key --data-file=-
gcloud run services update partyscout-backend --region us-central1
gcloud run services update partyscout-backend-canary --region us-east1
```

### Rotate Anthropic API Key

```bash
echo -n "NEW_KEY" | gcloud secrets versions add ANTHROPIC_API_KEY --data-file=-
gcloud run services update partyscout-backend --region us-central1 \
  --update-secrets=ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest
```

### Rotate SMTP Password

```bash
echo -n "NEW_PASSWORD" | gcloud secrets versions add smtp-password --data-file=-
gcloud run services update partyscout-backend --region us-central1 \
  --update-secrets=SMTP_PASSWORD=smtp-password:latest
```

---

## Scaling

```bash
# Set min/max instances
gcloud run services update partyscout-backend \
  --region us-central1 \
  --min-instances 1 \
  --max-instances 20

# Increase resources
gcloud run services update partyscout-backend \
  --region us-central1 \
  --memory 1Gi \
  --cpu 2
```

| Symptom | Action |
|---------|--------|
| Cold starts affecting UX | Set min-instances=1 |
| 503 errors under load | Increase max-instances |
| OOM errors | Increase memory to 1Gi |

---

## Incident Response

### Severity Levels

| Level | Description | Response Time |
|-------|-------------|---------------|
| P1 | Service completely down | Immediate |
| P2 | Major feature broken (chat, search, auth) | 1 hour |
| P3 | Minor issue, workaround exists | 24 hours |
| P4 | Cosmetic/enhancement | Best effort |

### P1 Incident Checklist

- [ ] Acknowledge incident
- [ ] Check service status in Cloud Run Console
- [ ] Check logs for errors: `gcloud run services logs read partyscout-backend --region us-central1 --limit 100`
- [ ] Verify all secrets are mounted: check for "not configured" or "connection refused" in logs
- [ ] Check Google Places API status: console.cloud.google.com
- [ ] Check Anthropic API status: status.anthropic.com
- [ ] Attempt rollback if recent deployment
- [ ] Document timeline and resolution

---

## Monitoring Commands Cheat Sheet

```bash
# Tail logs in real-time (prod)
gcloud run services logs tail partyscout-backend --region us-central1

# View recent logs (canary)
gcloud run services logs read partyscout-backend-canary --region us-east1 --limit 100

# Check service status
gcloud run services describe partyscout-backend --region us-central1

# List all revisions
gcloud run revisions list --service partyscout-backend --region us-central1

# Check traffic split
gcloud run services describe partyscout-backend --region us-central1 --format='yaml(status.traffic)'

# List all secrets
gcloud secrets list

# Verify a secret exists
gcloud secrets versions access latest --secret=ANTHROPIC_API_KEY
```
