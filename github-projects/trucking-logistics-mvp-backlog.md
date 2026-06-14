# TopNotch Broker — GitHub Projects MVP Backlog

## Project configuration
### Recommended Project fields
- `Status`: Backlog, Ready, In Progress, In Review, Blocked, Done
- `Priority`: P0, P1, P2
- `Size`: XS, S, M, L, XL
- `Type`: Epic, Feature, Task, Bug
- `Area`: product, platform, backend, dispatch, web-dispatcher, mobile-driver, realtime, billing, security, qa-release
- `Milestone`: M1-M7
- `Iteration`: Week 1 ... Week 22
- `Risk`: Low, Medium, High
- `Dependencies`: issue keys (comma-separated)

### Milestones
- **M1 (Weeks 1-6):** Backend foundation and end-to-end dispatch lifecycle
- **M2 (Weeks 7-9):** Dispatcher web experience
- **M3 (Weeks 10-14):** Driver mobile workflow reliability
- **M4 (Weeks 15-16):** Real-time visibility
- **M5 (Week 17):** Billing export
- **M6 (Weeks 18-19):** Security, reliability, observability hardening
- **M7 (Weeks 20-22):** QA, UAT, pilot launch

### Labels
- Type: `type/epic`, `type/feature`, `type/task`, `type/bug`
- Area: `area/product`, `area/platform`, `area/backend`, `area/dispatch`, `area/web-dispatcher`, `area/mobile-driver`, `area/realtime`, `area/billing`, `area/security`, `area/qa-release`
- Priority: `priority/p0`, `priority/p1`, `priority/p2`
- Status: `status/backlog`, `status/blocked`
- Risk: `risk/high`, `risk/medium`, `risk/low`

## Full backlog
## M1 — Foundation and backend lifecycle (Weeks 1-6)
### MVP-001 — [Epic] Foundation and environment baseline
- Type: Epic
- Area: platform
- Priority: P0
- Size: L
- Iteration: Weeks 1-2
- Milestone: M1
- Dependencies: none
- Description: Establish delivery foundations (scope baseline, repo standards, CI/CD, auth scaffolding, telemetry baseline).
- Acceptance criteria:
  - Dev and staging environments are deployable through CI.
  - Core team roles (dispatcher, driver, billing, admin) are represented in RBAC scaffolding.
  - Logging and error monitoring baseline are active in staging.

### MVP-002 — Finalize MVP requirements and acceptance matrix
- Type: Task
- Area: product
- Priority: P0
- Size: M
- Iteration: Week 1
- Milestone: M1
- Dependencies: none
- Description: Finalize MVP scope, workflows, and definition-of-done criteria aligned to roadmap checkpoints.
- Acceptance criteria:
  - MVP in-scope and out-of-scope items are documented.
  - Workflow acceptance criteria are defined for load creation, dispatch, status updates, and billing export.
  - Stakeholder sign-off on scope baseline is recorded.

### MVP-003 — Repository standards and branching strategy
- Type: Task
- Area: platform
- Priority: P1
- Size: S
- Iteration: Week 1
- Milestone: M1
- Dependencies: MVP-002
- Description: Establish coding conventions, branch naming, PR review rules, and required checks.
- Acceptance criteria:
  - Contribution guide includes branch and PR conventions.
  - Required checks are enforced for protected main branch.
  - PR template includes testing and rollout notes.

### MVP-004 — CI/CD pipeline for dev and staging
- Type: Task
- Area: platform
- Priority: P0
- Size: M
- Iteration: Week 2
- Milestone: M1
- Dependencies: MVP-003
- Description: Implement build/test/deploy pipeline for backend and frontend surfaces with staging promotion.
- Acceptance criteria:
  - Push to main triggers build and deployment to staging.
  - CI enforces linting and test execution.
  - Failed deployments provide clear rollback path.

### MVP-005 — Authentication and RBAC scaffolding
- Type: Feature
- Area: security
- Priority: P0
- Size: M
- Iteration: Week 2
- Milestone: M1
- Dependencies: MVP-002, MVP-004
- Description: Add auth foundation and role-based access model for dispatcher, driver, billing, and admin.
- Acceptance criteria:
  - Protected endpoints require authenticated identity.
  - Role claims map to allowed actions by role.
  - Unauthorized requests return consistent error responses.

