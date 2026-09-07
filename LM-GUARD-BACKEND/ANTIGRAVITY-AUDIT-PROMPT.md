# Antigravity prompt — LM-GUARD backend API audit

Paste everything below the line into Antigravity's agent, with the `lm-guard-backend` folder open.

Two notes before you do:

- **Start the backend first** and leave it running. The agent needs a live server on `:8080`.
- The section headed *"Deviations that are deliberate"* is the important part. Without it the
  agent will report the backend's design decisions as defects and hand you a report full of
  CRITICALs that are not bugs.

---

You are auditing the **LM-GUARD** Spring Boot backend — an AI-assisted Legal Metrology inspection
platform — against the API contract its frontend team submitted.

Your job is to find out where backend and frontend genuinely disagree. It is **not** to produce a
long list of findings. A short report with three real defects is worth more than forty entries
where thirty-seven are noise.

## The one architectural principle

The AI decides what is **visible**. A deterministic rule engine decides what is **legal**. A human
inspector decides what is **enforced**. No language model touches the compliance decision.

Several behaviours below exist to protect that boundary. If a test seems to show the backend being
"inconsistent", check this principle before calling it a bug — you have probably found the design.

---

## Step 1 — Ground yourself in what already exists

Read these before writing a single test. They are in the repo root:

| File | What it tells you |
|---|---|
| `FRONTEND-API-CONTRACT.md` | **the expected contract** — this is the frontend team's document |
| `APPLYING-THIS-CHANGE.md` | why the backend deviates where it does, and what is unverified |
| `QUICKSTART.md` | how to run it |
| `docs/SUPABASE-SETUP.md` | database/storage configuration |

Then inspect `src/main/java/com/lmguard/` — controllers, DTOs, `SecurityConfig`,
`GlobalExceptionHandler`, `OpenApiConfig` — and `src/main/resources/application*.yml` plus the
Flyway migrations in `db/migration/`.

**Do not modify any file under `src/main/java` or `src/main/resources`.** You are auditing. If you
find a defect, report it with the exact file, line and suggested change, and stop there.

## Step 2 — Get the running contract, not the source contract

```
curl -s http://localhost:8080/v3/api-docs > /tmp/openapi.json
curl -s http://localhost:8080/v3/api-docs/swagger-config
```

Expect roughly **71 paths** and **four** Swagger groups: `0 - Everything`, `1 - Authentication`,
`2 - Administrator` (~50 paths), `3 - Inspector`. If the numbers differ substantially, say so —
that itself is a finding.

Build your endpoint inventory from `/v3/api-docs`, not from reading controllers. A controller
method that Swagger does not expose, or exposes differently, is exactly the kind of gap worth
catching.

## Step 3 — Deviations that are DELIBERATE

**Do not report any of these as defects.** They are documented decisions. Your job is to confirm
each one still behaves as documented — if one has drifted, *that* is the finding.

1. **Two response conventions.** `/api/auth/**` and `/api/admin/**` return payloads **flat**.
   Everything else (`/api/inspections`, `/api/products`, `/api/rules`, `/api/dashboard`) returns
   the `{success, message, data, errorCode, timestamp}` envelope. A flat login response is correct.

2. **`errorCode` is present on errors even though the frontend document omits it.** Deliberate
   superset. Its absence would be the defect.

3. **Inspection status is two fields.** `status` is `PENDING/PROCESSING/COMPLIANT/NON_COMPLIANT/
   INCONCLUSIVE/FAILED` and is read-only. `reviewStatus` is `PENDING_REVIEW/UNDER_REVIEW/ACCEPTED/
   OVERRIDDEN/CLOSED` and is what `PATCH /api/admin/inspections/{id}/status` writes. The frontend
   document proposed a single six-value workflow enum; that was rejected on purpose.

4. **`INCONCLUSIVE` is not a violation.** It means a declaration could not be read confidently.

5. **`complianceRate` can be `null`** — when nothing was decided. Null, not zero.

6. **Pagination is `{items, page, limit, totalItems, totalPages, hasNext}`** with `page`/`limit`
   query params, on `/api/admin/**` only. The older endpoints use `size` and return
   `{items, page, size, totalItems, totalPages, last}`.

## Step 4 — The invariants that actually matter

Endpoint-exists checks are the cheap part. These four are what an audit is *for*. Test each
against the live server and report PASS/FAIL with the actual response body.

**A. A review never rewrites a verdict.**
Run an inspection to a `NON_COMPLIANT` verdict. `PATCH /api/admin/inspections/{id}/status` with
`{"status":"OVERRIDDEN","note":"disagree"}`. Then `GET` the inspection.
→ `status` must still be `NON_COMPLIANT`. `reviewStatus` must be `OVERRIDDEN`.
If `status` changed, that is a **CRITICAL** defect — the audit trail is broken.

**B. An override without a note is refused.**
Same PATCH with `{"status":"OVERRIDDEN"}` and no note → expect `400 REVIEW_NOTE_REQUIRED`, and
confirm nothing was partially applied. Same for `DISMISSED` on
`PATCH /api/admin/violations/{id}/status`.

**C. Inconclusive never counts as a violation.**
Create enough inspections that at least one comes back `INCONCLUSIVE` (the mock analyser cycles
all three verdicts deterministically). Then check:
- `GET /api/admin/dashboard/summary` — `inconclusiveCount` is its own field and is **not**
  included in `potentialViolations`
