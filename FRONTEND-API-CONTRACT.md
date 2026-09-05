# LM-GUARD Admin API — what the frontend gets

**For:** the LM-GUARD frontend team
**Against:** *Admin Backend API Requirements v1.0*, 5 September 2026
**Base URL:** `http://localhost:8080`
**Swagger:** `http://localhost:8080/swagger-ui/index.html` — every endpoint below is documented
there with request and response schemas.

Use the **definition selector at the top right** and pick **`2 - Administrator`**. That narrows
71 endpoints down to the 50 you actually call. To try anything out: `POST /api/auth/login`, copy
`accessToken`, click **Authorize**, paste it (without the word "Bearer").

To run the backend yourself with no database setup at all, see `QUICKSTART.md` — one command,
and it comes pre-loaded with zones, inspectors and an admin account.

Everything in your document is implemented. Six things behave differently from what you
specified, and all six are listed in §2. Nothing else should surprise you.

---

## 1. Auth

```
POST /api/auth/login      → tokens + user      (public)
POST /api/auth/refresh    → new tokens         (public)
POST /api/auth/logout     → { success, message } (bearer)
GET  /api/auth/me         → user               (bearer)
POST /api/auth/change-password                 (bearer)
```

**Login** — send the identifier as `username` *or* `email`; both work.

```jsonc
// POST /api/auth/login   { "username": "admin@example.com", "password": "..." }
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "8Kd2mQ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-05T13:00:00Z",
  "user": { "id": "…", "name": "Raj Kumar", "email": "admin@example.com",
            "role": "ADMIN", "enabled": true, "createdAt": "…" }
}
```

Send `Authorization: Bearer <accessToken>` on everything else.

**Refresh tokens are single-use.** Every call to `/refresh` revokes the token you sent and
returns a new one. **Store the new `refreshToken` or your next refresh fails.**

```jsonc
// POST /api/auth/refresh   { "refreshToken": "8Kd2mQ..." }
// → the same shape as login
```

If a refresh returns `401 REFRESH_TOKEN_INVALID` and you know you stored the latest token, treat
it as a signal that the token may have been used elsewhere — sign the user out.
`401 REFRESH_TOKEN_EXPIRED` is the ordinary "session is old, sign in again" case.

**Logout** — send `{ "refreshToken": "..." }` to end that session, or send no body to end every
session for the account ("sign me out everywhere").

Suggested client rule: on `401` from any endpoint, try `/refresh` once; if that fails, clear both
tokens and route to login.

---

## 2. The six differences from your document

**1. `/api/auth/**` and `/api/admin/**` return payloads flat, as you specified.**
The older inspector-facing endpoints (`/api/inspections`, `/api/products`, `/api/rules`,
`/api/dashboard`) still use the `{success, message, data}` envelope from the README. Those are
out of scope for this phase. If your fetch wrapper unwraps `.data`, it must **not** do so for
`/api/auth` or `/api/admin`.

**2. Errors carry `errorCode` in addition to the fields you specified.**

```jsonc
{
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "errors": [ { "field": "email", "message": "Email already exists" } ],
  "timestamp": "2026-09-05T12:00:00Z"
}
```

`errors[]` is present only on validation failures, exactly as you asked. `errorCode` is added
because branching on message text breaks the moment someone rewords a message. **Branch on
`errorCode`.**

**3. Inspection status is two fields, not one.**

Your §18 proposed `ASSIGNED, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, COMPLETED, CLOSED`. The
backend deliberately keeps these separate:

| Field | Values | Meaning |
|---|---|---|
| `status` | `PENDING`, `PROCESSING`, `COMPLIANT`, `NON_COMPLIANT`, `INCONCLUSIVE`, `FAILED` | What the analysis found. Read-only. |
| `reviewStatus` | `PENDING_REVIEW`, `UNDER_REVIEW`, `ACCEPTED`, `OVERRIDDEN`, `CLOSED` | What a person decided. This is what `PATCH .../status` writes. |

Render both. A row is not "done" because it has a verdict — it is done when someone has reviewed
it.

`FAILED` is **not** a compliance outcome; it means the analysis itself broke. Show it as an error
state, not as a red "non-compliant" badge.

**4. `INCONCLUSIVE` is a first-class state and must never be shown as a violation.**