### MVP-006 — Logging, metrics, and baseline alerts
- Type: Task
- Area: platform
- Priority: P1
- Size: M
- Iteration: Week 2
- Milestone: M1
- Dependencies: MVP-004
- Description: Add structured logs, baseline service metrics, and initial alert rules.
- Acceptance criteria:
  - Logs include request ID and entity IDs where relevant.
  - Basic service health and error rate dashboards exist.
  - Alerting triggers on elevated error thresholds.

### MVP-007 — [Epic] Core load/job domain and APIs
- Type: Epic
- Area: backend
- Priority: P0
- Size: XL
- Iteration: Weeks 3-4
- Milestone: M1
- Dependencies: MVP-001
- Description: Build data model and API surface for load/job creation and management.
- Acceptance criteria:
  - Load and job entities are persisted with lifecycle status.
  - CRUD APIs are available and tested.
  - Domain validation rules prevent invalid records.

### MVP-008 — Database schema and migrations for core entities
- Type: Task
- Area: backend
- Priority: P0
- Size: M
- Iteration: Week 3
- Milestone: M1
- Dependencies: MVP-007
- Description: Implement schema and migrations for Load, Job, Driver, Assignment, JobStatusEvent, BillingRecord, ExportRun.
- Acceptance criteria:
  - Migrations run cleanly in local and staging environments.
  - Core foreign keys and status constraints are enforced.
  - Rollback migration path is validated.

### MVP-009 — Load CRUD API
- Type: Feature
- Area: backend
- Priority: P0
- Size: M
- Iteration: Week 4
- Milestone: M1
- Dependencies: MVP-008
- Description: Build create/read/update load endpoints for dispatcher workflows.
- Acceptance criteria:
  - Loads can be created and retrieved by ID/list.
  - Update path enforces immutable fields where required.
  - API returns clear validation and not-found errors.

### MVP-010 — Job CRUD API linked to load
- Type: Feature
- Area: backend
- Priority: P0
- Size: M
- Iteration: Week 4
- Milestone: M1
- Dependencies: MVP-008
- Description: Build job APIs with sequencing and relation to parent load.
- Acceptance criteria:
  - Job creation requires valid `load_id`.
  - Sequence ordering is persisted and returned consistently.
  - Job status defaults to `UNASSIGNED`.

### MVP-011 — Domain validation rules for load/job timelines
- Type: Task
- Area: backend
- Priority: P0
- Size: S
- Iteration: Week 4
- Milestone: M1
- Dependencies: MVP-009, MVP-010
- Description: Enforce required fields and time-window consistency for load and job records.
- Acceptance criteria:
  - Missing required fields are rejected with field-level messages.
  - Invalid pickup/dropoff window ordering is blocked.
  - Validation behavior is covered by automated tests.

### MVP-012 — Audit trail for load and job lifecycle actions
- Type: Task
- Area: security
- Priority: P1
- Size: M
- Iteration: Week 4
- Milestone: M1
- Dependencies: MVP-009, MVP-010
- Description: Capture who changed key domain entities and when for compliance and operations review.
- Acceptance criteria:
  - Create/update lifecycle actions emit audit entries.
  - Audit entries contain actor, entity, action, and timestamp.
  - Audit retrieval endpoint or query path is documented.

### MVP-013 — [Epic] Dispatch and assignment lifecycle
- Type: Epic
- Area: dispatch
- Priority: P0
- Size: XL
- Iteration: Weeks 5-6
- Milestone: M1
- Dependencies: MVP-007
- Description: Implement assignment lifecycle from pending dispatch to accept/reject/expire outcomes.
- Acceptance criteria:
  - Dispatchers can assign jobs to drivers.
  - Drivers can accept or reject assignments.
  - Expired assignments return jobs to actionable queue.

### MVP-014 — Driver directory and availability API
- Type: Feature
- Area: dispatch
- Priority: P1
- Size: M
- Iteration: Week 5
- Milestone: M1
- Dependencies: MVP-008
- Description: Build driver identity, availability, and assignment eligibility endpoints.
- Acceptance criteria:
  - Driver profiles are queryable by dispatcher workflow.
  - Availability state can be toggled and persisted.
  - Inactive/unavailable drivers are excluded from dispatch suggestions.

