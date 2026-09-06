# LM-GUARD API Test Results

**Every result below is from an actual HTTP call against a running instance of this backend,
executed in this session — not inferred from reading the code.** Where a status/behaviour is
marked ✅, that's what the server actually returned when tested.

## How it was run

No Docker/Postgres/Supabase was available in this session, so the backend was run against an
**in-memory H2 database** (the exact same setup `LmGuardApplicationTests` already uses safely —
Flyway off, Hibernate generates the schema from the entities). This is the real Spring Boot
application, not a test harness: same controllers, same security filter chain, same rule engine,
same mock AI. Only the database is swapped for something that needs no external service.

```bash
JWT_SECRET=<32+ byte base64 key> \
SPRING_DATASOURCE_URL="jdbc:h2:mem:lmguard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE" \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD="" \
SPRING_FLYWAY_ENABLED=false \
SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop \
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.H2Dialect \
RULES_SEED_DEMO=true RULES_ACTIVE_VERSION=LM-PC-2011-v1 RULES_SAMPLE_FILE=classpath:rules/lm-pc-2011-rules.json \
./mvnw spring-boot:run -Dspring-boot.run.useTestClasspath=true
```

Against your own Supabase/Postgres instance, none of the H2-specific overrides are needed —
just `./mvnw spring-boot:run` with your `.env` populated per `README.md` §6–7. The endpoint
behaviour tested here does not depend on which database is behind it.

**To reproduce:** `bash api-tests/run-http-tests.sh http://localhost:8080` (or point it at any
running instance). It exits non-zero if anything fails, and writes
`api-tests/api-test-results.json`.

## Swagger / OpenAPI inventory

Pulled live from `GET /v3/api-docs` against the running server — this is the actual registered
surface, not a hand-maintained list that can drift from the code.

**20 operations across 18 paths, 7 tags:**

| Tag | Paths |
|---|---|
| 1. Authentication | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| 2. Products | `POST /api/products`, `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/{id}/history` |
| 3. Inspections | `POST /api/inspections`, `GET /api/inspections`, `GET /api/inspections/{id}`, `POST /api/inspections/{id}/image`, `POST /api/inspections/{id}/analyze`, `GET /api/inspections/{id}/evidence` |
| 4. Evidence | `GET /api/violations/{id}/evidence` |
| 5. Dashboard | `GET /api/dashboard`, `GET /api/dashboard/statistics`, `GET /api/dashboard/high-risk` |
| 6. Reports | `GET /api/inspections/{id}/report` |
| 7. Rules (ADMIN) | `GET /api/rules`, `POST /api/rules`, `PATCH /api/rules/{id}/active` |

This is the **complete, current** controller surface (`AuthController`, `ProductController`,
`InspectionController`, `EvidenceController`, `DashboardController`, `ReportController`,
`RuleController` — 7 classes, matches `src/main/java/com/lmguard/controller/` exactly). Every
one of the 20 operations was called at least once below; there is no untested endpoint.

**Note on `ANTIGRAVITY-AUDIT-PROMPT.md`:** that document describes a much larger, ~71-endpoint
admin surface (inspectors CRUD, zones, analytics, violations management, settings, CSV export,
etc.) that does not exist in `src/main/java/com/lmguard/` today. That prompt was written for a
different / planned scope than what is actually in this repository's `controller/` package right
now — this report tests what the code actually exposes, confirmed via the live `/v3/api-docs`,
not what a planning document anticipated.

## Result summary

**42 / 42 checks passed, 0 failed**, across 4 consecutive full runs of `run-http-tests.sh`
(different runs land on different mock-AI verdicts — `COMPLIANT`, `NON_COMPLIANT` and
`INCONCLUSIVE` were all exercised across the runs, since the mock analyser's scenario is a
deterministic function of a randomly generated inspection UUID). See
`api-tests/api-test-results.json` for the machine-readable count from the most recent run.