It means a declaration could not be read confidently — a blurred photo, bad lighting. It is
**not** evidence that a label is missing. Please render it **amber, in its own column or filter**,
never folded into non-compliant counts. Every count the API returns keeps it separate; the one
place it could get merged back together is your UI.

A practical use: `GET /api/admin/violations?status=INCONCLUSIVE` is the *re-inspection queue*,
not the enforcement queue.

**5. Compliance rates can be `null` — and the field is always present.**

`complianceRate` is compliant ÷ (compliant + non-compliant). Inconclusive results and failures
are excluded from the denominator. When nothing was decided in a period, the value is `null` —
**plot that as a gap, not as 0%.** Zero would read as "everything failed".

The API is otherwise configured to omit null fields. Where a null *means* something you have to
render, the field is sent explicitly as `null` rather than dropped, so you never have to tell an
absent field from a typo. That applies to `complianceRate`, `averageScore`, an inspector's
`zone` / `lastLoginAt` / `lastActiveAt`, and a product's `riskLevel` / `riskScore` /
`latestInspectionDate` / `complianceStatus` (all null until first inspected). Example, from a
zone with no inspections yet:

```json
{ "zoneCode": "ZONE-B", "inspectorCount": 1, "totalInspections": 0,
  "nonCompliantCount": 0, "inconclusiveCount": 0, "complianceRate": null }
```

**6. Pagination uses `page` / `limit`, and lists come back like this:**

```jsonc
{ "items": [ … ], "page": 0, "limit": 20, "totalItems": 137, "totalPages": 7, "hasNext": true }
```

Query params on every list endpoint: `page`, `limit`, `sortBy`, `sortOrder` (`asc`/`desc`).
`sortBy` is whitelisted per endpoint — an unknown field returns `400` naming the allowed ones,
rather than being silently ignored. Swagger lists them.

---

## 3. Inspector management (§13)

```
GET    /api/admin/inspectors?search=&zoneId=&status=&page=&limit=&sortBy=&sortOrder=
POST   /api/admin/inspectors
GET    /api/admin/inspectors/{id}
PATCH  /api/admin/inspectors/{id}            name / email / phone
PATCH  /api/admin/inspectors/{id}/status     { "status": "ACTIVE" | "INACTIVE" }
PATCH  /api/admin/inspectors/{id}/zone       { "zoneId": "…" | null }
GET    /api/admin/inspectors/{id}/workload
GET    /api/admin/inspectors/{id}/activity?page=&limit=
```

`search` matches **name, inspector code, email and phone**, as specified.

```jsonc
// InspectorResponse
{
  "id": "3f1c…",                    // use this in URLs
  "inspectorId": "INSP-001",        // the human-facing code
  "name": "Priya Raman",
  "email": "priya.raman@example.com",
  "phone": "+919876543210",
  "zone": { "id": "a000…", "name": "Zone A", "code": "ZONE-A" },   // null if unassigned
  "status": "ACTIVE",
  "assignedInspectionCount": 12,
  "completedInspectionCount": 8,
  "lastActiveAt": "2026-09-05T06:30:00Z",
  "lastLoginAt": "2026-09-05T06:00:00Z",
  "createdAt": "2026-08-01T00:00:00Z"
}
```

Two ids, both needed: **`id`** is what every endpoint takes; **`inspectorId`** is what an
inspector quotes on paperwork. Show `inspectorId`, send `id`.

**Creating an inspector.** `password` and `inspectorId` are both optional.

```jsonc
// POST  { "name": "…", "email": "…", "phone": "…", "zoneId": "…" }
{
  "inspector": { … },
  "temporaryPassword": "k7Rm2xQp9wTz"   // present ONLY when the server generated one
}
```

**Show `temporaryPassword` to the administrator once, prominently, and warn that it cannot be
retrieved again.** It is not stored in recoverable form. If it is lost, a new one must be set.

**Deactivating signs the inspector out everywhere** — their refresh tokens are revoked
immediately. An administrator cannot deactivate their own account (`403
CANNOT_MODIFY_OWN_ACCOUNT`).

**Workload** reports `inconclusiveCount` separately from `nonCompliantCount`. Please keep them
apart in the UI: an inspector with a failing camera should not look like an inspector finding
breaches.

---

## 4. Zones (§14)