- `GET /api/admin/violations/summary` — `inconclusiveCount` is **not** inside `totalNonCompliant`
- `GET /api/admin/analytics/zones` — `complianceRate` excludes inconclusive from its denominator.
  Verify the arithmetic by hand: rate should equal compliant ÷ (compliant + nonCompliant).

**D. `/api/admin/**` is ADMIN-only.**
Call three admin endpoints with (i) no token, (ii) an INSPECTOR token, (iii) an ADMIN token.
Expect `401`, `403`, `200`. **Do not weaken security to make anything pass.**

## Step 5 — Runtime tests

Create `api-tests/LMGuardApiTests.http` with real, executable requests against
`http://localhost:8080`. No mocks, no tests that only reflect over Java classes.

Cover, in this order (later steps need earlier ones' data):

1. **Auth** — register; login with good and bad credentials; `/api/auth/me` with and without a
   token; duplicate email → `409 EMAIL_ALREADY_REGISTERED`.
2. **Refresh token rotation** — call `/api/auth/refresh`, then call it *again with the same
   token*. The second call must fail with `401 REFRESH_TOKEN_INVALID`. Single-use is intentional.
3. **Products** — create, list, search, category filter, pagination, get by id, history, invalid
   UUID, missing required field.
4. **Inspections** — create; upload image (`multipart/form-data`, field name **`file`**); analyze;
   get; list with `status` / `productId` / `inspectorId` filters; evidence; report. Also upload a
   non-image and confirm `400 INVALID_IMAGE`.
5. **Admin surface** — dashboard overview/summary/compliance-trend/risk-distribution; inspectors
   CRUD + status + zone + workload + activity; zones; violations; products; risk-intelligence;
   analytics; `reports/export` (CSV; a non-CSV `format` must give `400
   UNSUPPORTED_EXPORT_FORMAT`); rules; settings.
6. **Errors** — 400, 401, 403, 404, invalid UUID, malformed JSON, invalid enum, missing field.

Three things that will trip you up if you do not expect them:

- **Zones `ZONE-A/B/C` already exist** (seeded by migration V11). Creating one returns
  `409 ZONE_CODE_ALREADY_EXISTS`. That is correct behaviour, not a failure.
- **`sortBy` is whitelisted per endpoint.** An unknown field returns `400` naming the allowed
  values. That is correct.
- **`POST /api/admin/inspectors` returns `temporaryPassword` exactly once** when you omit
  `password`. Do not print it into the report.

## Step 6 — DTO and response comparison

For every request DTO, compare against the frontend document: field names, capitalisation, types,
required vs optional, enum values, validation rules. Note especially that `POST /api/auth/login`
accepts the identifier as **either** `username` or `email`.

For responses, compare wrapper shape, field names, nested objects, timestamps, enum values and
nullability. Do not call two responses matching just because they carry similar information.

## Step 7 — Database

The backend is configured against Supabase PostgreSQL. **Do not switch it to H2** for these tests.
Confirm that data written through the API is actually persisted — create a product, then read it
back in a separate request.

If you can reach the database directly, `docs/supabase-verification.sql` contains read-only checks.

## Step 8 — Deliverables

Create exactly these, and nothing else:

1. **`api-tests/LMGuardApiTests.http`** — executable requests.
2. **`API-AUDIT-REPORT.md`** — with a per-endpoint matrix:

   | Endpoint | Method | Frontend expects | Backend/Swagger | Contract match | Runtime | Verdict |

   For each mismatch: what the frontend expects, what the backend does, why they differ, the
   recommended fix, and a severity of 🔴 CRITICAL / 🟠 HIGH / 🟡 MEDIUM / 🟢 LOW.

   Reserve 🔴 for things that break the frontend or the audit trail. An extra field in a response
   is not critical.

3. **`api-tests/api-test-results.json`** — real counts only:
   `{"totalEndpoints":N,"passed":N,"failed":N,"notTested":N,"mismatched":N}`

## Rules

1. **Never mark something PASS that you did not execute.** If a prerequisite was missing, the
   verdict is `NOT TESTED`. This is the rule I care about most — a fabricated PASS is worse than
   an honest gap, because it stops anyone looking again.
2. Distinguish four separate things and say which you verified: *controller exists* / *Swagger
   documents it* / *runtime works* / *matches the frontend contract*.
3. Do not modify backend source to make a test pass. Report the defect instead.
4. If the **frontend document** is wrong or stale, say so — it is not automatically the authority.
5. Never write secrets into any generated file: no DB password, JWT secret, Supabase service-role
   key, bearer token or generated password. Use variables in the `.http` file.
6. Do not commit `.env`. Do not delete existing files.

## Final answer

- **Summary:** total Swagger endpoints / frontend expected / matching / mismatched / missing /
  extra / runtime passed / failed / not tested.
- **Every 🔴 and 🟠 finding**, with evidence — the actual request and response.
- **`FRONTEND ↔ BACKEND COMPATIBILITY: PASS | PARTIAL | FAIL`**, with the one sentence that
  justifies it.
- **Files created.**
- **Required fixes**, by file, with suggested change — but change nothing without my approval.
- **Is it safe to commit these files to GitHub?** Answer only after grepping your own generated
  files for tokens and secrets.
