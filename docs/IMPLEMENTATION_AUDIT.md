# LM-GUARD Implementation Audit

Date: 2026-09-06
Auditor: Claude Code, working directly in this repository.

This document is the required first deliverable before any implementation work: what already
exists, what was missing, and exactly what this pass changed. It also records where the original
request ("full PaddleOCR + VLM pipeline, CV-based typography checks, barcode decoding, physical
test automation, multi-panel fusion") exceeds what a single engineering session can responsibly
ship, and what was substituted instead.

## 1. What already existed (verified by reading the code, not assumed)

The backend is a mature, well-tested Spring Boot 3 application, not a skeleton:

- **Auth**: JWT access + rotating single-use refresh tokens, `INSPECTOR`/`ADMIN` roles
  (`security/`, `AuthController`, `AuthService`).
- **Inspections**: create → upload image → analyze → get/list/filter, backed by Postgres/Flyway
  (`InspectionController`, `InspectionService`, `InspectionAnalysisService`).
- **AI seam**: a clean interface (`AIAnalysisService`) with three implementations already wired —
  `MockAIAnalysisService` (deterministic per-inspection demo facts), `ExternalAIAnalysisService`
  (HTTP client for a Python service that **did not exist yet**), and `AiAnalysisRouter` (mock/real
  switch + fallback-to-mock on external failure). This seam is exactly the contract this session's
  Python service needed to satisfy — nothing about it needed to change.
- **Deterministic rule engine**: `DeterministicRuleEngineService` — data-driven rules
  (`REQUIRED_FIELD`, `PATTERN_MATCH`, `NUMERIC_RANGE`, `MIN_LENGTH`), confidence-threshold logic
  that already implements the single most important false-positive protection in the whole spec:
  *low confidence and "not assessed" both resolve to INCONCLUSIVE, never NON_COMPLIANT*. This is
  exactly the "AI observes, rules decide" boundary the brief asked for, already built and already
  unit-tested (`DeterministicRuleEngineServiceTest`, 16 tests).
- **Rules storage**: `Rule` entity + Flyway `V6__create_rules.sql`, versioned, admin CRUD via
  `RuleAdminService`/`RuleController`. `RuleCatalog` loads from the DB first and falls back to a
  bundled JSON file; `DemoRuleSeeder` seeds that file into the DB on first boot.
- **Evidence**: every finding gets an `Evidence` row pointing at the image + bounding box
  (`EvidenceService`), including for absences.
- **Risk**: `WeightedRiskEngineService` — transparent, weighted, testable.
- **History**: `ProductVersion`/`VersionSource`, change detection in `ProductService`.
- **Online comparison**: `OnlineListing` entity + mismatch detection in
  `InspectionAnalysisService.detectOnlineMismatch` (currently compares against whatever listing was
  last captured; no live scraper — this is a deliberate, documented seam, not a gap I introduced).
- **Human review**: `reviewStatus` on inspections, admin override endpoints, and an explicit
  invariant enforced already — an override can never rewrite the automated `status` verdict.
- **Storage**: local + Supabase implementations behind `FileStorageService`.
- **Tests**: 6 test classes, ~40+ test methods, all passing before this session's changes
  (`DeterministicRuleEngineServiceTest`, `InspectionAnalysisServiceTest`, `InspectionControllerTest`,
  `WeightedRiskEngineServiceTest`, `JwtServiceTest`, `MockAIAnalysisServiceTest`,
  `LmGuardApplicationTests`).
- `ANTIGRAVITY-AUDIT-PROMPT.md` shows a previous, thorough API-contract audit was already run
  against this backend — the API surface is not unaudited territory.

**Conclusion: this is not a project that needed rebuilding.** The architecture described in the
request ("AI sees, rules decide, human enforces") was already implemented, not proposed.

## 2. What was actually missing

1. **No Python AI/OCR/VLM service existed at all.** `ExternalAIAnalysisService` had nothing to
   call; `AI_MOCK_MODE` defaulted to `true` so the whole system ran on fixed mock facts.
2. **The rules were 100% placeholder.** `sample-rules.json` is explicitly labeled
   `DEMO / SAMPLE RULES ONLY` and states outright it is *not* the Legal Metrology Rules. One of its
   9 rules (`DEMO-ORG-001`, "country of origin required") does not correspond to any actual
   requirement in the 2011 Rules for domestic packages — see §5 below.
3. **No structured extraction of the actual Legal Metrology (Packaged Commodities) Rules, 2011**
   existed anywhere in the repo.
4. **No applicability/exemption modeling** — the engine has no concept of "this rule doesn't apply
   to this package type," which the real Rules require constantly (Rule 3 excludes >25kg/25L and
   industrial/institutional packages; Rule 26 exempts ≤10g/10ml packages entirely; food packages
   are carved out of several sub-rules in favour of the Prevention of Food Adulteration Act, etc.).