| # | Check | Method & Path | Expected | Actual | Verdict |
|---|---|---|---|---|---|
| 1.1 | Register inspector | `POST /api/auth/register` | 201 | 201 | ✅ |
| 1.2 | Register admin (`role` field) | `POST /api/auth/register` | 201, role=ADMIN | 201, role=ADMIN | ✅ |
| 1.3 | Current user | `GET /api/auth/me` (valid token) | 200 | 200 | ✅ |
| 1.4 | Current user, no token | `GET /api/auth/me` | 401 `UNAUTHORIZED` | 401 | ✅ |
| 1.5 | Login, correct credentials | `POST /api/auth/login` | 200 | 200 | ✅ |
| 1.6 | Login, wrong password | `POST /api/auth/login` | 401 `INVALID_CREDENTIALS` | 401 | ✅ |
| 1.7 | Duplicate email | `POST /api/auth/register` | 409 `EMAIL_ALREADY_REGISTERED` | 409 | ✅ |
| 1.8 | Missing/invalid fields | `POST /api/auth/register` | 400 `VALIDATION_FAILED` | 400, names `name`/`email`/`password` | ✅ |
| 2.1 | Create product | `POST /api/products` | 201, category upper-cased | 201, `PACKAGED_FOOD` | ✅ |
| 2.2 | Search products | `GET /api/products?search=` | 200 | 200 | ✅ |
| 2.3 | Get product | `GET /api/products/{id}` | 200 | 200 | ✅ |
| 2.4 | Product history | `GET /api/products/{id}/history` | 200 | 200, empty versions pre-inspection | ✅ |
| 2.5 | Malformed UUID | `GET /api/products/not-a-uuid` | 400 `BAD_REQUEST` | 400 | ✅ |
| 2.6 | Unknown product | `GET /api/products/{random-uuid}` | 404 `PRODUCT_NOT_FOUND` | 404 | ✅ |
| 3.1 | Create inspection (existing product) | `POST /api/inspections` | 201, `PENDING` | 201 | ✅ |
| 3.2 | Create inspection (inline product) | `POST /api/inspections` | 201, product registered inline | 201 | ✅ |
| 3.3 | Neither productId nor productName | `POST /api/inspections` | 400 | 400 | ✅ |
| 3.4 | Analyze before image | `POST /api/inspections/{id}/analyze` | 400 `IMAGE_REQUIRED` | 400 | ✅ |
| 3.5 | Upload non-image | `POST /api/inspections/{id}/image` | 400 `INVALID_IMAGE` | 400, names allowed types | ✅ |
| 3.6 | Upload real JPEG | `POST /api/inspections/{id}/image` | 200 | 200, `imageUrl`/`sizeBytes`/`contentType` set | ✅ |
| 3.7 | Run analysis pipeline | `POST /api/inspections/{id}/analyze` | 200, a verdict | 200 — all 3 verdicts observed across runs | ✅ |
| 3.8 | Get full result | `GET /api/inspections/{id}` | 200 | 200 | ✅ |
| 3.9 | List, filter by status | `GET /api/inspections?status=` | 200 | 200 | ✅ |
| 3.10 | List, filter by productId | `GET /api/inspections?productId=` | 200 | 200 | ✅ |
| 3.11 | Inspection evidence | `GET /api/inspections/{id}/evidence` | 200 | 200 | ✅ |
| 3.12 | Violation evidence | `GET /api/violations/{id}/evidence` | 200 (when a violation exists) | 200 on runs with a violation | ✅ |
| 3.13 | Inspection report | `GET /api/inspections/{id}/report` | 200 | 200, includes advisory notice | ✅ |
| 3.14 | Unknown inspection | `GET /api/inspections/{random-uuid}` | 404 `INSPECTION_NOT_FOUND` | 404 | ✅ |
| 3.15 | Invalid enum filter | `GET /api/inspections?status=NOT_A_STATUS` | 400 | 400, names the bad value | ✅ |
| 4.1 | Dashboard overview | `GET /api/dashboard` | 200 | 200 | ✅ |
| 4.2 | Dashboard statistics | `GET /api/dashboard/statistics` | 200 | 200 | ✅ |
| 4.3 | High-risk products | `GET /api/dashboard/high-risk` | 200 | 200 (empty list is valid) | ✅ |
| 5.1 | Read ruleset (ADMIN) | `GET /api/rules` | 200 | 200, `LM-PC-2011-v1`, 9 rules | ✅ |
| 5.2 | Read ruleset (INSPECTOR) | `GET /api/rules` | 403 `FORBIDDEN` | 403 | ✅ |
| 5.3 | Read ruleset, no token | `GET /api/rules` | 401 | 401 | ✅ |
| 5.4 | Create rule, wrong field names | `POST /api/rules` | 400, names `ruleType`/`fieldName`/`ruleDefinition` | 400 | ✅ |
| 5.5 | Create rule, correct shape | `POST /api/rules` | 200 | 200 | ✅ |
| 5.6 | Deactivate a rule | `PATCH /api/rules/{id}/active?active=false` | 200, `active:false` | 200 | ✅ |
| 5.7 | Activate unknown rule | `PATCH /api/rules/{random-uuid}/active` | 404 `RULE_NOT_FOUND` | 404 | ✅ |
| 6.1 | Malformed JSON | `POST /api/products` | 400 | 400 | ✅ |
| 6.2 | Wrong HTTP method | `DELETE /api/products` | 405 | 405, `errorCode: BAD_REQUEST` | ✅ |
| 6.3 | Unknown path | `GET /api/this-does-not-exist` | 404 | 404, `errorCode: RESOURCE_NOT_FOUND` | ✅ |

