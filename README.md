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
- Supported build surface types:
  - Maven (`pom.xml`) for Java/Spring services
  - npm (`package.json`) for Node services
- If a surface exists without supported Maven/npm commands, CI fails.

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

## Backend stack and security scaffold
Backend stack for issue `#5`:
- Java 21
- Spring Boot 3
- Spring Security (HTTP Basic + RBAC scaffolding)

Project path:
- `backend`

Run locally:
1. `cd backend`
2. `mvn spring-boot:run`

### Auth scaffold
All `/api/v1/**` endpoints require authenticated identity.

Consistent auth error payloads:
- 401 Unauthorized
  - `{"error":"UNAUTHORIZED","message":"Authentication is required to access this resource."}`
- 403 Forbidden
  - `{"error":"FORBIDDEN","message":"Authenticated user is not allowed to access this resource."}`

### Role claims to allowed actions
- `DISPATCHER`
  - `GET /api/v1/dispatch/jobs`
- `DRIVER`
  - `GET /api/v1/driver/assignments`
- `BILLING`
  - `GET /api/v1/billing/exports`
- `ADMIN`
  - Access to all role-scoped endpoints, including:
  - `GET /api/v1/admin/users`

Authenticated identity probe:
- `GET /api/v1/me`

### Scaffold credentials (development only)
- `dispatcher / dispatcher-pass`
- `driver / driver-pass`
- `billing / billing-pass`
- `admin / admin-pass`

These credentials are for scaffolding and must be replaced with a real identity provider before production.