5. **No image quality / preprocessing / OCR / VLM code** of any kind (Java or Python).
6. **Single image per inspection only** — `Inspection.imageUrl` is one column, not a collection;
   there is no multi-panel (front/back/label) model in the schema, DTOs, or controller.

## 3. What this pass implemented

| Area | What was built | Where |
|---|---|---|
| Legal source extraction | The complete, faithful text of the supplied PDF (43 pages, all 34 rules, all 7 schedules) was extracted programmatically (`pypdf`) and read in full — not summarized from memory. | scratch file, read in full during this session |
| Legal rule registry | Every rule 1–34 and their legally-significant sub-clauses, provisos, explanations and exceptions, machine-readable, cited by rule number and PDF page. | [legal-rules/lm_pc_2011_rules.json](../legal-rules/lm_pc_2011_rules.json) |
| Schedules | First–Seventh Schedule structured as data: permissible-error tables, standard package sizes, the "when packed" list, unit-of-measure exceptions, sampling and testing procedure. | [legal-rules/lm_pc_2011_schedules.json](../legal-rules/lm_pc_2011_schedules.json) |
| Coverage accounting | Every one of the 34 rules classified by verification method (image-checkable / physical-test-required / external-data-required / human-review-required) — 100% coverage, not 100% automation. | [legal-rules/RULE_COVERAGE.md](../legal-rules/RULE_COVERAGE.md) |
| Legal review notes | Ambiguities, since-withdrawn provisos found *inside the supplied document itself*, and the correction to `DEMO-ORG-001`. | [legal-rules/RULE_REVIEW.md](../legal-rules/RULE_REVIEW.md) |
| Real engine ruleset | The image-checkable declaration rules from Rule 6 (and the numeric error tolerance from the First Schedule) converted into the **existing** `RuleDefinition`/`RuleType` schema — no new rule engine, no schema change. | `src/main/resources/rules/lm-pc-2011-rules.json` |
| Config | `LM-PC-2011-v1` made the default seeded/active ruleset for dev; env-overridable; `DEMO-2026.1` kept, unmodified, as a fallback file and as what the test suite still pins to. | `application.yml` |
| AI/OCR/VLM service | Real FastAPI service: multi-pass OCR (`rapidocr-onnxruntime`, CPU, no system dependency) + Claude vision for semantic extraction, implementing `AiAnalyzeRequest`/`AiAnalyzeResponse` exactly. | `ai-service/` |
| Tests | New Java tests for the real ruleset; new Python tests for normalization/confidence/contract shape. | `src/test/...`, `ai-service/tests/...` |

## 4. What this pass explicitly did NOT build, and why

Being honest about this is more useful than a false checklist. These are the mega-prompt items
that require substantially more than one engineering session, hardware, legal sign-off, or a live
production dataset that does not exist here:

- **Multi-panel image fusion.** The DB schema, `Inspection` entity, upload endpoint and DTOs all
  model **one image per inspection**. Adding front/back/label panels is a real schema change
  (new `inspection_images` table, controller changes, `InspectionAnalysisService` rewritten to
  merge N `AIAnalysisResult`s field-by-field, taking the highest-confidence observation per field).
  I did not do this because it changes the API contract and the DB schema of a system the brief
  said to preserve, and doing it half-right (e.g. bolting a `List<String>` onto the DTO without
  the merge logic, storage, and mapper changes) would be worse than not doing it. **This is the
  single highest-value follow-up** if the team wants a second session — I scoped it, I did not
  build it.
