#!/usr/bin/env bash
set -euo pipefail

root_dir="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$root_dir"

ran_any=false

run_npm_script() {
  local surface="$1"
  local script_name="$2"

  if [[ ! -f "${surface}/package.json" ]]; then
    return 1
  fi

  echo "Running npm ${script_name} for ${surface}"
  pushd "$surface" >/dev/null
  if [[ -f package-lock.json ]]; then
    npm ci
  else
    npm install
  fi
  npm run "${script_name}"
  popd >/dev/null
  return 0
}

run_maven_goal() {
  local surface="$1"
  local goal="$2"

  if [[ ! -f "${surface}/pom.xml" ]]; then
    return 1
  fi

  echo "Running mvn ${goal} for ${surface}"
  pushd "$surface" >/dev/null
  mvn -B "${goal}"
  popd >/dev/null
  return 0
}

if [[ -d "backend" ]]; then
  if run_maven_goal backend validate || run_npm_script backend lint; then
    ran_any=true
  else
    echo "::error::backend exists but does not expose a supported Maven or npm lint command."
    exit 1
  fi
fi

if [[ -d "frontend" ]]; then
  if run_npm_script frontend lint || run_maven_goal frontend validate; then
    ran_any=true
  else
    echo "::error::frontend exists but does not expose a supported npm or Maven lint command."
    exit 1
  fi
fi

if [[ "$ran_any" == "false" ]]; then
  echo "No backend/frontend surfaces found; lint step intentionally skipped."
fi
