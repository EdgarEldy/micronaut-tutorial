#!/usr/bin/env bash
# Starts the application and replays one scenario against it, so that the JVM build and the native
# executable can be shown to answer the same way.
#
# It exercises what compile-time Micronaut generates and GraalVM must keep working: JSON serialization,
# Bean Validation, JWT signing and validation, the @RequiresPermission AOP interceptor (a 403 proves it
# ran), the JWT blacklist validator (a 401 after logout), Micronaut Data queries, Flyway and the cache.
#
# Usage: scripts/smoke-test.sh <label> <command> [args...]
# Expects the packaged configuration in the environment (MICRONAUT_ENVIRONMENTS=prod, DB_URL, DB_USER,
# DB_PASSWORD, JWT_*_LOCATION) plus APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD, which make
# the application create the first administrator at startup. Honours PORT (default 8091).
set -euo pipefail

label=$1
shift
port=${PORT:-8091}
base="http://localhost:${port}"
log="target/smoke-${label}.log"
admin_email=${APP_BOOTSTRAP_ADMIN_EMAIL:?APP_BOOTSTRAP_ADMIN_EMAIL is required}
admin_password=${APP_BOOTSTRAP_ADMIN_PASSWORD:?APP_BOOTSTRAP_ADMIN_PASSWORD is required}

MICRONAUT_SERVER_PORT="$port" "$@" >"$log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT

for _ in $(seq 1 600); do
  curl -sf "${base}/health" >/dev/null && break
  kill -0 "$pid" 2>/dev/null || { echo "Application exited early:" >&2; tail -n 40 "$log" >&2; exit 1; }
  sleep 0.2
done
curl -sf "${base}/health" >/dev/null || { echo "Application never became ready" >&2; tail -n 40 "$log" >&2; exit 1; }

failures=0
# request <expected status> <description> <curl args...>: prints the body on stdout for the caller.
request() {
  local expected=$1 what=$2
  shift 2
  local out code
  out=$(curl -s -w '\n%{http_code}' "$@")
  code=${out##*$'\n'}
  if [ "$code" != "$expected" ]; then
    echo "FAIL ${what}: expected ${expected}, got ${code}" >&2
    echo "${out%$'\n'*}" >&2
    failures=$((failures + 1))
  else
    echo "ok   ${what} (${code})" >&2
  fi
  printf '%s' "${out%$'\n'*}"
}
json=(-H 'Content-Type: application/json')

login() {
  request 200 "login $1" -X POST "${base}/api/v1/auth/login" "${json[@]}" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r '.data.accessToken'
}

admin_token=$(login "$admin_email" "$admin_password")
auth_admin=(-H "Authorization: Bearer ${admin_token}")

# Micronaut Data + Flyway + Serde: create a category and a product, then read the product twice (cache).
category_id=$(request 201 "create category" -X POST "${base}/api/v1/categories" "${json[@]}" "${auth_admin[@]}" \
  -d '{"categoryName":"Smoke"}' | jq -r '.data.id')
product_id=$(request 201 "create product" -X POST "${base}/api/v1/products" "${json[@]}" "${auth_admin[@]}" \
  -d "{\"categoryId\":${category_id},\"productName\":\"Widget\",\"unitPrice\":10.50}" | jq -r '.data.id')
first=$(request 200 "read product" "${base}/api/v1/products/${product_id}" "${auth_admin[@]}" | jq -c '.data')
second=$(request 200 "read product again (cache)" "${base}/api/v1/products/${product_id}" "${auth_admin[@]}" | jq -c '.data')
[ "$first" = "$second" ] || { echo "FAIL cached read differs" >&2; failures=$((failures + 1)); }

# The computed total of an order: 3 x 10.50.
customer_id=$(request 201 "create customer" -X POST "${base}/api/v1/customers" "${json[@]}" "${auth_admin[@]}" \
  -d '{"firstName":"Smoke","lastName":"Tester"}' | jq -r '.data.id')
total=$(request 201 "create order" -X POST "${base}/api/v1/orders" "${json[@]}" "${auth_admin[@]}" \
  -d "{\"customerId\":${customer_id},\"productId\":${product_id},\"quantity\":3}" | jq -r '.data.total')
[ "$total" = "31.5" ] || [ "$total" = "31.50" ] || { echo "FAIL order total was ${total}" >&2; failures=$((failures + 1)); }

# Registration, activation (the raw token only exists in the log), login.
email="smoke-$$@example.com"
request 201 "register" -X POST "${base}/api/v1/auth/register" "${json[@]}" \
  -d "{\"firstName\":\"Simple\",\"lastName\":\"User\",\"email\":\"${email}\",\"password\":\"Password123!\"}" >/dev/null
activation=$(grep -o 'Activation token for user [0-9]*: [A-Za-z0-9_-]*' "$log" | tail -n 1 | awk '{print $NF}')
request 200 "activate" "${base}/api/v1/auth/activate-account?token=${activation}" >/dev/null
user_token=$(login "$email" "Password123!")
auth_user=(-H "Authorization: Bearer ${user_token}")
request 200 "me" "${base}/api/v1/auth/me" "${auth_user[@]}" >/dev/null

# The compile-time @RequiresPermission interceptor: a user without ROLE:READ is refused, an admin is not.
request 403 "roles without permission (AOP interceptor)" "${base}/api/v1/roles" "${auth_user[@]}" >/dev/null
request 200 "roles as admin" "${base}/api/v1/roles" "${auth_admin[@]}" >/dev/null

# The JWT blacklist validator: after logout the same token is rejected.
request 200 "logout" -X POST "${base}/api/v1/auth/logout" "${auth_user[@]}" >/dev/null
request 401 "me after logout (blacklist)" "${base}/api/v1/auth/me" "${auth_user[@]}" >/dev/null

# A validation error and an unknown route keep the ApiResponse format.
request 400 "validation error" -X POST "${base}/api/v1/categories" "${json[@]}" "${auth_admin[@]}" -d '{"categoryName":""}' >/dev/null
request 404 "unknown route" "${base}/nope" >/dev/null

if [ "$failures" -ne 0 ]; then
  echo "${label}: ${failures} check(s) failed" >&2
  exit 1
fi
echo "${label}: all checks passed"
