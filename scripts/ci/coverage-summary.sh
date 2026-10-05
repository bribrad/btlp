#!/usr/bin/env bash
set -euo pipefail

# Prints one coverage table per surface, to stdout and to the GitHub job summary.
#
# Reports are produced by the test run itself (JaCoCo binds to the Maven test phase;
# `npm test` passes --coverage), so this script only reads what is already on disk. A
# missing report is reported as such and does not fail the build: it means the matching
# test job did not get far enough, and that failure is the one worth surfacing.

root_dir="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root_dir"

readonly JACOCO_CSV="backend/target/site/jacoco/jacoco.csv"
readonly VITEST_JSON="frontend/coverage/coverage-summary.json"

emit() {
  echo "$1"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    echo "$1" >>"$GITHUB_STEP_SUMMARY"
  fi
}

emit "## Coverage"
emit ""

# ── Backend (JaCoCo) ──────────────────────────────────────────────────────────

emit "### Backend (JaCoCo)"
emit ""
if [[ -f "$JACOCO_CSV" ]]; then
  # jacoco.csv columns are fixed:
  #   1 GROUP 2 PACKAGE 3 CLASS
  #   4 INSTRUCTION_MISSED 5 INSTRUCTION_COVERED
  #   6 BRANCH_MISSED      7 BRANCH_COVERED
  #   8 LINE_MISSED        9 LINE_COVERED
  #  10 COMPLEXITY_MISSED 11 COMPLEXITY_COVERED
  #  12 METHOD_MISSED     13 METHOD_COVERED
  summary="$(awk -F, 'NR > 1 {
      im += $4; ic += $5; bm += $6; bc += $7
      lm += $8; lc += $9; mm += $12; mc += $13
    }
    END {
      printf "| Metric | Covered | Total | %% |\n"
      printf "|---|---:|---:|---:|\n"
      printf "| Instructions | %d | %d | %.2f%% |\n", ic, ic + im, (ic + im) ? 100 * ic / (ic + im) : 0
      printf "| Branches | %d | %d | %.2f%% |\n",     bc, bc + bm, (bc + bm) ? 100 * bc / (bc + bm) : 0
      printf "| Lines | %d | %d | %.2f%% |\n",        lc, lc + lm, (lc + lm) ? 100 * lc / (lc + lm) : 0
      printf "| Methods | %d | %d | %.2f%% |\n",      mc, mc + mm, (mc + mm) ? 100 * mc / (mc + mm) : 0
    }' "$JACOCO_CSV")"
  emit "$summary"
else
  emit "_No JaCoCo report at \`${JACOCO_CSV}\` — the backend test phase did not complete._"
fi
emit ""

# ── Frontend (vitest / v8) ────────────────────────────────────────────────────

emit "### Frontend (vitest v8)"
emit ""
if [[ -f "$VITEST_JSON" ]]; then
  summary="$(python3 -c '
import json, sys

with open(sys.argv[1]) as handle:
    total = json.load(handle)["total"]

rows = ["| Metric | Covered | Total | % |", "|---|---:|---:|---:|"]
for key, label in (
    ("statements", "Statements"),
    ("branches", "Branches"),
    ("lines", "Lines"),
    ("functions", "Functions"),
):
    entry = total[key]
    rows.append(
        "| {} | {} | {} | {:.2f}% |".format(
            label, entry["covered"], entry["total"], entry["pct"]
        )
    )
print("\n".join(rows))
' "$VITEST_JSON")"
  emit "$summary"
else
  emit "_No vitest coverage at \`${VITEST_JSON}\` — the frontend test run did not complete._"
fi