```
GET    /api/admin/zones?search=&status=&page=&limit=
POST   /api/admin/zones
GET    /api/admin/zones/{id}
PATCH  /api/admin/zones/{id}
GET    /api/admin/zones/{id}/inspectors     → plain array, not paginated
GET    /api/admin/zones/{id}/summary
```

```jsonc
{ "id": "a000…", "name": "Zone A", "code": "ZONE-A", "description": "…",
  "status": "ACTIVE", "inspectorCount": 3, "activeInspectionCount": 20,
  "createdAt": "…", "updatedAt": "…" }
```

`code` is normalised to upper case and must be unique (letters, digits, hyphens).
**There is no delete** — set `status: "INACTIVE"` instead. Inspectors and historic inspections
reference zones, and those references need to stay resolvable.

Three zones (`ZONE-A/B/C`) exist on a fresh database. Rename them freely.

---

## 5. Dashboard (§5)

```
GET /api/admin/dashboard/overview?period=30d          ← everything, one call
GET /api/admin/dashboard/summary?period=30d
GET /api/admin/dashboard/compliance-trend?from=&to=
GET /api/admin/dashboard/risk-distribution?zoneId=
```

`period`: `24h`, `7d`, `30d` (default), `90d`, `6m`, `12m`, `all`. An unrecognised value is a
`400` — the dashboard will not quietly show you a different window than you asked for.

Use `/overview` for first paint; the individual endpoints are there for refreshing one panel.

```jsonc
// summary
{
  "totalInspections": 128,        "totalInspectionsChangePercent": 12.5,
  "potentialViolations": 31,      "potentialViolationsChangePercent": -8.2,
  "highRiskProducts": 6,          "repeatOffenders": 4,
  "inconclusiveCount": 9,         "pendingReviewCount": 17,
  "selectedPeriod": "30d"
}
```

`potentialViolations` is named carefully: rule failures detected, **not confirmed offences**.
Please keep that wording in the UI — the system is not entitled to call them violations until an
inspector does.

`…ChangePercent` compares against the immediately preceding window of equal length.

```jsonc
// compliance-trend — one point per day, including quiet days
{ "date": "2026-08-06", "inspections": 20, "violations": 4,
  "inconclusive": 2, "complianceRate": 80.5 }
```

```jsonc
// risk-distribution
{ "totalProductsTracked": 42,
  "highRisk":   { "count": 6,  "percentage": 14.3 },
  "mediumRisk": { "count": 14, "percentage": 33.3 },
  "lowRisk":    { "count": 22, "percentage": 52.4 } }
```

Counted from the **latest assessment per product**, so a heavily inspected product counts once.

---

## 6. Inspections, violations, products

```
GET   /api/admin/inspections?status=&reviewStatus=&riskLevel=&productId=&inspectorId=
                            &zoneId=&fromDate=&toDate=&search=&page=&limit=&sortBy=&sortOrder=
GET   /api/admin/inspections/{id}                 ← full detail, same shape as README §11
GET   /api/admin/inspections/{id}/evidence
GET   /api/admin/inspections/{id}/history
PATCH /api/admin/inspections/{id}/status          { "status": "ACCEPTED", "note": "…" }

GET   /api/admin/violations?status=&reviewStatus=&severity=&inspectionId=&productId=&zoneId=…
GET   /api/admin/violations/summary
GET   /api/admin/violations/{id}
GET   /api/admin/violations/{id}/evidence
PATCH /api/admin/violations/{id}/status           { "status": "CONFIRMED", "note": "…" }

GET   /api/admin/products?search=&category=…
GET   /api/admin/products/{id}
GET   /api/admin/products/{id}/risk
GET   /api/admin/products/{id}/inspections
GET   /api/admin/products/{id}/violations
GET   /api/admin/products/{id}/history
GET   /api/admin/product-history?productId=&search=…
```

Dates accept `YYYY-MM-DD` or full ISO-8601. A bare `toDate` **includes the whole of that day**.

**`note` is required** when setting an inspection to `OVERRIDDEN` or a violation to `DISMISSED`
(`400 REVIEW_NOTE_REQUIRED`). Make the note field mandatory in the UI for those two choices — it
is what makes the override defensible later.

**Evidence bounding boxes:** a `null` box is meaningful. It records that the region was examined
and the declaration was **absent** — there is nothing to draw a rectangle around. Render a badge,
not a box. Coordinates are pixels on the stored image, origin top-left; scale by
`renderedWidth / naturalWidth`.

