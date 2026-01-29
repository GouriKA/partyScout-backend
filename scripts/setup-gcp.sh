#!/bin/bash
# GCP Setup Script for PartyScout Backend
# Run this once to set up your GCP project

set -e

# Configuration - UPDATE THESE VALUES
PROJECT_ID="${GCP_PROJECT_ID:-your-project-id}"
REGION="us-central1"
SERVICE_ACCOUNT_NAME="partyscout-deployer"

echo "Setting up GCP for PartyScout Backend..."
echo "Project: $PROJECT_ID"
echo "Region: $REGION"

# Set project
gcloud config set project $PROJECT_ID

# Enable required APIs
echo "Enabling required APIs..."
gcloud services enable \
  run.googleapis.com \
  containerregistry.googleapis.com \
  secretmanager.googleapis.com \
  cloudbuild.googleapis.com

# Create Secret for Google Places API Key
echo "Creating secret for Google Places API key..."
echo "Enter your Google Places API key:"
read -s PLACES_API_KEY

echo -n "$PLACES_API_KEY" | gcloud secrets create google-places-api-key \
  --replication-policy="automatic" \
  --data-file=- 2>/dev/null || \
  echo -n "$PLACES_API_KEY" | gcloud secrets versions add google-places-api-key --data-file=-

# Create Service Account for GitHub Actions
echo "Creating service account for CI/CD..."
gcloud iam service-accounts create $SERVICE_ACCOUNT_NAME \
  --display-name="PartyScout Deployer" 2>/dev/null || echo "Service account already exists"

SA_EMAIL="$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com"

# Grant required permissions
echo "Granting permissions..."
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/run.admin" --quiet

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/storage.admin" --quiet

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/iam.serviceAccountUser" --quiet

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/secretmanager.secretAccessor" --quiet

# Allow Cloud Run to access secrets
echo "Granting Cloud Run access to secrets..."
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
gcloud secrets add-iam-policy-binding google-places-api-key \
  --member="serviceAccount:$PROJECT_NUMBER-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor" --quiet

# Create and download service account key
echo "Creating service account key..."
KEY_FILE="sa-key.json"
gcloud iam service-accounts keys create $KEY_FILE \
  --iam-account=$SA_EMAIL

echo ""
echo "=========================================="
echo "Setup complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Add these GitHub repository secrets:"
echo "   - GCP_PROJECT_ID: $PROJECT_ID"
echo "   - GCP_SA_KEY: (contents of $KEY_FILE)"
echo ""
echo "2. Delete the key file after adding to GitHub:"
echo "   rm $KEY_FILE"
echo ""
echo "3. Push to main branch to trigger deployment"