### MVP-015 — Dispatch endpoint to create pending assignments
- Type: Feature
- Area: dispatch
- Priority: P0
- Size: M
- Iteration: Week 5
- Milestone: M1
- Dependencies: MVP-014, MVP-010
- Description: Add dispatch API to create `PENDING` assignments and tie them to target drivers.
- Acceptance criteria:
  - Dispatch endpoint creates assignment with `PENDING` state.
  - Existing assignment conflicts are prevented.
  - Dispatch action logs actor and context metadata.

### MVP-016 — Assignment accept/reject/expire API
- Type: Feature
- Area: dispatch
- Priority: P0
- Size: M
- Iteration: Week 6
- Milestone: M1
- Dependencies: MVP-015
- Description: Implement assignment response endpoints and timeout/expiration behavior.
- Acceptance criteria:
  - Driver can accept or reject assignment exactly once.
  - Timeout transitions assignment to `EXPIRED`.
  - Job status updates correctly on each outcome.

### MVP-017 — Transition guards and idempotency protections
- Type: Task
- Area: backend
- Priority: P0
- Size: M
- Iteration: Week 6
- Milestone: M1
- Dependencies: MVP-015, MVP-016, MVP-011
- Description: Enforce legal state transitions and prevent duplicate writes from retries.
- Acceptance criteria:
  - Invalid state transitions are rejected with deterministic codes.
  - Idempotency keys prevent duplicate assignment state changes.
  - Replayed requests return original response shape.

### MVP-018 — Dispatch lifecycle integration tests and API docs
- Type: Task
- Area: qa-release
- Priority: P0
- Size: M
- Iteration: Week 6
- Milestone: M1
- Dependencies: MVP-009, MVP-010, MVP-015, MVP-016, MVP-017, MVP-012
- Description: Complete end-to-end backend verification and publish API contract docs for web/mobile clients.
- Acceptance criteria:
  - Automated integration suite covers create -> dispatch -> accept/reject lifecycle.
  - API docs include request/response examples and error cases.
  - M1 readiness demo is possible using documented endpoints.

## M2 — Dispatcher web experience (Weeks 7-9)
### MVP-019 — [Epic] Dispatcher portal workflows
- Type: Epic
- Area: web-dispatcher
- Priority: P0
- Size: XL
- Iteration: Weeks 7-9
- Milestone: M2
- Dependencies: MVP-018
- Description: Deliver dispatcher-facing web workflows for load/job management and dispatch operations.
- Acceptance criteria:
  - Dispatcher can create, view, and edit loads/jobs through UI.
  - Dispatcher can assign and reassign jobs to drivers.
  - Activity timeline shows key operational events.

### MVP-020 — Dispatcher web shell and authenticated navigation
- Type: Feature
- Area: web-dispatcher
- Priority: P0
- Size: M
- Iteration: Week 7
- Milestone: M2
- Dependencies: MVP-019, MVP-005
- Description: Build app shell with authentication gate, role-aware navigation, and layout scaffolding.
- Acceptance criteria:
  - Unauthenticated users are redirected to sign-in.
  - Dispatcher role can access operations pages.
  - Navigation and route guards are covered by UI tests.

### MVP-021 — Load/job list and detail screens
- Type: Feature
- Area: web-dispatcher
- Priority: P0
- Size: M
- Iteration: Week 7
- Milestone: M2
- Dependencies: MVP-020, MVP-009, MVP-010
- Description: Implement searchable list and detail pages for loads/jobs.
- Acceptance criteria:
  - Lists show status and key schedule attributes.
  - Detail pages render full record information.
  - Empty/error/loading states are user-friendly.

### MVP-022 — Load/job create and edit forms
- Type: Feature
- Area: web-dispatcher
- Priority: P0
- Size: M
- Iteration: Week 8
- Milestone: M2
- Dependencies: MVP-021, MVP-011
- Description: Add form workflows for creating and updating loads/jobs with field validation feedback.
- Acceptance criteria:
  - Required field and timeline errors are surfaced inline.
  - Successful save updates list/detail views.
  - Form behavior supports keyboard-first operation.

