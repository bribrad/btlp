# Operations Activity Timeline API

Backend API contract for the dispatcher portal's activity timeline (issue #24): a chronological,
read-only feed of dispatch and status milestones, scopable to a load or a single job.

All endpoints are under `/api/v1`, require HTTP Basic auth, and return the shared error envelope
`{ "error": "<CODE>", "message": "<detail>" }` on failure.

## Where the events come from

The timeline is a read model over `audit_events` — the same rows that `POST`/`PUT` on loads and jobs
and every dispatch transition already write inside their own transaction. Nothing publishes to the
timeline separately, so an event exists exactly when the change it describes committed.

### Action vocabulary

`action` names the transition rather than merely that something changed, so the UI can say "Alice
Rivera accepted" instead of "assignment updated":

| Action | Entity | Written by |
| --- | --- | --- |
| `CREATE` / `UPDATE` | `LOAD`, `JOB` | load and job create/edit |
| `STATUS_CHANGE` | `JOB` | a job status moved as a side effect of a dispatch transition |
| `ASSIGN` | `ASSIGNMENT` | dispatcher dispatched a driver |
| `REASSIGN` | `ASSIGNMENT` | the replacement assignment created by a reassignment |
| `CANCEL` | `ASSIGNMENT` | dispatcher pulled the assignment back (also the canceled half of a reassignment) |
| `ACCEPT` / `REJECT` / `COMPLETE` | `ASSIGNMENT` | the driver's response |
| `EXPIRE` | `ASSIGNMENT` | the timeout sweep, recorded with actor `system` |

`detail` carries the audited entity's resulting status or state (`ASSIGNED`, `CANCELED`, …), or
`null` for an action that has none. It is what a `STATUS_CHANGE` row renders.

`actor` comes from `Authentication#getName()` at the time of the change and is never taken from a
request body; the background expiry sweep records `system`.

### Ordering

`audit_events.occurred_at` defaults to `now()`, which in PostgreSQL is **transaction start time** —
so the several events one dispatch action writes all share a timestamp. Each row also carries a
`seq` allocated per `INSERT`, and both `/api/v1/activity` and `/api/v1/audit` order by it
descending. Ordering is therefore both correct within a transaction and identical on every read.
Clients must not re-sort on `occurredAt`.

## List the timeline

`GET /api/v1/activity` — requires `DISPATCHER` or `ADMIN`.

| Param | Default | Notes |
| --- | --- | --- |
| `loadId` | — | Widens to every job and assignment on that load. |
| `jobId` | — | Narrows to a single leg. Supplying both intersects them. |
| `page` | `0` | Clamped to `>= 0`. |
| `size` | `20` | Clamped to `1..100`. |

Returns the standard `PagedResponse` envelope. An unknown `loadId`/`jobId` yields an empty page, not
a `404` — the filter is a scope, not a lookup.

```json
{
  "id": "c0ffee00-0000-4000-8000-000000000001",
  "sequence": 42,
  "occurredAt": "2026-07-15T18:00:00Z",
  "actor": "dispatcher",
  "entityType": "ASSIGNMENT",
  "entityId": "5b1c2f2e-6c1a-4e0b-9d0e-2a9f9d4c1a11",
  "action": "ASSIGN",
  "detail": "PENDING",
  "loadId": "7c3b1a9e-4d2f-4b6a-9e8d-1f0a2b3c4d5e",
  "origin": "Chicago, IL",
  "destination": "Columbus, OH",
  "jobId": "0f2a7b9c-1d3e-4a5b-8c7d-6e5f4a3b2c1d",
  "jobType": "PICKUP",
  "jobSequence": 1,
  "assignmentId": "5b1c2f2e-6c1a-4e0b-9d0e-2a9f9d4c1a11",
  "driverId": "9a8b7c6d-5e4f-3a2b-1c0d-0e1f2a3b4c5d",
  "driverName": "Alice Rivera"
}
```

Context fields are resolved server-side by walking assignment → job → load, so a client can scope to
a load without first fetching that load's jobs and assignments. They narrow with the entity type — a
`LOAD` event carries no job, only an `ASSIGNMENT` event carries a driver — and are also `null` once
the referenced row is gone; the event itself still belongs on the timeline.

- `400 VALIDATION_ERROR` — `loadId`/`jobId` is not a UUID.
- `401 UNAUTHORIZED` / `403 FORBIDDEN` — see the role rule above.

## Related: the raw audit trail

`GET /api/v1/audit?entityType=&entityId=` remains the unjoined view of the same table, filtered by
exact entity. It now also returns `sequence` and `detail`. Use `/api/v1/activity` when you want the
load/job context resolved for you.

## Try it

Continuing from the walkthrough in [dispatch-lifecycle-api.md](./dispatch-lifecycle-api.md):

```bash
BASE=http://localhost:8080/api/v1

# Everything that has happened, newest first
curl -s -u dispatcher:dispatcher-pass "$BASE/activity" | jq

# Scoped to one load — includes its jobs and their assignments
curl -s -u dispatcher:dispatcher-pass "$BASE/activity?loadId=$LOAD_ID" | jq

# Scoped to a single leg
curl -s -u dispatcher:dispatcher-pass "$BASE/activity?jobId=$JOB_ID" | jq
```
