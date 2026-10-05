# WARP.md

Guidance for agents working in the **BTLP** (Broker Trucking & Logistics Platform) repository.
Read this before making changes. It captures conventions that are enforced by review and CI but are
not obvious from a single file.

## Repository layout

Two build surfaces live side by side; CI discovers them by name (`backend/`, `frontend/`).

- `backend/` — Java 21 / Spring Boot 3.3 REST API (`com.topnotchbroker.btlp`, artifact `btlp-backend`)
- `frontend/` — React 18 + TypeScript + Vite dispatcher portal (`btlp-dispatcher-portal`)
- `scripts/ci/` — `lint.sh`, `test.sh`, `build.sh` (the exact scripts CI runs)
- `scripts/deploy/` — `staging_deploy.sh`, `staging_rollback.sh`
- `docs/` — `architecture.md`, `dispatch-lifecycle-api.md`, `activity-timeline-api.md`, plus dated
  `programme-status-*.md` stakeholder snapshots
- `.github/workflows/` — `ci.yml`, `deploy-staging.yml`

Root-level `trucking-logistics-*.md` files are product/roadmap docs, not specs to implement against.

## Common commands

Run from the repository root unless noted.

```bash
# Start the local database (required for backend run AND tests)
docker compose -f backend/docker-compose.db.yml up -d

# Backend
cd backend && mvn spring-boot:run       # applies Liquibase migrations on startup
cd backend && mvn -B test               # requires a running Docker daemon (Testcontainers)
cd backend && mvn -B validate           # what CI runs as the backend "lint" step
cd backend && mvn -B package

# Frontend
cd frontend && npm run dev              # Vite dev server on :5173
cd frontend && npm run lint             # eslint, --max-warnings 0
cd frontend && npm run test             # vitest run
cd frontend && npm run test:e2e         # playwright
cd frontend && npm run build            # tsc -b && vite build

# Reproduce CI exactly (covers both surfaces)
./scripts/ci/lint.sh && ./scripts/ci/test.sh && ./scripts/ci/build.sh
```

Run a single backend test class or method:

```bash
cd backend && mvn -B test -Dtest=LoadApiIntegrationTest
cd backend && mvn -B test -Dtest=LoadApiIntegrationTest#createReturns201WithBodyLocationAndAudit
```

**Docker is a hard requirement for `mvn test`.** The suite boots a real PostgreSQL via Testcontainers;
without a Docker daemon every integration test fails at startup.

**Minimum supported Docker Engine: 20.10.** `backend/pom.xml` pins the Docker API version Surefire
passes to Testcontainers (`api.version=1.41`), because docker-java otherwise negotiates v1.32 and
Engine 29+ rejects anything below v1.40 with a 400 — surfacing as `Could not find a valid Docker
environment` even though the daemon is running. v1.41 is served by every engine from 20.10 onward.

## Architecture

### Backend: package-by-feature, three layers

Packages are organized by domain (`load`, `job`, `driver`, `dispatch`, `audit`, `activity`,
`idempotency`), not by technical layer. Cross-cutting concerns live in `security`, `web`, `logging`,
and `api`.

Each feature package follows the same shape, e.g. `load/`:

- `LoadController` — HTTP only: validation triggers, status codes, `Location` headers, logging
- `LoadService` — `@Transactional` business logic, audit stamping, domain exceptions
- `LoadRepository` — data access
- `Load` — domain record; `LoadCreateRequest` / `LoadUpdateRequest` / `LoadResponse` — DTO records
- `LoadStatus` — enum mirroring a database `CHECK` constraint

Keep this split. Controllers must not contain business logic, and services must not construct HTTP
responses.

### Persistence: explicit SQL, no JPA

There is **no JPA/Hibernate** in this project. Repositories are hand-written `@Repository` classes
using `NamedParameterJdbcTemplate` with:

- SQL held in `private static final String` text blocks
- A `private static final RowMapper<T>` constant for mapping
- `INSERT`/`UPDATE ... RETURNING *` so DB-managed columns (id, timestamps) come back in one round trip
- `MapSqlParameterSource` with explicit `java.sql.Types` (note: UUIDs bind as `Types.OTHER`)
- `Optional<T>` returns for single-row lookups via `.stream().findFirst()`

Do not add `spring-boot-starter-data-jpa` or entity annotations to "simplify" this — it is a
deliberate choice for query control.

### Domain models and DTOs are Java records

Use `record` for domain models, requests, and responses. Response records expose a static factory
(`LoadResponse.from(load)`) rather than a constructor call at the call site.

## Conventions

### API

- All endpoints are versioned under `/api/v1/**` and require authentication.
- List endpoints return the `PagedResponse` envelope (`content`, `page`, `size`, `totalElements`,
  `totalPages`) built with `PagedResponse.of(...)` — never a bare array.
- Pagination params default to `page=0`, `size=20`, and are clamped in the controller
  (`MAX_PAGE_SIZE = 100`). Follow the same clamping pattern for new list endpoints.
- `POST` that creates a resource returns `201` with a `Location` header built from
  `UriComponentsBuilder`.
- Mutating dispatch/driver endpoints accept an optional `Idempotency-Key` header; route it through
  `IdempotencyService`.

### Errors

Every error response is the `ApiErrorResponse` shape: `{"error":"CODE","message":"..."}`.

Throw domain exceptions from `com.topnotchbroker.btlp.web` and let `ApiExceptionHandler` map them —
do not build `ResponseEntity` error bodies in controllers or services:

- `ResourceNotFoundException` → `404 NOT_FOUND`
- `ValidationException` → `400 VALIDATION_ERROR`
- `ConflictException` → `409 CONFLICT`
- `InvalidStateTransitionException` → `409 INVALID_STATE_TRANSITION`

`401 UNAUTHORIZED` and `403 FORBIDDEN` are produced earlier by `ApiAuthenticationEntryPoint` and
`ApiAccessDeniedHandler` in the security filter chain, so they are intentionally absent from the
exception handler.

### Security and RBAC

Roles are `DISPATCHER`, `DRIVER`, `BILLING`, `ADMIN`; `ADMIN` has access to all role-scoped routes.
Authorization is declared centrally by URL prefix in `SecurityConfig` — **when you add a new route
prefix, add a matching rule there**, otherwise it falls through to `authenticated()` with no role check.

The actor for audit stamping comes from `Authentication#getName()`, passed from the controller into
the service. Never trust an actor field supplied in a request body.