### MVP-023 — Dispatch board with assign/reassign actions
- Type: Feature
- Area: dispatch
- Priority: P0
- Size: L
- Iteration: Week 9
- Milestone: M2
- Dependencies: MVP-021, MVP-015, MVP-016
- Description: Build dispatch board UI for assignment decisions and queue management.
- Acceptance criteria:
  - Dispatcher can assign unassigned jobs to available drivers.
  - Reassignment flow is audited and visible.
  - Assignment outcomes refresh board status accurately.

### MVP-024 — Operations activity timeline
- Type: Task
- Area: web-dispatcher
- Priority: P1
- Size: M
- Iteration: Week 9
- Milestone: M2
- Dependencies: MVP-023, MVP-012
- Description: Display chronological events for dispatch and status milestones on web UI.
- Acceptance criteria:
  - Timeline includes who/what/when for key events.
  - Event order remains consistent across refreshes.
  - Timeline filters by load or job context.

## M3 — Driver mobile workflow reliability (Weeks 10-14)
### MVP-025 — [Epic] Driver mobile app core workflows
- Type: Epic
- Area: mobile-driver
- Priority: P0
- Size: XL
- Iteration: Weeks 10-14
- Milestone: M3
- Dependencies: MVP-018
- Description: Deliver driver app flows for assignments and status updates with robust retry behavior.
- Acceptance criteria:
  - Driver can authenticate and view assigned jobs.
  - Driver can accept/reject and update job status.
  - Offline retry path preserves updates without duplication.

### MVP-026 — Mobile authentication and session management
- Type: Feature
- Area: mobile-driver
- Priority: P0
- Size: M
- Iteration: Week 10
- Milestone: M3
- Dependencies: MVP-025, MVP-005
- Description: Implement secure mobile sign-in and token/session handling.
- Acceptance criteria:
  - Driver login/logout is functional.
  - Expired sessions redirect to re-auth safely.
  - Sensitive tokens are stored using platform secure storage.

### MVP-027 — Driver job inbox and detail view
- Type: Feature
- Area: mobile-driver
- Priority: P0
- Size: M
- Iteration: Week 11
- Milestone: M3
- Dependencies: MVP-026, MVP-015
- Description: Show assigned jobs, key details, and next required action.
- Acceptance criteria:
  - Inbox lists active assignments with current state.
  - Detail screen shows pickup/dropoff and timeline context.
  - Pull-to-refresh/state sync works against backend.

### MVP-028 — Assignment accept/reject flow and push handling
- Type: Feature
- Area: mobile-driver
- Priority: P0
- Size: M
- Iteration: Week 12
- Milestone: M3
- Dependencies: MVP-027, MVP-016
- Description: Implement assignment response UX and push-notification deep-link behavior.
- Acceptance criteria:
  - Driver can accept/reject directly from app.
  - Notification opens relevant assignment detail.
  - Response action reflects backend state within defined SLA.

### MVP-029 — Job status actions and backend integration
- Type: Feature
- Area: mobile-driver
- Priority: P0
- Size: L
- Iteration: Week 13
- Milestone: M3
- Dependencies: MVP-027, MVP-017
- Description: Add status progression actions (`EN_ROUTE`, `ARRIVED`, `LOADED`, `IN_TRANSIT`, `DELIVERED`) and API integration.
- Acceptance criteria:
  - Only valid next status actions are enabled.
  - Status submission includes timestamp and contextual metadata.
  - Duplicate taps do not create duplicate status events.

### MVP-030 — Offline queue, retry, and conflict handling
- Type: Feature
- Area: mobile-driver
- Priority: P0
- Size: L
- Iteration: Week 14
- Milestone: M3
- Dependencies: MVP-029
- Description: Queue driver updates offline and replay safely on reconnection.
- Acceptance criteria:
  - Offline updates persist across app restarts.
  - Replay honors idempotency and ordering guarantees.
  - Conflict resolution UX is defined for rejected transitions.

## M4 — Real-time visibility (Weeks 15-16)
### MVP-031 — [Epic] Real-time status pipeline and live board
- Type: Epic
- Area: realtime
- Priority: P0
- Size: L
- Iteration: Weeks 15-16
- Milestone: M4
- Dependencies: MVP-030, MVP-024
- Description: Provide low-latency status fanout from driver actions to dispatcher UI.
- Acceptance criteria:
  - Status events are published and consumed through event pipeline.
  - Dispatcher views update without manual refresh.
  - Ordering and duplication protections are in place.