**Product risk** (`/products/{id}/risk`) returns the full itemised breakdown plus an
`explanation` string written to be shown verbatim. Please show the breakdown, not just the score.
The risk model is deliberately plain arithmetic rather than machine-learned so that an inspector
can interrogate it; reducing it to a single number in the UI throws that away.

---

## 7. Risk intelligence, analytics, rules, settings

```
GET /api/admin/risk-intelligence/overview | /products | /trends | /offenders
GET /api/admin/analytics/overview | /inspections | /violations | /compliance | /zones | /inspectors
GET /api/admin/reports/export?reportType=&fromDate=&toDate=&zoneId=&format=csv
GET|POST /api/admin/rules       GET|PATCH /api/admin/rules/{id}    PATCH /api/admin/rules/{id}/status
GET /api/admin/rulesets         GET /api/admin/rulesets/{version}
GET|PATCH /api/admin/settings   GET|PATCH /api/admin/profile   POST /api/admin/change-password
```

`/analytics/inspections`, `/violations` and `/compliance` share one shape
(`BreakdownResponse`: `total`, `groups[]`, `secondaryGroups[]`, `series[]`) so you can write one
chart component rather than five.

**Export is CSV only.** Any other `format` returns `400 UNSUPPORTED_EXPORT_FORMAT` rather than
silently sending CSV under a different name. `reportType` is `inspections`, `violations`,
`inspectors` or `zones`. The response is a file download with `Content-Disposition`. Every export
carries a header block stating the period and that the contents are advisory.

**Rules carry a disclaimer on every response.** The shipped ruleset is demonstration scaffolding,
**not** the Legal Metrology (Packaged Commodities) Rules. Please surface that disclaimer wherever
rules or findings are displayed, and in anything printable. It is not decorative — presenting
demo rules as law is the one mistake that would sink the project's credibility.

Settings are a whitelist. `GET` returns editable keys under `settings` and contextual values
under `readOnlyInfo`. Sending an unknown key returns `400` listing what is editable.

---

## 8. Error codes worth handling explicitly

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | check `errors[]` for per-field messages |
| `REVIEW_NOTE_REQUIRED` | 400 | override or dismissal without a note |
| `UNSUPPORTED_EXPORT_FORMAT` | 400 | export format other than csv |
| `INVALID_CREDENTIALS` | 401 | bad login |
| `REFRESH_TOKEN_INVALID` | 401 | token unknown or already used → sign out |
| `REFRESH_TOKEN_EXPIRED` | 401 | ordinary session expiry → sign in again |
| `ACCOUNT_DISABLED` | 403 | account deactivated |
| `FORBIDDEN` | 403 | non-admin hitting `/api/admin/**` |
| `CANNOT_MODIFY_OWN_ACCOUNT` | 403 | admin deactivating themselves |
| `INSPECTOR_NOT_FOUND` / `ZONE_NOT_FOUND` | 404 | |
| `EMAIL_ALREADY_REGISTERED` | 409 | |
| `ZONE_CODE_ALREADY_EXISTS` / `INSPECTOR_CODE_ALREADY_EXISTS` | 409 | |

---

## 9. Getting started

```bash
# 1. bootstrap an admin (registration is still open — will be restricted later)
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Raj Kumar","email":"admin@example.com","password":"Admin12345","role":"ADMIN"}'

# 2. log in
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin@example.com","password":"Admin12345"}'

# 3. zones already exist
curl -s localhost:8080/api/admin/zones -H "Authorization: Bearer $TOKEN"

# 4. create an inspector — note the temporaryPassword in the response
curl -s -X POST localhost:8080/api/admin/inspectors -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Priya Raman","email":"priya@example.com","phone":"+919876543210",
       "zoneId":"a0000000-0000-4000-8000-000000000001"}'
```

The dashboard will be empty until inspections exist. To generate data, run the inspector-side
flow from README §11 a few times against the same product — the mock analyser cycles through
`NON_COMPLIANT`, `COMPLIANT` and `INCONCLUSIVE`, and the risk score climbs from LOW into HIGH as
history accumulates. That progression is the best thing to demo.

Questions, or anything that doesn't match: ask before working around it. If a shape here is
awkward for the UI, it is easier to change now than after both sides have built on it.
