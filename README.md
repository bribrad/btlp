# BTLP

## CI/CD overview
This repository includes a GitHub Actions pipeline for CI validation and staging deployment.

### CI workflow
File: `.github/workflows/ci.yml`

Triggers:
- Pull requests
- Pushes to `main`

Required checks (branch protection):
- `lint`
- `test`
- `build`

Current CI behavior:
- The workflow looks for `backend` and `frontend` surfaces.
- If a surface exists, it must expose npm scripts:
  - `lint`
  - `test`
  - `build`
- If a surface exists without these scripts, CI fails.

CI scripts:
- `scripts/ci/lint.sh`
- `scripts/ci/test.sh`
- `scripts/ci/build.sh`

### Staging deployment workflow
File: `.github/workflows/deploy-staging.yml`

Trigger:
- Automatically runs after `CI` succeeds on pushes to `main`.

Environment:
- Uses GitHub environment `staging`.

Required secret:
- `STAGING_DEPLOY_COMMAND` (shell command that deploys the current commit to staging)

Optional secret:
- `STAGING_ROLLBACK_COMMAND` (shell command to revert staging to last known good state)

Deploy scripts:
- `scripts/deploy/staging_deploy.sh`
- `scripts/deploy/staging_rollback.sh`

## Rollback path
If deployment fails:
- The workflow invokes `staging_rollback.sh`.
- If `STAGING_ROLLBACK_COMMAND` exists, it runs automatically.
- If not, the workflow prints the manual rollback path:
  1. Identify last known good artifact/image.
  2. Redeploy to staging.
  3. Re-run staging health checks/smoke tests.