### MVP-032 — Status event pipeline and pub/sub integration
- Type: Feature
- Area: realtime
- Priority: P0
- Size: M
- Iteration: Week 15
- Milestone: M4
- Dependencies: MVP-029, MVP-031
- Description: Implement event publishing/consuming for status updates with durable processing semantics.
- Acceptance criteria:
  - Each accepted status update emits exactly one durable event.
  - Consumers process events with retry and poison-message handling.
  - Event payload schema is versioned and documented.

### MVP-033 — Dispatcher live board and timeline streaming updates
- Type: Feature
- Area: web-dispatcher
- Priority: P0
- Size: M
- Iteration: Week 16
- Milestone: M4
- Dependencies: MVP-032, MVP-023
- Description: Connect web UI to real-time channel and render live job state changes.
- Acceptance criteria:
  - Board and timeline update within target latency.
  - Out-of-order events are handled gracefully in UI.
  - Connection drops auto-recover with backfill sync.

### MVP-034 — Real-time latency monitoring and alerting
- Type: Task
- Area: realtime
- Priority: P1
- Size: S
- Iteration: Week 16
- Milestone: M4
- Dependencies: MVP-032, MVP-006
- Description: Measure end-to-end status latency and alert when p95 exceeds target.
- Acceptance criteria:
  - Latency metrics are collected from submit-to-UI pipeline.
  - Dashboard shows p50/p95 trends.
  - Alert rule configured for >2s p95 breach threshold.

## M5 — Billing export (Week 17)
### MVP-035 — [Epic] Billing readiness and export workflows
- Type: Epic
- Area: billing
- Priority: P0
- Size: L
- Iteration: Week 17
- Milestone: M5
- Dependencies: MVP-018
- Description: Convert completed operations records into export-ready billing outputs.
- Acceptance criteria:
  - Billable readiness is deterministically computed.
  - Finance can generate and download export files.
  - Export runs are auditable and repeatable.

### MVP-036 — Billing-ready rules engine
- Type: Feature
- Area: billing
- Priority: P0
- Size: M
- Iteration: Week 17
- Milestone: M5
- Dependencies: MVP-035, MVP-029
- Description: Mark jobs/loads as billable once required completion criteria are met.
- Acceptance criteria:
  - Billable status requires completed delivery conditions.
  - Rules are test-covered for happy and edge cases.
  - Non-billable records include reason codes.

### MVP-037 — CSV/JSON export generation and storage
- Type: Feature
- Area: billing
- Priority: P0
- Size: M
- Iteration: Week 17
- Milestone: M5
- Dependencies: MVP-036
- Description: Implement export generation pipeline with file persistence.
- Acceptance criteria:
  - Export supports CSV and JSON formats.
  - Export file includes required finance fields and totals.
  - Export metadata is saved with run status.

### MVP-038 — Export history and retrieval in dispatcher UI
- Type: Feature
- Area: billing
- Priority: P1
- Size: S
- Iteration: Week 17
- Milestone: M5
- Dependencies: MVP-037, MVP-020
- Description: Add billing export history list and download actions for authorized users.
- Acceptance criteria:
  - Export list includes status, creator, timestamp, and format.
  - Authorized users can download generated files.
  - Failed exports show actionable error context.

## M6 — Hardening (Weeks 18-19)
### MVP-039 — [Epic] Security, reliability, observability hardening
- Type: Epic
- Area: security
- Priority: P0
- Size: L
- Iteration: Weeks 18-19
- Milestone: M6
- Dependencies: MVP-034, MVP-038
- Description: Strengthen controls, failure handling, and observability for pilot readiness.
- Acceptance criteria:
  - Access controls are validated against role and tenant boundaries.
  - Async pathways support retries and dead-letter handling.
  - Operational dashboards and alerts support on-call troubleshooting.

### MVP-040 — RBAC hardening and tenant access checks
- Type: Task
- Area: security
- Priority: P0
- Size: M
- Iteration: Week 18
- Milestone: M6
- Dependencies: MVP-039, MVP-005
- Description: Enforce least privilege and tenant data isolation for all critical endpoints.
- Acceptance criteria:
  - Cross-tenant data access attempts are blocked.
  - Permission matrix tests pass for all core roles.
  - Security review checklist is complete for MVP endpoints.

