#!/usr/bin/env bash
set -euo pipefail

# Runs the Playwright end-to-end suite against the real stack: PostgreSQL, the Spring Boot
# API on :8080, and the Vite dev server on :5173 (started by Playwright's own webServer
# config). Usable locally and in CI; everything it starts, it stops again via the EXIT trap.
#
# The specs are not hermetic by design — they drive the browser against a live API. Two of
# them (src/test/e2e/loads.spec.ts) read pre-existing records, so this script seeds a known
# load and its jobs through the public REST API rather than by writing SQL. Seeding through
# the API keeps the fixture honest: if create-load breaks, seeding fails loudly here instead
# of leaving the suite asserting against rows no endpoint could have produced.

root_dir="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root_dir"

readonly COMPOSE_FILE="backend/docker-compose.db.yml"
readonly API_BASE="http://localhost:8080/api/v1"
readonly HEALTH_URL="http://localhost:8080/actuator/health"
readonly DISPATCHER="dispatcher:dispatcher-pass"

backend_pid=""

cleanup() {
  local status=$?
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    echo "Stopping backend (pid ${backend_pid})"
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
  echo "Stopping PostgreSQL"
  # -v so a rerun starts from a clean schema; the e2e specs create records as they go.
  docker compose -f "$COMPOSE_FILE" down -v >/dev/null 2>&1 || true
  if (( status != 0 )) && [[ -f backend/e2e-backend.log ]]; then
    echo "::group::backend log (last 80 lines)"
    tail -80 backend/e2e-backend.log
    echo "::endgroup::"
  fi
  return "$status"
}
trap cleanup EXIT

# Blocks until $1 returns success, or fails after $2 attempts spaced a second apart.
wait_for() {
  local description="$1" attempts="$2"
  shift 2
  local i
  for (( i = 1; i <= attempts; i++ )); do
    if "$@" >/dev/null 2>&1; then
      echo "${description} ready after ${i}s"
      return 0
    fi
    sleep 1
  done
  echo "::error::${description} did not become ready within ${attempts}s"
  return 1
}

# ── PostgreSQL ────────────────────────────────────────────────────────────────

echo "Starting PostgreSQL"
docker compose -f "$COMPOSE_FILE" up -d
wait_for "PostgreSQL" 60 docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U btlp -d btlp

# ── Backend ───────────────────────────────────────────────────────────────────

# Package without tests: the `test` CI job already ran them, and re-running the
# Testcontainers suite here would double the slowest part of the pipeline.
echo "Building backend"
(cd backend && mvn -B -q package -DskipTests)

jar="$(find backend/target -maxdepth 1 -name '*.jar' ! -name '*.original' -print -quit)"
if [[ -z "$jar" ]]; then
  echo "::error::No backend jar found in backend/target after packaging."
  exit 1
fi

echo "Starting backend from ${jar}"
# Liquibase applies the migrations against the fresh container on startup.
java -jar "$jar" >backend/e2e-backend.log 2>&1 &
backend_pid=$!

wait_for "Backend" 120 curl -fsS "$HEALTH_URL"

# ── Seed ──────────────────────────────────────────────────────────────────────

# Reads the "id" of a JSON object on stdin. python3 rather than jq: it is already a CI
# prerequisite (actions/setup-python) and present on developer machines, and it parses by
# key instead of relying on field order.
read_id() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

api_post() {
  local path="$1" body="$2"
  curl -fsS -u "$DISPATCHER" -X POST "${API_BASE}${path}" \
    -H 'Content-Type: application/json' -d "$body"
}

echo "Seeding a load and its jobs"
# Origin "Chicago, IL" matches the `search loads` assertion in loads.spec.ts (the API
# search is ILIKE, so the spec's lowercase "chicago" hits this row).
load_id="$(api_post /loads '{
  "customerId": "ACME",
  "origin": "Chicago, IL",
  "destination": "Dallas, TX",
  "pickupWindowStart": "2026-03-03T14:00:00Z",
  "pickupWindowEnd": "2026-03-03T18:00:00Z",
  "dropoffWindowStart": "2026-03-05T14:00:00Z",
  "dropoffWindowEnd": "2026-03-05T18:00:00Z",
  "rateAmount": 1250.50,
  "rateCurrency": "USD",
  "notes": "Seeded by scripts/ci/e2e.sh"
}' | read_id)"
echo "  load ${load_id}"

# Both legs: the spec filters the jobs list by DROPOFF and expects a row back.
api_post /jobs "{\"loadId\":\"${load_id}\",\"jobType\":\"PICKUP\",\"sequence\":1,\"scheduledAt\":\"2026-03-03T15:00:00Z\"}" >/dev/null
api_post /jobs "{\"loadId\":\"${load_id}\",\"jobType\":\"DROPOFF\",\"sequence\":2,\"scheduledAt\":\"2026-03-05T15:00:00Z\"}" >/dev/null
echo "  jobs PICKUP + DROPOFF"

# ── Playwright ────────────────────────────────────────────────────────────────

cd frontend
if [[ -f package-lock.json ]]; then
  npm ci
else
  npm install
fi

echo "Installing Playwright browser"
# --with-deps installs the system libraries Chromium needs on a bare runner, but it is
# Linux-only — on macOS the browser download alone is enough.
if [[ "$(uname -s)" == "Linux" ]]; then
  npx playwright install --with-deps chromium
else
  npx playwright install chromium
fi

echo "Running Playwright suite"
npm run test:e2e
