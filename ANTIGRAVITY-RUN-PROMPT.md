# Antigravity prompt — LM-GUARD: run + verify

Paste everything below the line into Antigravity's agent, with the LM-Guard project folder open.

Notes before you do:

- This project needs **two processes running at once**: the Python `ai-service/` (FastAPI on
  `:8000`) and the Spring Boot backend (`:8080`). Start ai-service first.
- `.env` and `ai-service/.env` already exist locally with real, working Supabase and Gemini
  credentials. Do not create new ones, do not regenerate them, do not print their contents.
- **Ignore `ANTIGRAVITY-AUDIT-PROMPT.md`** in this repo. It describes a ~71-endpoint admin surface
  that does not exist in the current codebase — the real API is ~20 endpoints across 7 controllers,
  confirmed live via `/v3/api-docs`. That file is stale, from an earlier phase of the project.

---

You are starting and verifying the **LM-GUARD** backend — an AI-assisted Legal Metrology
inspection platform — end to end, on this machine, using its real configuration. This is **not**
a code-review or audit task: don't propose refactors, don't touch source code, don't modify the
rule engine or AI service logic. Your job is to get it running correctly and prove it's working
with real evidence, not assumptions.

## The two things this run is not allowed to do silently

1. **Never let `AI_MOCK_MODE` resolve to true without saying so.** `MockAIAnalysisService` exists
   and is legitimate for unit tests, but if a real inspection returns `"aiProvider":"MOCK"`, that
   is not "working" — stop and report it, don't call the run a success.
2. **Never fall back to H2 or local disk storage without saying so.** `STORAGE_PROVIDER=supabase`
   in `.env` means every image must land in real Supabase Storage. A log line reading
   `Using LOCAL file storage`, or an `imageUrl` starting with `http://localhost:8080/files/...`,
   means the real config didn't take effect — that's a setup bug to report, not something to route
   around.

## Step 1 — Start the AI service first

```
cd ai-service
```

On Windows, do not assume `.venv\Scripts\Activate.ps1` works — it's a PowerShell-only script and
does nothing (no error) if you're in `cmd.exe`, which then makes `uvicorn` "not recognized" even
though it's installed. The reliable, shell-agnostic way is to skip activation and call the venv's
own interpreter directly:

```
.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000 --env-file .env
```
(macOS/Linux: `.venv/bin/python -m uvicorn app.main:app --port 8000 --env-file .env`)

Leave this running. In a second terminal, confirm:
```
curl http://127.0.0.1:8000/health
```
Expect `{"status":"ok","vlmEnabled":true,"apiKeyRequired":false}`. If `vlmEnabled` is `false`,
`GEMINI_API_KEY` in `ai-service/.env` isn't loading — stop and report that, don't proceed to Spring.

## Step 2 — Start Spring Boot

Spring Boot does **not** auto-load `.env` — there is no dotenv library in this project. You must
load `.env`'s variables into the shell's own environment before running Maven, in the same
terminal session, or Spring will fail on `${DB_URL}` etc. having no value.

PowerShell:
```
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
    }
}
.\mvnw.cmd spring-boot:run
```

macOS/Linux/Git Bash:
```
set -a; source .env; set +a
./mvnw spring-boot:run
```

Watch the startup log for these exact lines — they're the proof the real config took effect, not
just that the app started:

- `Database: jdbc:postgresql://...` followed by no errors, then a Flyway line
  (`Schema "public" is up to date` or a migration list) — confirms real Postgres, not H2.
- `Using Supabase Storage at https://<project-ref>.supabase.co` — confirms real Storage, not local
  disk. `Using LOCAL file storage` means `STORAGE_PROVIDER` isn't resolving to `supabase` — report
  it, don't switch tests to local mode to make things pass.
- `Started LmGuardApplication in ... seconds` with nothing red above it.

Then confirm the app is actually reachable:
```
curl http://localhost:8080/v3/api-docs
```
Any JSON back (not connection-refused) means it's up.

## Step 3 — Run one real inspection, and prove it's real

Register a user, create a product, create an inspection, upload
`api-tests/fixtures/sample-package.jpg`, then analyze it:

```
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"name\":\"Verify\",\"email\":\"verify_TIMESTAMP@lmguard.test\",\"password\":\"Str0ngPassw0rd!\"}"
# capture the token, then:
curl -X POST http://localhost:8080/api/products -H "Content-Type: application/json" -H "Authorization: Bearer TOKEN" -d "{\"productName\":\"Verify Run\",\"brand\":\"Test\",\"category\":\"packaged_food\",\"barcode\":\"8900000000001\"}"
# capture the product id, then:
curl -X POST http://localhost:8080/api/inspections -H "Content-Type: application/json" -H "Authorization: Bearer TOKEN" -d "{\"productId\":\"PRODUCT_ID\"}"
# capture the inspection id, then:
curl -X POST http://localhost:8080/api/inspections/INSPECTION_ID/image -H "Authorization: Bearer TOKEN" -F "file=@api-tests/fixtures/sample-package.jpg;type=image/jpeg"
curl -X POST http://localhost:8080/api/inspections/INSPECTION_ID/analyze -H "Authorization: Bearer TOKEN"
```

**This last call can legitimately take up to ~4 minutes.** `AI_SERVICE_TIMEOUT_MS=240000` on the
Spring side exists precisely because Gemini's real API sometimes needs multiple retries (3
attempts, exponential backoff, up to 60s each). Do not treat a slow response as a hang; do not cap
a curl timeout under 250 seconds; do not report "it's stuck" before ~4 minutes have actually
passed.

In the response, check specifically:

- `"aiProvider"` must be `"EXTERNAL"`, never `"MOCK"`.
- `"imageUrl"` must start with `https://<project-ref>.supabase.co/storage/v1/object/public/...`,
  never `http://localhost:8080/files/...`.
- `"rulesetVersion"` should read `"LM-PC-2011-v1"`.
- If `MANUFACTURER` and `COMMODITY_NAME` came back with real (non-null) values, that's Gemini's
  semantic extraction working — those two fields cannot come from regex/OCR alone.

If ai-service's own terminal shows a line like `Gemini call failed (attempt 1/3, ...); retrying in
1.0s`, that's the retry logic working as designed under a real, flaky network condition — not a
bug.

If the analysis genuinely fails (not just slow) with `errorCode: AI_SERVICE_ERROR`, retry once.
Gemini's free tier has a hard daily quota (20 requests/day for the configured model) and
occasional real `503`s; a quota-exhaustion error is an external condition, not a defect in this
codebase, and should be reported as such rather than worked around.

## Step 4 — Run the test suites

```
mvnw.cmd test                                              # Java — expect 57/57
cd ai-service && pytest tests/ -q                          # Python — expect 72/72
bash api-tests/run-http-tests.sh http://localhost:8080     # API — expect 41 or 42 / 41 or 42
```
(The API script is bash — run it from Git Bash or WSL if you're on Windows cmd/PowerShell.) One
API check self-skips when a run happens to produce zero violations; that's a script design choice,
not a failure.

## Rules

1. **Never report success you didn't observe.** If a step didn't actually run (e.g. you couldn't
   wait the full ~4 minutes for analyze), say `NOT TESTED`, not `PASS`.
2. **Never print, log, or write out the contents of `.env` or `ai-service/.env`.** Confirm values
   are *present* and *correctly formatted* without echoing them.
3. **Do not modify application code, the rule engine, or AI service logic** to make a run succeed
   faster or more reliably. If something is genuinely broken, report the exact file/line and stop
   — don't fix it yourself without asking.
4. **Do not fall back to mock mode or H2/local storage to get a green result.** A run that had to
   fall back is a run that failed, and should be reported that way.
5. **Do not commit `.env` files or print secrets in your final report.**

## Final answer

- Whether ai-service and Spring Boot both started cleanly, with the exact log lines that prove
  real Postgres + real Storage were used.
- The full real inspection result: `aiProvider`, `imageUrl`, verdict, and how long the analyze
  call actually took.
- Java / Python / API test counts, exactly as observed.
- Anything that didn't work, with the exact error text — not a guess at the cause.
