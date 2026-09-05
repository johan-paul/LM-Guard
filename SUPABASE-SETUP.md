# Connecting LM-GUARD to Supabase

Everything here has been executed against a real PostgreSQL 16 instance. What has **not** been
executed is a connection to your actual Supabase project — I have no network route to it and no
password, and would not want either. Where something is unverified this document says so.

---

## 1. What already existed

The backend was built for Supabase from the start. It already had:

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` environment variables and the PostgreSQL driver
- Flyway enabled, `ddl-auto: validate`, migrations `V1`–`V16`
- `SupabaseStorageService` behind a `FileStorageService` interface, selected by
  `STORAGE_PROVIDER=supabase`, with a local-disk alternative
- `SupabaseProperties` reading `SUPABASE_URL` / `SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY`

So this was an integration and verification job, not a rewrite. Nothing in the entity model,
services, controllers, rule engine, evidence engine, risk engine or security was replaced.

---

## 2. Supabase project setup

**Database.** Nothing to create. Flyway builds all 14 tables on first start.

**Storage.** Create three buckets under **Storage → New bucket**, and mark each **Public**:

| Bucket | Holds | Env var if you rename it |
|---|---|---|
| `package-images` | uploaded package photographs | `STORAGE_BUCKET_PACKAGE_IMAGES` |
| `evidence` | cropped evidence regions | `STORAGE_BUCKET_EVIDENCE` |
| `reports` | generated reports | `STORAGE_BUCKET_REPORTS` |

> The bucket the code calls `package-images` is what your spec called `inspection-images`. The
> name is entirely yours — set `STORAGE_BUCKET_PACKAGE_IMAGES=inspection-images` and create it
> under that name. I left the default alone rather than renaming it in code, because the name is
> already stored inside every `image_path` of every existing inspection.

Buckets must be **public** for this build: the backend returns public object URLs. Making them
private and issuing signed URLs is a change confined to `SupabaseStorageService`, and is the right
move once inspection images count as restricted evidence.

---

## 3. Configuration

```bash
cp .env.example .env      # then fill in the three secrets
```

```properties
DB_URL=jdbc:postgresql://db.ukzhctgmnyjluoqnktpc.supabase.co:5432/postgres?sslmode=require
DB_USERNAME=postgres
DB_PASSWORD=<Supabase → Project Settings → Database → password>

SUPABASE_URL=https://ukzhctgmnyjluoqnktpc.supabase.co
SUPABASE_ANON_KEY=<Project Settings → API → anon public>
SUPABASE_SERVICE_ROLE_KEY=<Project Settings → API → service_role>

STORAGE_PROVIDER=supabase
JWT_SECRET=<openssl rand -base64 48>
```

Three things that matter:

**`sslmode=require` is not decoration.** Without it the driver may negotiate an unencrypted
connection, and your database password crosses the network in the clear.

**Port 5432, not 6543.** Flyway runs DDL inside transactions; Supabase's transaction pooler
breaks that. Use the direct connection or the session pooler, both on 5432.

**`SUPABASE_SERVICE_ROLE_KEY` bypasses row level security.** It is server-side only. It must
never reach the React app, a `VITE_` variable, a commit, or a screenshot. The backend never
returns it through any endpoint and never writes it to a log — verified by grep, see §7.

Then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring Boot does not read `.env` by itself. Either export the variables
(`set -a; source .env; set +a` on macOS/Linux) or set them in your IDE's run configuration.

---

## 4. Troubleshooting: IPv6

The most likely thing to go wrong, and it is not your configuration.

`db.<project>.supabase.co` resolves to an **IPv6** address on most Supabase projects. If your
network has no IPv6 route you will see, after a long pause:

```
java.net.SocketTimeoutException: connect timed out
org.postgresql.util.PSQLException: The connection attempt failed.
```

Check first:

```bash
nslookup db.ukzhctgmnyjluoqnktpc.supabase.co
ping -6 db.ukzhctgmnyjluoqnktpc.supabase.co
```

An AAAA record and no A record, plus a failing ping, confirms it.

**The fix is a different `DB_URL`, not a code change.** In Supabase → Project Settings →
Database → Connection string, choose **Session pooler**. It is IPv4-reachable. Copy the host and
username Supabase shows you — the pooler username is *not* plain `postgres`, it has the project
ref appended:

```properties
DB_URL=jdbc:postgresql://<pooler-host-supabase-shows-you>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<your-project-ref>
```

I have deliberately not written a pooler hostname here. Supabase assigns it per region and
guessing it would send you chasing a host that does not exist.

Nothing in the Java code assumes either IP version. The address family is entirely a property of
the URL you supply.

---

## 5. Verify the database

After the first successful start, open **Supabase → SQL Editor** and paste
[`docs/supabase-verification.sql`](supabase-verification.sql). Every statement is read-only.

What to expect:

| Section | Expected |
|---|---|
| 1. Tables | 15 rows — 14 LM-GUARD tables plus `flyway_schema_history` |
| 2. Expected tables | every row `present` |
| 3. Flyway history | 16 rows, `succeeded 16, failed 0, current_version 16` |
| 7. Row counts | `zones = 3` (seeded by V11), everything else 0 on a fresh database |
| 11. Secrets | **zero rows from both queries** — passwords BCrypt, refresh tokens SHA-256 |

If section 3 shows a row with `success = false`, that migration failed part-way. Fix the cause,
delete that single row, and restart — Flyway will not retry a migration it has recorded as failed.

---

## 6. Verify the whole pipeline

```bash
# 1. bootstrap an administrator
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Admin","email":"admin@example.com","password":"Admin@12345","role":"ADMIN"}'

# 2. create an inspector (as that admin), then sign in as them and run:
./scripts/seed-demo-inspections.sh      # or the .ps1 on Windows
```

Then re-run section 8 of the verification SQL. A completed inspection should carry extracted
fields, a risk score, and — when non-compliant — violations each with evidence.

Section 10 tells you whether images went to Supabase Storage or local disk. If it says `local
disk`, `STORAGE_PROVIDER` was not picked up.

---

## 7. What was actually verified, and what was not

**Verified — executed against real PostgreSQL 16 in a sandbox:**

- All 16 Flyway migrations apply cleanly, in order, to an empty PostgreSQL database
- The application starts with `ddl-auto: validate` — Hibernate checked all 14 entities against
  the Flyway-built schema and found no mismatch. This was the single largest open risk in the
  project and it is now closed
- 13 inspections run end to end: 5 products, 10 product versions, 91 extracted fields,
  9 violations, 9 evidence records, 13 risk scores, all persisted
- `docs/supabase-verification.sql` runs with zero errors and both secret checks return no rows
- The storage health check is non-fatal when Storage is unreachable, and the service role key
  appears **zero** times in the startup log
- Full test suite: **42 of 42 passing**

**NOT VERIFIED — I could not do these:**

- **A connection to your actual Supabase project.** No network route from the sandbox, and no
  password. Everything above used a local PostgreSQL 16 with the same driver, the same Flyway
  version and the same configuration shape — which proves the migrations and the schema, but not
  your credentials, your network path, or your SSL handshake.
- **A real upload to Supabase Storage.** The code path is unchanged from what already existed and
  I did not modify it; I only added a startup probe. The local-disk provider was exercised
  end to end.
- **IPv6 reachability from your machine.** Cannot be tested from here.
- **Your six pre-existing tests.** Still not run — see `APPLYING-THIS-CHANGE.md`.

The first time you point this at Supabase, watch the startup log for `Successfully applied 16
migrations` and for the storage bucket lines. Those two are where a real problem will show up.
