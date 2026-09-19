#!/usr/bin/env bash
# Starts the application, waits until it is ready, sends a fixed request load, then prints one
# markdown table row: time until /health answers, the startup time Micronaut itself reports, and the
# resident memory of the process after the load.
#
# Usage: scripts/measure-startup.sh <label> <command> [args...]
# Expects the packaged configuration in the environment (MICRONAUT_ENVIRONMENTS=prod, DB_URL, DB_USER,
# DB_PASSWORD, JWT_PRIVATE_KEY_LOCATION, JWT_PUBLIC_KEY_LOCATION) and honours PORT (default 8090) and
# REQUESTS (default 300).
set -euo pipefail

label=$1
shift
port=${PORT:-8090}
requests=${REQUESTS:-300}
log="target/measure-${label}.log"

# An environment variable works for a jar and for a native executable alike; a -D option placed after
# "-jar" would be handed to the program instead of the JVM and silently ignored.
MICRONAUT_SERVER_PORT="$port" "$@" >"$log" 2>&1 &
pid=$!
# Wait for the process to be gone, so the next measurement never finds the port still taken.
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT

start=$(date +%s%N)
until curl -sf "http://localhost:${port}/health" >/dev/null; do
  if [ $(( ($(date +%s%N) - start) / 1000000000 )) -gt 120 ]; then
    echo "The application was not ready after 120 s:" >&2
    tail -n 40 "$log" >&2
    exit 1
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "The application exited before becoming ready:" >&2
    tail -n 40 "$log" >&2
    exit 1
  fi
  sleep 0.02
done
ready_ms=$(( ($(date +%s%N) - start) / 1000000 ))

# The same load for every mode: a health probe, the OpenAPI document and a secured endpoint
# (answers 401), which together exercise routing, serialization and the security stack.
for i in $(seq 1 "$requests"); do
  case $((i % 3)) in
    0) path=/health ;;
    1) path=/swagger/micronaut-tutorial-0.1.yml ;;
    2) path=/api/v1/products ;;
  esac
  # 401 is the expected answer of the secured endpoint; a refused connection (000) or a 5xx means the
  # load did not exercise the application and the figures would be meaningless.
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}${path}")
  if [ "$code" = "000" ] || [ "$code" -ge 500 ]; then
    echo "Request ${i} to ${path} answered ${code}" >&2
    exit 1
  fi
done

reported=$(grep -o 'Startup completed in [0-9]*ms' "$log" | head -n 1 | grep -o '[0-9]*' || echo "n/a")
rss_mb=$(awk '/VmRSS/ {printf "%d", $2 / 1024}' "/proc/${pid}/status")

echo "| ${label} | ${ready_ms} | ${reported} | ${rss_mb} |"
