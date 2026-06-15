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

run_maven_package() {
  local surface="$1"

  if [[ ! -f "${surface}/pom.xml" ]]; then
    return 1
  fi

  echo "Running mvn package -DskipTests for ${surface}"
  pushd "$surface" >/dev/null
  mvn -B package -DskipTests
  popd >/dev/null
  return 0
}

if [[ -d "backend" ]]; then
  if run_maven_package backend || run_npm_script backend build; then
    ran_any=true
  else
    echo "::error::backend exists but does not expose a supported Maven or npm build command."
    exit 1
  fi
fi

if [[ -d "frontend" ]]; then
  if run_npm_script frontend build || run_maven_package frontend; then
    ran_any=true
  else
    echo "::error::frontend exists but does not expose a supported npm or Maven build command."
    exit 1
  fi
fi

if [[ "$ran_any" == "false" ]]; then
  echo "No backend/frontend surfaces found; build step intentionally skipped."
fi
