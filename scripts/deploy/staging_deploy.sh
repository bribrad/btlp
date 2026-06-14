#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${STAGING_DEPLOY_COMMAND:-}" ]]; then
  echo "::error::STAGING_DEPLOY_COMMAND secret is not configured."
  echo "Set repository secret STAGING_DEPLOY_COMMAND to your staging deploy command."
  exit 1
fi

echo "Starting staging deployment..."
bash -lc "${STAGING_DEPLOY_COMMAND}"
echo "Staging deployment completed."
