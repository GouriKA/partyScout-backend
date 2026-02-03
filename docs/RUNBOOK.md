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

| Service | URL | Region | Project |
|---------|-----|--------|---------|
| Backend | https://partyscout-backend-869352526308.us-central1.run.app | us-central1 | bionic-upgrade-485121-h5 |
| Frontend | https://partyscout-frontend-869352526308.us-central1.run.app | us-central1 | bionic-upgrade-485121-h5 |

### Dependencies

| Dependency | Type | Impact if Down |
|------------|------|----------------|
| Google Places API | External | Venue search fails |
| Secret Manager | GCP | Backend fails to start |
| Container Registry | GCP | Deployments fail |

---

## Health Checks

### Quick Health Check

```bash
# Backend API
curl -s https://partyscout-backend-869352526308.us-central1.run.app/api/v2/party-wizard/party-types/7 | head -c 100

# Frontend
curl -s -o /dev/null -w "%{http_code}" https://partyscout-frontend-869352526308.us-central1.run.app
```

### Expected Results
- Backend: JSON array of party types
- Frontend: HTTP 200

### Service Status

```bash
# Check Cloud Run service status
gcloud run services describe partyscout-backend --region us-central1 --format='value(status.conditions[0].status)'

gcloud run services describe partyscout-frontend --region us-central1 --format='value(status.conditions[0].status)'
```

---

## Common Issues

### Issue 1: 502 Bad Gateway

**Symptoms**: Users see 502 error

**Possible Causes**:
1. Backend crashed during startup
2. Memory limit exceeded
3. Secret not accessible

**Diagnosis**:
```bash
# Check recent logs
gcloud run logs read partyscout-backend --region us-central1 --limit 50

# Check for OOM
gcloud run logs read partyscout-backend --region us-central1 --limit 50 | grep -i "memory\|oom\|killed"
```

**Resolution**:
```bash
# If memory issue, increase memory
gcloud run services update partyscout-backend --region us-central1 --memory 1Gi

# If secret issue, verify secret access
gcloud secrets versions access latest --secret=google-places-api-key
```

---

### Issue 2: No Venues Returned

**Symptoms**: Search returns empty results

**Possible Causes**:
1. Google Places API key invalid
2. API quota exceeded
3. Invalid ZIP code

**Diagnosis**:
```bash
# Check for API errors in logs
gcloud run logs read partyscout-backend --region us-central1 --limit 50 | grep -i "error\|exception\|api"

# Test API directly
curl -X POST https://partyscout-backend-869352526308.us-central1.run.app/api/v2/party-wizard/search \
  -H "Content-Type: application/json" \
  -d '{"age":7,"partyTypes":["active_play"],"guestCount":15,"zipCode":"94105"}'
```

**Resolution**:
- If quota exceeded: Wait or increase quota in GCP Console
- If key invalid: Rotate the secret (see Secret Rotation)

---

### Issue 3: Slow Response Times

**Symptoms**: API takes > 5 seconds

**Possible Causes**:
1. Cold start (scale from zero)
2. Google Places API slow
3. Too many results processing

**Diagnosis**:
```bash
# Check instance count
gcloud run services describe partyscout-backend --region us-central1 --format='value(status.traffic[0].latestRevision)'

# Check latency in logs
gcloud run logs read partyscout-backend --region us-central1 --limit 20 | grep -i "latency\|duration\|ms"
```

**Resolution**:
```bash
# Set minimum instances to avoid cold starts
gcloud run services update partyscout-backend --region us-central1 --min-instances 1
```

---

### Issue 4: CORS Errors

**Symptoms**: Browser console shows CORS errors

**Possible Causes**:
1. Frontend URL not in allowed origins
2. New frontend domain

**Resolution**:
1. Update `CorsConfig.kt` with new origin
2. Redeploy backend

---

## Deployment Procedures

### Deploy Backend

```bash
cd partyScout-backend

# Deploy with source
gcloud run deploy partyscout-backend \
  --source . \
  --region us-central1 \
  --set-secrets="GOOGLE_PLACES_API_KEY=google-places-api-key:latest" \
  --memory 512Mi
```

### Deploy Frontend

```bash
cd partyScout-frontend

# Build and deploy with Cloud Build
gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=_VITE_API_URL="https://partyscout-backend-869352526308.us-central1.run.app"
```

### Verify Deployment

```bash
# List revisions
gcloud run revisions list --service partyscout-backend --region us-central1

# Check traffic routing
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

### Rollback via Git

```bash
# Revert commit
git revert HEAD
git push origin main

# Redeploy
gcloud run deploy partyscout-backend --source . --region us-central1
```

---

## Secret Rotation

### Rotate Google Places API Key

1. **Create new key** in Google Cloud Console

2. **Add new secret version**:
   ```bash
   echo -n "NEW_API_KEY" | gcloud secrets versions add google-places-api-key --data-file=-
   ```

3. **Deploy to pick up new secret**:
   ```bash
   gcloud run services update partyscout-backend --region us-central1
   ```

4. **Verify** new key works

5. **Disable old key** in Google Cloud Console

6. **Delete old secret version** (optional):
   ```bash
   gcloud secrets versions disable 1 --secret=google-places-api-key
   ```

---

## Scaling

### Manual Scaling

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

### Auto-scaling Configuration

Current settings:
- Min instances: 0 (scale to zero)
- Max instances: 10
- Concurrency: 80 (default)

### When to Scale

| Symptom | Action |
|---------|--------|
| Cold starts affecting UX | Set min-instances=1 |
| 503 errors under load | Increase max-instances |
| OOM errors | Increase memory |
| CPU throttling | Increase CPU |

---

## Incident Response

### Severity Levels

| Level | Description | Response Time |
|-------|-------------|---------------|
| P1 | Service completely down | Immediate |
| P2 | Major feature broken | 1 hour |
| P3 | Minor issue, workaround exists | 24 hours |
| P4 | Cosmetic/enhancement | Best effort |

### P1 Incident Checklist

- [ ] Acknowledge incident
- [ ] Check service status in Cloud Run Console
- [ ] Check logs for errors
- [ ] Check Google Places API status
- [ ] Attempt rollback if recent deployment
- [ ] Communicate status to stakeholders
- [ ] Document timeline and resolution

### Post-Incident

1. Create incident report
2. Identify root cause
3. Implement preventive measures
4. Update runbook if needed

---

## Monitoring Commands Cheat Sheet

```bash
# Tail logs in real-time
gcloud run logs tail partyscout-backend --region us-central1

# View recent logs
gcloud run logs read partyscout-backend --region us-central1 --limit 100

# Check service status
gcloud run services describe partyscout-backend --region us-central1

# List all revisions
gcloud run revisions list --service partyscout-backend --region us-central1

# Check traffic split
gcloud run services describe partyscout-backend --region us-central1 --format='yaml(status.traffic)'

# View metrics in console
open "https://console.cloud.google.com/run/detail/us-central1/partyscout-backend/metrics?project=bionic-upgrade-485121-h5"
```
