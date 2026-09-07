# LM-GUARD — run it in one command

No PostgreSQL. No Supabase account. No environment variables. No internet.

```bash
# Windows
run.cmd

# macOS / Linux
./run.sh
```

That starts the app on the `local` profile: an embedded H2 database, the mock AI analyser, and
local disk storage. The whole inspection pipeline runs.

Then open **<http://localhost:8080/swagger-ui/index.html>**

| | |
|---|---|
| Admin | `admin@lmguard.local` / `Admin@12345` |
| Inspectors | `priya.raman@lmguard.local`, `arun.kumar@lmguard.local`, `meera.nair@lmguard.local` — all `Inspector@123` |

Three zones (`ZONE-A/B/C`) and those four accounts are created on first start. Seeding is
idempotent, so restarting will not duplicate them.

---

## Fill it with data worth demonstrating

The app starts with an empty dashboard. In a **second terminal**, with the app running:

```bash
# Windows
powershell -ExecutionPolicy Bypass -File scripts\seed-demo-inspections.ps1

# macOS / Linux
./scripts/seed-demo-inspections.sh
```

This does **not** insert rows into the database. It signs in as a real inspector and drives the
real API — create inspection, upload image, run analysis — thirteen times. Every verdict,
evidence record and risk score you then see was produced by the actual rule and risk engines.
Inserting results directly would have been faster and would have demonstrated nothing.

Actual output from a clean run:

```
==> Demo Namkeen Mixture 500g  (x8)
    1/8  COMPLIANT      risk  10 (LOW)
    2/8  NON_COMPLIANT  risk  10 (LOW)
    3/8  NON_COMPLIANT  risk  45 (MEDIUM)
    4/8  COMPLIANT      risk  40 (MEDIUM)
    5/8  INCONCLUSIVE   risk  50 (MEDIUM)
    ...
```

**That climb is the thing to show a judge.** The same product, inspected repeatedly: the score
rises from 10 to 50 and the band moves LOW → MEDIUM as previous-violation and repeat-issue
history accumulates. Every point of it is arithmetic you can show the working for — open
`GET /api/admin/products/{id}/risk` and the breakdown is itemised, with an `explanation` string
written to be read aloud.

Note also that all three verdicts appear — `COMPLIANT`, `NON_COMPLIANT` and `INCONCLUSIVE`. The
mock analyser cycles them deterministically so you can demonstrate the property the system works
hardest to preserve: **a declaration that could not be read confidently is reported as
`INCONCLUSIVE`, never as a violation.**

One honest note: with this data the score peaks at MEDIUM (50) and does not reach HIGH. Reaching
HIGH needs the repeat-issue factor to fire repeatedly on the same rule. Do not claim HIGH in a
demo unless you see it.

---

## Swagger

`http://localhost:8080/swagger-ui/index.html`

The definition selector at the top right has four groups, because 71 endpoints in one flat list
is unusable:

| Group | Contains |
|---|---|
| `0 - Everything` | all 71 paths — for generating a client |
| `1 - Authentication` | login, refresh, logout, me, change-password |
| `2 - Administrator` | the 50 admin endpoints — **the frontend team wants this one** |
| `3 - Inspector` | inspections, products, evidence, reports |

To call a protected endpoint: `POST /api/auth/login` → copy `accessToken` → **Authorize** button
→ paste (without the word "Bearer") → everything is now callable from the browser.

---

## Reset

```bash
rm -rf data storage-data      # Windows: rmdir /s /q data storage-data
```

`data/` is the H2 database, `storage-data/` the uploaded images. Delete both and the next start
rebuilds from scratch. That is the intended way to recover if a schema change fails to apply —
this database exists to be thrown away.

---

## What this profile does and does not prove

**Does:** the application starts, the Spring context wires up, every endpoint serves, security
works, the pipeline runs end to end, and the admin API returns real aggregates.

**Does not:** validate the Flyway migrations. They are deliberately PostgreSQL-specific
(`TIMESTAMPTZ`, `COMMENT ON`) and are switched off here; Hibernate derives the schema from the
entities instead. Only running against real PostgreSQL proves `V1`–`V16` are correct, and that
is still outstanding — see `APPLYING-THIS-CHANGE.md` §5.

---

## Running against real PostgreSQL / Supabase

Use the `dev` or `prod` profile with the environment variables in `.env.example`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

There Flyway owns the schema and `ddl-auto: validate` fails startup if the entities and the
migrations disagree — which is the behaviour you want everywhere that holds data you care about.

Remember the connection-port detail from the project notes: use the **direct connection or
session pooler (5432)**, not the transaction pooler (6543), or Flyway's DDL transactions break.