The in-memory users in `SecurityConfig` (`dispatcher/dispatcher-pass`, `driver/driver-pass`,
`billing/billing-pass`, `admin/admin-pass`) and the permissive localhost CORS config are development
scaffolding to be replaced by a real identity provider before production (issue #70). Do not add new
hardcoded credentials or commit real secrets.

### Auditing

Create/update actions on loads and jobs must record an audit event through `AuditService.record(...)`
**inside the same `@Transactional` method** as the change, so an audit row exists only if the change
committed.

Pick the `AuditAction` that names the transition (`ASSIGN`, `ACCEPT`, `CANCEL`, `STATUS_CHANGE`, …),
not a generic `UPDATE` — the operations timeline renders the verb directly. Pass the entity's
resulting status/state as the `detail` argument. `audit_events.occurred_at` is transaction start
time, so events written by one action share it; order by the `seq` column instead. See
`docs/activity-timeline-api.md`.

### Database migrations

Schema is owned by Liquibase and applied automatically on application startup in every environment.

To add a migration:

1. Create `backend/src/main/resources/db/changelog/changes/NNNN_snake_case_description.sql` using the
   next zero-padded sequence number.
2. Start the file with a `--liquibase formatted sql logicalFilePath:...` header and a
   `--changeset btlp:NNNN-kebab-case-description` line.
3. **Always include an explicit `--rollback` statement.** This is required, not optional.
4. Register the file with an `include` entry (with `relativeToChangelogFile: true`) at the end of
   `db.changelog-master.yaml`.

Never edit an already-applied changeset — add a new one. Status enums are enforced with database
`CHECK` constraints, so changing an enum in Java requires a migration that extends the constraint
(see `0014_extend_assignments_state.sql`).

Preview and roll back:

```bash
cd backend && mvn liquibase:updateSQL                        # preview pending SQL
cd backend && mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

### Backend tests

Integration tests live in `backend/src/test/java/com/topnotchbroker/btlp/` and follow one pattern:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
```

- `PostgresTestContainerConfig` is a JVM-wide singleton container reused by the whole suite — import
  it rather than declaring a new container, or the suite time balloons.
- Isolate state with a `@BeforeEach` that deletes from the tables under test via `JdbcTemplate`.
- Authenticate with `.with(httpBasic("dispatcher", "dispatcher-pass"))` and assert against real roles.
- Cover the full matrix for each new endpoint: happy path, validation `400`, `404`, role-forbidden
  `403`, and anonymous `401`. Assert on `$.error` codes, not just status numbers.
- Test method names are descriptive sentences (`createWithBlankOriginReturns400`).

### Frontend

- Path alias `@/` maps to `frontend/src/`.
- All HTTP goes through the `api` helper in `src/api/client.ts`; it injects Basic auth from
  session storage and normalizes failures into `ApiRequestError` carrying `status`, `code`, `message`.
  Do not call `fetch` directly in components.
- Server state uses TanStack Query; forms use `react-hook-form` + `zod` via `@hookform/resolvers`.
- UI is Tailwind + Radix primitives; compose class names with `cn()` from `src/lib/utils.ts`.
- Shared API types live in `src/types/index.ts` and mirror backend response records.
- Lint runs with `--max-warnings 0`, so warnings break the build.

### Code style

- Backend: Google Java Format conventions already in use — 2-space indent, 100-column limit,
  static imports for test matchers. Every class gets a Javadoc comment explaining its role.
- Loggers are `private static final Logger log = LoggerFactory.getLogger(X.class);`. Log mutations
  with structured key=value pairs (`log.info("Created load id={} by={}", id, actor)`), and never log
  credentials or full request bodies.
- Configuration is externalized via environment variables with local defaults in `application.yml`
  (e.g. `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/btlp}`). Tunables belong under the
  `btlp.*` prefix bound to a `@ConfigurationProperties` record (see `DispatchProperties`).

## Local environment notes

- Keep the working copy **outside** iCloud Drive (i.e. not under `~/Documents` or `~/Desktop` when
  "Desktop & Documents Folders" sync is on). Syncing evicts file contents and creates `file 2.tsx`
  conflict copies — including inside `.git` — which has already wedged a checkout.
- PostgreSQL is published on host port **5433**, not 5432, to avoid clashing with a native install.
- Backend runs on `:8080`; the Vite dev server runs on `:5173` and is the only origin allowed by CORS.
- Actuator endpoints (`/actuator/**`) are unauthenticated and expose `health`, `metrics`,
  `prometheus`, and `info`.
- Logs include a `requestId` MDC value populated by `RequestIdFilter`; preserve it when adding
  async or scheduled work.

## Git and PR workflow

- Branch from `main` using `feature/`, `fix/`, `chore/`, or `docs/` prefixes, lowercase and hyphenated.
- Never push directly to `main`; open a focused PR titled `feat:`, `fix:`, `chore:`, or `docs:`.
- Link the issue in the description (`Closes #123`) and include rollout/rollback notes.
- Required checks are `lint`, `test`, and `build`; branch protection enforces them plus one approval.
- Prefer squash merge.
- Do not commit `backend/target/` or other build output.
- **Do not commit unless explicitly asked.** When you do, append
  `Co-Authored-By: Warp <agent@warp.dev>` to the commit message.
