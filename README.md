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

## Database & migrations
The backend persists core domain data in **PostgreSQL**, with schema managed by **Liquibase**. Migrations live in `backend/src/main/resources/db/changelog/` (master: `db.changelog-master.yaml`; change sets under `changes/`) and run **automatically on application startup** in every environment.

### Core entities (issue `#8`)
`drivers`, `loads`, `jobs`, `assignments`, `job_status_events`, `export_runs`, `billing_records`, and the `billing_record_jobs` join table. Foreign keys and status `CHECK` constraints are enforced at the database level.

### Run locally
1. Start PostgreSQL: `docker compose -f backend/docker-compose.db.yml up -d`
2. Run the app (applies migrations on startup): `cd backend && mvn spring-boot:run`

Connection settings are read from environment variables, defaulting to the local compose database. The container publishes host port **5433** (not the default 5432) to avoid clashing with a native PostgreSQL install:
- `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5433/btlp`)
- `SPRING_DATASOURCE_USERNAME` (default `btlp`)
- `SPRING_DATASOURCE_PASSWORD` (default `btlp`)

For staging, set those three variables to point at the staging database; migrations apply on startup.

### Rollback
Every change set ships an explicit rollback. To roll back the last N change sets against the configured database:
```
cd backend
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```
Use `mvn liquibase:updateSQL` to preview pending SQL without applying it.
These commands target the local dev database by default; for another environment, export `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD`, or pass `-Dliquibase.url=… -Dliquibase.username=… -Dliquibase.password=…`.

### Tests
Migrations and the rollback path are validated by `LiquibaseMigrationTest` against a real PostgreSQL using Testcontainers. Running `mvn test` therefore requires a running Docker daemon.
