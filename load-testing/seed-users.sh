#!/usr/bin/env bash
# Seed N Auth0 test users for the load-test ROPC pool.
#
# Requires: curl. Reads config from load-testing/.env.
# Needs an M2M app authorized for the Auth0 Management API with scope
# create:users (set AUTH0_MGMT_CLIENT_ID / AUTH0_MGMT_CLIENT_SECRET in .env).
#
# Creates: <PREFIX>-1@<DOMAIN> .. <PREFIX>-<POOL_SIZE>@<DOMAIN>
# in the AUTH0_CONNECTION database connection, email_verified=true,
# tagged app_metadata.loadtest=true. Idempotent: existing users are skipped.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/.env}"
[ -f "$ENV_FILE" ] || { echo "✗ Missing $ENV_FILE (copy .env.example → .env)"; exit 1; }
set -a; . "$ENV_FILE"; set +a

: "${AUTH0_DOMAIN:?set in .env}"
: "${AUTH0_CONNECTION:?set in .env}"
: "${AUTH0_TEST_PASSWORD:?set in .env}"
POOL_SIZE="${POOL_SIZE:-50}"
PREFIX="${AUTH0_TEST_EMAIL_PREFIX:-loadtest}"
DOMAIN="${AUTH0_TEST_EMAIL_DOMAIN:-example.com}"

case "$AUTH0_TEST_PASSWORD" in *'<'*) echo "✗ Fill AUTH0_TEST_PASSWORD in .env"; exit 1;; esac

# Management API token: use a pasted AUTH0_MGMT_TOKEN if present (Option B,
# Auth0 → APIs → Auth0 Management API → Test tab), else fetch via M2M creds.
MGMT_TOKEN="${AUTH0_MGMT_TOKEN:-}"
case "$MGMT_TOKEN" in *'<'*) MGMT_TOKEN="";; esac
if [ -z "$MGMT_TOKEN" ]; then
  : "${AUTH0_MGMT_CLIENT_ID:?paste AUTH0_MGMT_TOKEN, or create an M2M app and set AUTH0_MGMT_CLIENT_ID/SECRET in .env}"
  : "${AUTH0_MGMT_CLIENT_SECRET:?set AUTH0_MGMT_CLIENT_SECRET (or paste AUTH0_MGMT_TOKEN) in .env}"
  case "$AUTH0_MGMT_CLIENT_SECRET" in *'<'*) echo "✗ Fill AUTH0_MGMT_CLIENT_SECRET (or paste AUTH0_MGMT_TOKEN) in .env"; exit 1;; esac
  echo "→ Requesting a Management API token via M2M credentials…"
  MGMT_TOKEN="$(curl -fsS --request POST "https://${AUTH0_DOMAIN}/oauth/token" \
    --header 'content-type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=client_credentials' \
    --data-urlencode "client_id=${AUTH0_MGMT_CLIENT_ID}" \
    --data-urlencode "client_secret=${AUTH0_MGMT_CLIENT_SECRET}" \
    --data-urlencode "audience=https://${AUTH0_DOMAIN}/api/v2/" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
  [ -n "$MGMT_TOKEN" ] || { echo "✗ Could not obtain a Management API token (check M2M creds + authorization)."; exit 1; }
else
  echo "→ Using AUTH0_MGMT_TOKEN from .env."
fi

created=0; existing=0; failed=0
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
for i in $(seq 1 "$POOL_SIZE"); do
  email="${PREFIX}-${i}@${DOMAIN}"
  body="{\"email\":\"${email}\",\"password\":\"${AUTH0_TEST_PASSWORD}\",\"connection\":\"${AUTH0_CONNECTION}\",\"email_verified\":true,\"app_metadata\":{\"loadtest\":true}}"
  code="$(curl -sS -o "$tmp" -w '%{http_code}' --request POST \
    "https://${AUTH0_DOMAIN}/api/v2/users" \
    --header "authorization: Bearer ${MGMT_TOKEN}" \
    --header 'content-type: application/json' \
    --data "$body" || echo 000)"
  case "$code" in
    201) created=$((created+1));;
    409) existing=$((existing+1));;
    *)   failed=$((failed+1)); echo "  ! ${email} → HTTP ${code}: $(head -c 200 "$tmp")";;
  esac
  sleep 0.3   # be gentle with Auth0 Management API rate limits
done

echo "✓ Done. created=${created} existing=${existing} failed=${failed} (pool=${POOL_SIZE})"
echo "  Users: ${PREFIX}-1@${DOMAIN} … ${PREFIX}-${POOL_SIZE}@${DOMAIN}"
echo "  Next: bash get-token.sh   # verify the ROPC chain end-to-end"