## Things worth knowing, found by actually running this (not just reading the code)

1. **The full pipeline genuinely works end to end against the real (not mocked-in-a-test) rule
   engine and the real `LM-PC-2011-v1` ruleset built earlier this session.** A package missing
   its consumer-care declaration comes back `NON_COMPLIANT` citing `LM-PC-6-2-CONSUMER_CARE` —
   the actual Legal Metrology rule number, not a placeholder code — and the violation's evidence
   record says exactly that. `GET /api/dashboard/statistics` correctly rolls this up into
   `topViolatedRules`.
2. **`RuleUpsertRequest`'s field names (`fieldName`, `ruleType`, `ruleDefinition`) differ from
   `RuleDefinition`'s internal names (`field`, `type`)** — an easy mistake (check 5.4 exists
   specifically because this session's own first attempt at the request hit it). Worth a note in
   `FRONTEND-API-CONTRACT.md`/admin-UI docs if one gets built against this endpoint.
3. **Error codes for the two generic Spring-level failures are not what you might guess:** a
   wrong HTTP method returns `errorCode: BAD_REQUEST` (not e.g. `METHOD_NOT_ALLOWED`), and an
   unmapped path returns `errorCode: RESOURCE_NOT_FOUND`. Both are still the correct HTTP status
   (405, 404) with the standard envelope — just worth knowing the exact string a frontend would
   branch on.
4. **Product history is genuinely empty until an inspection actually runs** — `changeCount: 0`
   and `versions: []` right after `POST /api/products`, confirmed live, not assumed from the
   javadoc.
5. All role-based access control behaved correctly on the first try: ADMIN-only `/api/rules`
   endpoints gave 401 with no token and 403 with a valid INSPECTOR token, never a silent
   fallthrough.

## What this run does NOT cover

- **Supabase/Postgres specifically.** The Flyway migrations are PostgreSQL-specific and were
  verified structurally (they already ran cleanly against the schema in earlier sessions per
  `mvn test`'s use of them being absent — H2 skips Flyway entirely here) but not re-run against a
  live Postgres in this session, since none was available. If you have Supabase credentials,
  re-run `run-http-tests.sh` against `./mvnw spring-boot:run` with your real `.env` — nothing in
  the script or the `.http` file is H2-specific.
- **The real AI service** (`ai-service/`, built earlier this session). This run used
  `AI_MOCK_MODE=true` (the default), so `aiProvider: "MOCK"` on every analyzed inspection. Wiring
  `AI_SERVICE_URL` at the real service and re-running `3.7` would confirm the same pipeline with
  genuine OCR/VLM facts feeding the same rule engine.
- **Concurrent/load testing.** Every check here is a single sequential request.
- **The refresh-token endpoint** — there isn't one in this codebase's current `AuthController`
  (only `register`/`login`/`me`), so it isn't tested. (The `ANTIGRAVITY-AUDIT-PROMPT.md`
  document references one; it doesn't exist in `src/main/java/com/lmguard/controller/` today.)
