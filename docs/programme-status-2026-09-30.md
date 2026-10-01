# BTLP — Programme Status Summary

**As of 30 September 2026.** Point-in-time stakeholder snapshot; supersedes earlier status reports
and will itself be superseded. For the engineering view of any capability named here, see
`architecture.md`, `dispatch-lifecycle-api.md`, and `activity-timeline-api.md`.

## Where we are

Two of seven milestones are complete. The platform's operational core — the data model, APIs, and
the dispatcher's web workspace — is built and working end to end. A dispatcher can today run a load
from creation through driver assignment to completion, and see a full history of who did what.

What does *not* yet exist is everything that touches a driver's phone, live updates, and billing.
The system is demonstrable and internally pilot-ready; it is **not yet production-ready**, primarily
because authentication is still development scaffolding.

| Milestone | Target | Status |
| --- | --- | --- |
| M1: Core Operations | 12 Jul 2026 | Complete (20 items) |
| M2: Dispatcher Experience | 14 Sep 2026 | Complete 27 Sep (6 items) |
| M3: Driver Mobile Experience | 18 Oct 2026 | Not started (8 items) |
| M4: Live Visibility | 1 Nov 2026 | Not started (4 items) |
| M5: Billing Export Readiness | 15 Nov 2026 | Not started (4 items) |
| M6: Reliability & Controls | 30 Nov 2026 | Not started (6 items) |
| M7: Pilot Readiness & Launch | 31 Dec 2026 | Not started (5 items) |

M2 closed roughly two weeks past its 14 September target.

## What a dispatcher can do today

- **Manage loads and jobs** — create, search, and edit loads and their constituent legs, with
  validation that catches impossible schedules before they are saved.
- **Dispatch drivers** — a three-lane board (*needs dispatch → awaiting acceptance → in progress*)
  showing every actionable job with its driver and a countdown on unanswered offers. Assign,
  reassign, or pull back an assignment.
- **See what happened** — a chronological activity feed recording who did what and when, filterable
  to a single load or leg, and embedded directly on each load and job record.

Behind this: a driver directory with availability, an assignment lifecycle with accept/reject/
completion and automatic timeout of unanswered offers, and an audit trail on every change.

## Engineering posture

- **227 automated tests** (123 backend, 104 frontend) run on every change; the pipeline blocks
  merges on lint, test, and build.
- **Staging deployment pipeline** in place.
- **Documented API contracts** for the dispatch lifecycle and the activity timeline, so the mobile
  app and any future integrator build against a written spec rather than reverse-engineering.
- Repeated mutations are protected against duplicate submission — a retried "assign driver" cannot
  create two assignments.

## What is deliberately not there yet

These are known and scheduled, not oversights.

1. **No driver mobile app.** The APIs a driver app needs exist, but there is no phone client — so
   drivers cannot yet receive or accept work. *This is the single largest remaining gap.* (M3)
2. **No road-side progress tracking.** The activity feed currently shows office-side actions.
   Milestones a driver reports *en route* — departed, arrived, loaded, delivered — are not captured
   yet; the database table is in place but nothing writes to it. Until this lands, the timeline
   shows dispatcher decisions, not shipment progress. (M3, issue #69)
3. **Authentication is scaffolding.** Logins are hardcoded development accounts. This must be
   replaced with a real identity provider before anyone outside the team touches the system.
   **Hard launch blocker.** (M6, issue #70)
4. **No live updates.** Dispatchers refresh to see changes rather than watching them arrive. (M4)
5. **No billing export.** (M5)

## Schedule risk

27 work items remain across four milestones in the three months to the 31 December pilot date. The
M3 mobile milestone is the critical path: M4 (live visibility) and M5 (billing) both depend on
drivers actually reporting status from the field, so **slippage in M3 compounds into two downstream
milestones.** M3's 18 October target is three weeks away with no work started.

## Decisions worth taking now

1. **Confirm the M3 start date and resourcing.** Mobile is the critical path and has not begun.
2. **Choose the identity provider.** This has a long lead time, blocks pilot onboarding, and nothing
   downstream can be security-reviewed until it is settled.
3. **Confirm the pilot scope.** If 31 December is firm, the honest options are to narrow pilot scope
   (e.g. defer billing export) or move the date — rather than compress quality work in M6.