### MVP-041 — Retry, dead-letter, and replay handling for async flows
- Type: Task
- Area: realtime
- Priority: P0
- Size: M
- Iteration: Week 19
- Milestone: M6
- Dependencies: MVP-039, MVP-032, MVP-037
- Description: Add robust retry and dead-letter support for event and export pipelines.
- Acceptance criteria:
  - Configurable retry policies exist for transient failures.
  - Poison events are routed to dead-letter queue with metadata.
  - Replay tooling/process is documented and validated.

### MVP-042 — Observability dashboards, tracing, and alert tuning
- Type: Task
- Area: platform
- Priority: P1
- Size: M
- Iteration: Week 19
- Milestone: M6
- Dependencies: MVP-039, MVP-006, MVP-041
- Description: Expand telemetry to include tracing and operations dashboards across services.
- Acceptance criteria:
  - Distributed trace IDs propagate across core services.
  - Dashboards include dispatch success, status lag, export failures.
  - Alert thresholds tuned to reduce false positives.

### MVP-043 — Resilience and failure-mode test suite
- Type: Task
- Area: qa-release
- Priority: P0
- Size: M
- Iteration: Week 19
- Milestone: M6
- Dependencies: MVP-040, MVP-041, MVP-042
- Description: Validate behavior under failure conditions before pilot.
- Acceptance criteria:
  - Test suite covers network disruption, duplicate events, and partial outages.
  - Recovery runbooks are validated in staging drills.
  - Critical severity defects are triaged and resolved or accepted with mitigation.

## M7 — QA, UAT, and pilot launch (Weeks 20-22)
### MVP-044 — [Epic] QA/UAT and pilot launch readiness
- Type: Epic
- Area: qa-release
- Priority: P0
- Size: L
- Iteration: Weeks 20-22
- Milestone: M7
- Dependencies: MVP-043
- Description: Complete final quality gates and execute controlled pilot rollout.
- Acceptance criteria:
  - End-to-end test pass supports MVP scope.
  - UAT sign-off is documented.
  - Pilot launch and hypercare plans are executed.

### MVP-045 — End-to-end QA regression and defect triage
- Type: Task
- Area: qa-release
- Priority: P0
- Size: M
- Iteration: Week 20
- Milestone: M7
- Dependencies: MVP-044, MVP-030, MVP-033, MVP-038
- Description: Run full regression suite and triage defects for release candidate readiness.
- Acceptance criteria:
  - Critical and high-priority defects are resolved before RC.
  - Regression report covers all MVP workflows.
  - Release candidate build is tagged for UAT.

### MVP-046 — UAT execution and sign-off checklist
- Type: Task
- Area: product
- Priority: P0
- Size: M
- Iteration: Week 21
- Milestone: M7
- Dependencies: MVP-045
- Description: Run user acceptance testing with operations and billing stakeholders.
- Acceptance criteria:
  - UAT scenarios cover dispatch, driver flows, real-time board, and billing exports.
  - Feedback and defects are logged with disposition.
  - Formal go/no-go sign-off is recorded.

### MVP-047 — Pilot onboarding, runbooks, and support operations
- Type: Task
- Area: qa-release
- Priority: P1
- Size: M
- Iteration: Week 21
- Milestone: M7
- Dependencies: MVP-046, MVP-042
- Description: Prepare pilot users and support team with onboarding docs and operational runbooks.
- Acceptance criteria:
  - Pilot user onboarding checklist is complete.
  - Support runbook includes escalation paths and SLAs.
  - Incident communication templates are prepared.

### MVP-048 — Launch hypercare and post-launch review
- Type: Task
- Area: qa-release
- Priority: P0
- Size: M
- Iteration: Week 22
- Milestone: M7
- Dependencies: MVP-047
- Description: Execute pilot launch monitoring, issue triage, and post-launch retrospective.
- Acceptance criteria:
  - Hypercare monitoring is active during launch window.
  - Pilot issues are triaged within agreed response times.
  - Post-launch report documents outcomes and next-phase recommendations.