- **CV-based text-height / contrast / placement checks (Rule 7, Rule 8, Rule 9).** The brief itself
  says not to claim millimetre accuracy from raw pixels without a calibration reference, and no
  such reference (a coin, a ruler, a barcode of known physical size) is available in an
  arbitrary phone photo. These rules are catalogued as `NOT_ASSESSABLE_FROM_IMAGE` rather than
  faked.
- **Barcode/GTIN decoding.** Not implemented. It's a genuinely useful, low-risk addition
  (`pyzbar`/`zxing-cpp`), but it doesn't yet connect to anything — there is no product-identity
  registry to look GTINs up against in this repo. Noted as a follow-up in
  `docs/AI_PIPELINE.md`.
- **Physical testing (Rules 19–23, First/Fifth/Sixth/Seventh Schedule).** Correctly out of scope
  for a camera. Catalogued, not automated, exactly as the brief requires.
- **A live online-price data source.** `OnlineListing` already exists as the seam; I did not
  attach a scraper, because a brittle scraper is explicitly called out as unwanted, and a
  trustworthy verified dataset for arbitrary Indian retail SKUs does not exist in this repo or
  session. Left as documented, working seam.
- **PaddleOCR specifically.** Requires PaddlePaddle, which is a heavy, GPU-oriented dependency with
  a poor track record on plain pip installs in a constrained Windows environment. I used
  `rapidocr-onnxruntime` instead — same underlying PP-OCR model family exported to ONNX, pure pip
  install, CPU-only, no system binary — and said so plainly rather than claiming PaddleOCR.
- **A new `NOT_APPLICABLE` / `NOT_ASSESSABLE_FROM_IMAGE` compliance status in the Java engine.**
  Adding it would ripple into `ComplianceStatus`, `InspectionStatus`, `ViolationStatus`, the Flyway
  CHECK constraints, the mapper, and the documented frontend contract (`FRONTEND-API-CONTRACT.md`)
  — exactly the kind of "break existing APIs" the brief said not to do. Instead, applicability is
  handled by only loading rules into the **engine** that apply unconditionally to a retail package
  under Chapter II (the overwhelming majority of real inspections), and cataloguing the
  conditional/exempted rules in the registry as requiring human classification of package type —
  which is what `RULE_COVERAGE.md` records for each of them.

## 5. A correction to the existing demo ruleset

`sample-rules.json`'s `DEMO-ORG-001` requires `ORIGIN` ("country of origin") on every package. That
is not what the 2011 Rules actually say for domestic packages: Rule 6(1)(a) and Rule 10 require the
**name and address of the manufacturer/packer**, and *additionally* the **importer's** name and
address only when the package is imported. There is no supplied-document, 2011-vintage basis for
requiring a country-of-origin declaration on a domestically made package. (That requirement was
added to Indian labeling law later, by amendment, and is out of scope for *this* document, which
the brief was explicit must be the sole legal source.) The demo file is left untouched — it is
clearly labeled as a demo — and `legal-rules/RULE_REVIEW.md` records the correction. The real
ruleset (`lm-pc-2011-rules.json`) does not carry this rule forward.

## 6. Existing API/DB contracts (unchanged by this work)

- `AIAnalysisService.analyzeImage(String imageUrl, UUID inspectionId) -> AIAnalysisResult` — the
  seam the Python service now actually implements, unmodified.
- `AiAnalyzeRequest{imageUrl, inspectionId, requestedFields}` /
  `AiAnalyzeResponse{modelVersion, warnings, fields[]}` — the wire contract; unmodified.
- `RuleDefinition` record (15-field canonical constructor) — unmodified; no fields added, no
  existing call sites touched.
- `Rule` entity / `rules` table — unmodified.
- REST API surface documented in `FRONTEND-API-CONTRACT.md` — unmodified.

## 7. Files this pass will create or change

See the final completion report delivered in chat at the end of this session for the exact list —
kept there rather than duplicated here so this document doesn't go stale the moment it's committed.
