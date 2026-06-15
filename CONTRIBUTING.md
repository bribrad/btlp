# Contributing to BTLP

## Branching strategy
- Base all work from `main`.
- Use short-lived feature branches.
- Branch naming convention:
  - `feature/<short-description>`
  - `fix/<short-description>`
  - `chore/<short-description>`
  - `docs/<short-description>`
- Keep branch names lowercase and hyphenated (example: `feature/dispatch-board`).

## Commit and pull request conventions
- Open a pull request (PR) for every change; do not push directly to `main`.
- Keep PRs focused on a single concern.
- Link the corresponding issue in the PR description (example: `Closes #123`).
- Use clear PR titles:
  - `feat: <summary>`
  - `fix: <summary>`
  - `chore: <summary>`
  - `docs: <summary>`

## PR review rules
- At least one approving review is required before merge.
- All required checks must pass before merge.
- Resolve all review comments before merging.
- Prefer squash merge for linear history.

## Required checks for `main`
- `lint`
- `test`
- `build`

These checks are enforced by branch protection on `main`.

## Definition of ready-to-merge
- Branch is up to date with `main`.
- Required checks pass.
- Required approvals are present.
- Rollout and rollback notes are documented in the PR.
