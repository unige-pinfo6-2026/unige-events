#!/usr/bin/env bash
# Smoke-test the ROPC chain: mint one access token for a test user and call
# GET ${BASE_URL}/users/me. Run this BEFORE the full k6 test.
#
# Usage: bash get-token.sh [userIndex]   (default index = 1)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/.env}"
[ -f "$ENV_FILE" ] || { echo "✗ Missing $ENV_FILE (copy .env.example → .env)"; exit 1; }
set -a; . "$ENV_FILE"; set +a

: "${AUTH0_DOMAIN:?}"; : "${AUTH0_AUDIENCE:?}"; : "${AUTH0_CONNECTION:?}"
: "${AUTH0_CLIENT_ID:?}"; : "${AUTH0_TEST_PASSWORD:?}"; : "${BASE_URL:?}"
case "$AUTH0_TEST_PASSWORD" in *'<'*) echo "✗ Fill AUTH0_TEST_PASSWORD in .env"; exit 1;; esac

I="${1:-1}"
PREFIX="${AUTH0_TEST_EMAIL_PREFIX:-loadtest}"
DOMAIN="${AUTH0_TEST_EMAIL_DOMAIN:-example.com}"
email="${PREFIX}-${I}@${DOMAIN}"

args=(--data-urlencode 'grant_type=http://auth0.com/oauth/grant-type/password-realm'
      --data-urlencode "client_id=${AUTH0_CLIENT_ID}"
      --data-urlencode "audience=${AUTH0_AUDIENCE}"
      --data-urlencode "realm=${AUTH0_CONNECTION}"
      --data-urlencode 'scope=openid profile email'
      --data-urlencode "username=${email}"
      --data-urlencode "password=${AUTH0_TEST_PASSWORD}")
# Include client_secret only for a confidential client (non-empty, not a placeholder).
if [ -n "${AUTH0_CLIENT_SECRET:-}" ] && [ "${AUTH0_CLIENT_SECRET#<}" = "${AUTH0_CLIENT_SECRET}" ]; then
  args+=(--data-urlencode "client_secret=${AUTH0_CLIENT_SECRET}")
fi

echo "→ Minting ROPC token for ${email}…"
resp="$(curl -sS --request POST "https://${AUTH0_DOMAIN}/oauth/token" \
  --header 'content-type: application/x-www-form-urlencoded' "${args[@]}")"
token="$(printf '%s' "$resp" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
if [ -z "$token" ]; then echo "✗ No token. Auth0 response:"; echo "$resp"; exit 1; fi
echo "✓ Token acquired (length ${#token})."

if command -v python3 >/dev/null 2>&1; then
  printf '%s' "$token" | python3 -c 'import sys,base64,json
p=sys.stdin.read().split(".")[1]; p+="="*(-len(p)%4)
d=json.loads(base64.urlsafe_b64decode(p))
print("  aud =", d.get("aud"), "| iss =", d.get("iss"), "| sub =", d.get("sub"))' || true
fi

echo "→ GET ${BASE_URL}/users/me"
code="$(curl -sS -o /tmp/lt_me.$$ -w '%{http_code}' "${BASE_URL%/}/users/me" \
  --header "authorization: Bearer ${token}" || echo 000)"
echo "  HTTP ${code}"
head -c 400 /tmp/lt_me.$$ 2>/dev/null; echo; rm -f /tmp/lt_me.$$
if [ "$code" = "200" ]; then
  echo "✓ Auth chain works end-to-end."
else
  echo "✗ Not 200. Hints: 401 → audience/issuer mismatch; 403 mfa_required → disable MFA;"
  echo "  invalid_grant → wrong user/password or Password grant not enabled on the client."
  exit 1
fi
