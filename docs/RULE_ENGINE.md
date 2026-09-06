# Rule Engine

The deterministic engine itself (`src/main/java/com/lmguard/rules/DeterministicRuleEngineService.java`)
was already implemented before this session and is unchanged -- its class-level javadoc is the
authoritative description of the evaluation procedure (confidence thresholds, absence-vs-unread
handling, worst-wins aggregation). This document covers what this session added on top of it:
real legal content, and how it is wired in.

## What changed vs. what didn't

**Unchanged:** `RuleDefinition`, `RuleType`, `RuleSet`, `RuleCatalog`, `Rule` entity,
`rules` table schema, the evaluation algorithm itself, and every existing rule-engine test.

**Added:** a second bundled ruleset, `src/main/resources/rules/lm-pc-2011-rules.json`
(`rulesetVersion: LM-PC-2011-v1`), built from the actual text of the Legal Metrology (Packaged
Commodities) Rules, 2011 supplied by the user -- see `legal-rules/` for the full extraction and
`legal-rules/RULE_REVIEW.md` for a correction this made to the previous demo ruleset's
`DEMO-ORG-001` rule (see below). `application.yml` now defaults to loading this ruleset instead
of the demo one; the demo ruleset (`DEMO-2026.1`) is untouched on disk and is what the test
suite still pins to, and either can be selected via `RULES_ACTIVE_VERSION` /
`RULES_SAMPLE_FILE` without a code change.

## Why only 6 of the 34 rules are engine-loaded

The engine's `RuleType` enum has four checks: `REQUIRED_FIELD`, `PATTERN_MATCH`,
`NUMERIC_RANGE`, `MIN_LENGTH`. It has **no applicability/exemption model** -- a rule loaded
into the engine is evaluated unconditionally against every inspection. Most of the 2011 Rules'
34 top-level rules are conditional (apply only to wholesale packages, only to imports, only
above/below a quantity threshold, only outside a list of exemptions) or are not something a
package photo can verify at all (physical tests, registration records, advertisements).
Loading those into the engine as if they were unconditional would produce **false positives**
-- exactly what the brief calls the most important thing to avoid.

So this session loaded only the six Rule 6 declarations that (a) map to a fact the AI layer
already reports (`ProductField.MRP/NET_QUANTITY/MANUFACTURER/MANUFACTURE_DATE/CONSUMER_CARE/
COMMODITY_NAME`) and (b) apply to essentially every retail package under Chapter II with no
conditional gate: `LM-PC-6-1-a` through `LM-PC-6-2` in
`src/main/resources/rules/lm-pc-2011-rules.json`. Everything else the 2011 Rules require is
still **accounted for** -- see `legal-rules/RULE_COVERAGE.md` for all 34, with an honest status
for each (image-checkable, physical-test-required, external-data-required, human-review-required)
-- just not loaded into a deterministic engine that has no way to represent "only if imported"
or "only if not exempt."

## Adding applicability support (a scoped, not-yet-built follow-up)

If a future session wants to load more of `legal-rules/lm_pc_2011_rules.json` into the engine,
the minimal additive change (i.e. one that does not touch `ComplianceStatus`,
`InspectionStatus`, the DB schema, or the frontend contract) is:

1. Add two nullable fields to `RuleDefinition` (`appliesWhenField`, `appliesWhenPattern`) via a
   **second, non-canonical constructor** that delegates to the existing 15-arg one with nulls
   -- this keeps every existing call site (all of `DeterministicRuleEngineServiceTest`,
   `InspectionAnalysisServiceTest`) compiling unchanged, and needs `@JsonCreator` on the
   canonical constructor so Jackson does not get confused between the two.
2. In `DeterministicRuleEngineService.evaluateRule`, before anything else: if
   `appliesWhenField` is set, look up that fact; if it's absent, return INCONCLUSIVE
   ("applicability could not be determined") -- reusing the existing status, no new enum
   value; if present but doesn't match `appliesWhenPattern`, return a plain pass (the rule
   simply doesn't apply here); otherwise fall through to today's logic unchanged.

This was scoped, not built, in this session -- see `docs/IMPLEMENTATION_AUDIT.md` §4 for why
(most of what it would gate on, e.g. "is this package imported," "is this an industrial
consumer," is not a fact the current AI layer extracts either, so the applicability engine and
the fact-extraction it needs are really one piece of future work, not two).

## Traceability

Every rule in `lm-pc-2011-rules.json` cites its real rule number in `description` (e.g. "Rule
6(1)(e)") and is regression-tested against the actual bundled file in
`src/test/java/com/lmguard/rules/LmPc2011RuleSetTest.java` -- not a hand-copied fixture, the
real classpath resource, loaded exactly the way `RuleCatalog` loads it in production.
