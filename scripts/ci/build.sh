#!/usr/bin/env bash
set -euo pipefail

root_dir="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root_dir"

ran_any=false

# Exit status meaning "this runner does not apply to this surface" — distinct from a real
# failure, so a tool that ran and failed can never be mistaken for one that was not there.
readonly NOT_APPLICABLE=64

run_npm_script() {
  local surface="$1"
  local script_name="$2"
  local status=0

  if [[ ! -f "${surface}/package.json" ]]; then
    return "$NOT_APPLICABLE"
  fi

  echo "Running npm ${script_name} for ${surface}"
  pushd "$surface" >/dev/null
  if [[ -f package-lock.json ]]; then
    npm ci || status=$?
  else
    npm install || status=$?
  fi
  if (( status == 0 )); then
    npm run "${script_name}" || status=$?
  fi
  popd >/dev/null
  return "$status"
}

run_maven_package() {
  local surface="$1"
  local status=0

  if [[ ! -f "${surface}/pom.xml" ]]; then
    return "$NOT_APPLICABLE"
  fi

  echo "Running mvn package -DskipTests for ${surface}"
  pushd "$surface" >/dev/null
  mvn -B package -DskipTests || status=$?
  popd >/dev/null
  return "$status"
}

# Runs the first applicable candidate for a surface. Falls through to the next candidate only
# when a runner reports NOT_APPLICABLE; a runner that ran and failed propagates its status, so a
# failing suite cannot be reported as a passing check.
try_candidates() {
  local missing_message="$1"
  shift
  local candidate status

  for candidate in "$@"; do
    status=0
    # shellcheck disable=SC2086  # candidates are internal and split on purpose
    $candidate || status=$?
    if (( status != NOT_APPLICABLE )); then
      return "$status"
    fi
  done

  echo "::error::${missing_message}"
  return 1
}

if [[ -d "backend" ]]; then
  try_candidates "backend exists but does not expose a supported Maven or npm build command." \
    "run_maven_package backend" "run_npm_script backend build"
  ran_any=true
fi

if [[ -d "frontend" ]]; then
  try_candidates "frontend exists but does not expose a supported npm or Maven build command." \
    "run_npm_script frontend build" "run_maven_package frontend"
  ran_any=true
fi

if [[ "$ran_any" == "false" ]]; then
  echo "No backend/frontend surfaces found; build step intentionally skipped."
fi
