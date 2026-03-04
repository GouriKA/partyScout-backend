#!/usr/bin/env bash
set -euo pipefail

# PartyScout Cloud SQL + Pub/Sub Provisioning Script
# Usage: ./scripts/setup-cloudsql.sh <PROJECT_ID> <REGION>

PROJECT_ID="${1:?Usage: $0 <PROJECT_ID> <REGION>}"
REGION="${2:-us-central1}"

INSTANCE_NAME="partyscout-db"
DB_NAME="partyscout"
DB_USER="partyscout-app"
DB_PASSWORD=$(openssl rand -base64 24)
SERVICE_ACCOUNT="partyscout-backend@${PROJECT_ID}.iam.gserviceaccount.com"

echo "=== PartyScout Cloud SQL Setup ==="
echo "Project: ${PROJECT_ID}"
echo "Region:  ${REGION}"
echo ""

# 1. Create Cloud SQL instance
echo "Creating Cloud SQL instance..."
gcloud sql instances create "${INSTANCE_NAME}" \
  --project="${PROJECT_ID}" \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region="${REGION}" \
  --storage-auto-increase \
  --backup-start-time=03:00

# 2. Create database
echo "Creating database..."
gcloud sql databases create "${DB_NAME}" \
  --project="${PROJECT_ID}" \
  --instance="${INSTANCE_NAME}"

# 3. Create user
echo "Creating database user..."
gcloud sql users create "${DB_USER}" \
  --project="${PROJECT_ID}" \
  --instance="${INSTANCE_NAME}" \
  --password="${DB_PASSWORD}"

# 4. Store password in Secret Manager
echo "Storing password in Secret Manager..."
printf '%s' "${DB_PASSWORD}" | gcloud secrets create partyscout-db-password \
  --project="${PROJECT_ID}" \
  --data-file=- \
  --replication-policy=automatic

# 5. Grant IAM roles to service account
echo "Granting IAM roles..."
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/secretmanager.secretAccessor"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/pubsub.publisher"

# 6. Create Pub/Sub topics
echo "Creating Pub/Sub topics..."
gcloud pubsub topics create partyscout-venue-events \
  --project="${PROJECT_ID}" || true

gcloud pubsub topics create partyscout-budget-events \
  --project="${PROJECT_ID}" || true

gcloud pubsub topics create partyscout-domain-events \
  --project="${PROJECT_ID}" || true

# 7. Output connection info
INSTANCE_CONNECTION_NAME=$(gcloud sql instances describe "${INSTANCE_NAME}" \
  --project="${PROJECT_ID}" \
  --format='value(connectionName)')

echo ""
echo "=== Setup Complete ==="
echo "Instance Connection Name: ${INSTANCE_CONNECTION_NAME}"
echo "Database:                 ${DB_NAME}"
echo "User:                     ${DB_USER}"
echo "Password Secret:          partyscout-db-password"
echo ""
echo "Add these env vars to Cloud Run:"
echo "  DB_NAME=${DB_NAME}"
echo "  DB_USERNAME=${DB_USER}"
echo "  CLOUD_SQL_INSTANCE=${INSTANCE_CONNECTION_NAME}"
echo "  GCP_PROJECT_ID=${PROJECT_ID}"
echo "  SPRING_PROFILES_ACTIVE=prod"
