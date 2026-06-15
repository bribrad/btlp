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

for surface in backend frontend; do
  if [[ -d "$surface" ]]; then
    if run_npm_script "$surface" test; then
      ran_any=true
    else
      echo "::error::${surface} exists but does not expose package.json test script."
      exit 1
    fi
  fi
done

if [[ "$ran_any" == "false" ]]; then
  echo "No backend/frontend surfaces found; test step intentionally skipped."
fi
