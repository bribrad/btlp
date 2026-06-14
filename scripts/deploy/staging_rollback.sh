#!/usr/bin/env bash
set -euo pipefail

echo "Deployment failed. Entering rollback path..."

if [[ -n "${STAGING_ROLLBACK_COMMAND:-}" ]]; then
  echo "Executing configured rollback command."
  bash -lc "${STAGING_ROLLBACK_COMMAND}"
  echo "Rollback command completed."
  exit 0
fi

echo "::warning::STAGING_ROLLBACK_COMMAND secret is not configured."
echo "Manual rollback path:"
echo "1) Identify last known good release artifact/image."
echo "2) Redeploy that version to staging."
echo "3) Verify staging health checks and smoke tests."
