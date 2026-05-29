# Load testing (Grafana K6)

This folder hosts the k6 load-test scripts (added later, per
`specs_load-testing-k6.md`) and the auth bootstrap for the ROPC token pool.

The **system under test is the REMOTE deployment** (default
`https://pinfo6.p-info.net`). This machine is only the **load generator**.
Authenticated traffic uses real Auth0 tokens, minted once via the **Resource
Owner Password** grant (ROPC) for a pool of seeded test users and reused across
virtual users — see the spec for the rationale and trade-offs.

## 1. Configure
`cp .env.example .env`, then fill it in. `.env` is git-ignored — never commit it.
Audience is `https://unige-events/api`; domain/connection are pre-filled.

## 2. Enable the Password grant (ROPC)
Auth0 Dashboard → **Applications** → your client → **Advanced Settings** →
**Grant Types** → tick **Password** → Save. Also:
- App → **Connections** tab → enable `Username-Password-Authentication`.
- Tenant → **Settings → General → API Authorization Settings** → set
  **Default Directory** to `Username-Password-Authentication`.
- Ensure the test users / connection do **not** enforce MFA.

Fast path: enable Password grant on the existing dev client already set in
`.env` (public, no secret). Cleaner path: a dedicated confidential *Regular Web
App* (then set its id + secret).

## 3. Seed the test users
Needs an **M2M app authorized for the Auth0 Management API** with scope
`create:users` (set `AUTH0_MGMT_CLIENT_*` in `.env`), then:

    bash seed-users.sh          # creates loadtest-1..50@example.com

Alternatives: Auth0 dashboard (manual), CSV bulk import (User Import/Export),
or `auth0 users create` in a loop.

## 4. Verify the auth chain
    bash get-token.sh           # mints a token for user #1, calls /users/me

Expect **HTTP 200** and `aud = https://unige-events/api`.

## Files
| File | Purpose | Committed? |
|---|---|---|
| `.env.example` | config/secrets template | ✅ |
| `.env` | real config/secrets | ❌ (git-ignored) |
| `seed-users.sh` | create the test-user pool (Management API) | ✅ |
| `get-token.sh` | ROPC end-to-end smoke test | ✅ |
| `specs_load-testing-k6.md` | full load-test spec (generated next) | ✅ |

> ⚠️ Writes hit a real deployment: they create real rows + Kafka→notifications,
> and the seeded users become real accounts. Prefer a preview env, coordinate a
> window, tag test data, and clean up. See the spec's safety section.
