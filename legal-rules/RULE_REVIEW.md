# Legal Rule Review Notes

Things a human legal reviewer should specifically check before this registry or the
`LM-PC-2011-v1` engine ruleset is relied on for real enforcement.

## 1. This is the 2011 text as supplied, not the current amended text

The source PDF is the **original 2011 notification** (GSR 202(E), 7 March 2011). It contains its
own footnotes flagging several provisions that were later withdrawn or amended by GSR 748(E)
(dated 24.10.2011, effective 01.07.2012) and GSR 734(E) (dated 30.09.2011):

- Rule 5's "non-standard size" declaration proviso — noted as withdrawn wef 01.07.2012.
- Rule 6(1)(d)'s rubber-stamp proviso — noted as withdrawn wef 01.07.2012.
- Rule 6(6)'s packaging-material exhaustion deadline — noted as time-extended by GSR 734(E).
- Rule 12(6)'s wording on misleading quantity expressions — noted as amended (broadened) wef
  01.07.2012.
- Rule 19(8)'s "release... once compliance is ensured" wording — noted as amended wef 01.07.2012.
- Rule 26's 10g-20g/10ml-20ml declaration proviso — noted as withdrawn wef 01.07.2012.
- Fourth Schedule item 15 (ice cream) — noted as amended from "Volume" to "Weight" wef
  01.07.2012.

**None of these amending notifications (GSR 748(E), GSR 734(E)) were supplied to this system.**
Per the brief's explicit instruction not to fill legal gaps from model memory, `legal-rules/
lm_pc_2011_rules.json` registers the **original 2011 wording** for each of these and carries an
`amendmentNote` flagging that a later amendment exists. It does not guess the amended text. Before
this registry is used for a live inspection, a legal reviewer must supply the actual amendment
text (or confirm the 2011 text still governs the relevant provision in the applicable
jurisdiction/date) and this file should be updated to `LM-PC-2011-v2` accordingly.

Separately: the Legal Metrology (Packaged Commodities) Rules were also substantially re-notified
in 2011 as a fresh rule-set in later years (with country-of-origin, e-commerce and unit-price
declarations added) — none of that later material is in the supplied PDF, so none of it appears
here. If LM-GUARD is meant to enforce the rules as they stand *today* rather than as they stood in
2011, the correct fix is to supply the current rules to this pipeline, not to patch this registry
from memory.

## 2. Correction to the existing demo rule `DEMO-ORG-001`

`src/main/resources/rules/sample-rules.json` (the pre-existing demo ruleset, left untouched) has an
unconditional "country of origin must be declared" rule tied to `ProductField.ORIGIN`. Nothing in
the supplied 2011 Rules text requires a country-of-origin declaration on a **domestically
manufactured** package. What the text actually requires (Rule 6(1)(a), Rule 10) is:

- the manufacturer's (or manufacturer + packer's) name and address — always;
- **additionally**, the importer's name and address, but *only* for an imported package;
- **additionally**, for a commodity manufactured outside India but packed in India, the Indian
  packer/importer's name and address on the principal display panel.

So "origin" in the 2011 Rules is expressed indirectly, through where the manufacturer/packer/
importer is located and whether an importer exists at all — not as its own standalone declared
field. `LM-PC-2011-v1` (the real ruleset built this session) does **not** carry an unconditional
ORIGIN-required rule forward, because that would misrepresent this source document as requiring
something it does not require for the common case (a domestic package). The existing `ORIGIN`
field in `ProductField`/the mock AI service is left in place since it's used elsewhere (frontend,
tests) — it is simply not backed by a mandatory rule in the real ruleset. A conditional
"importer's address required, but only when the package is imported" rule was not added to the
engine either, because the engine (`DeterministicRuleEngineService`) has no notion of conditional
applicability today (see `docs/IMPLEMENTATION_AUDIT.md` §4) — this is flagged as `LM-PC-6-1-a`'s
`applicability` note in the registry instead of silently mis-encoded.

## 3. Ambiguities worth a legal reviewer's attention

- **Rule 3's "industrial/institutional consumer" exemption** hinges on *who the package is sold
  to*, which is not visible in a package photograph at all. The registry marks this
  `human_review: true`; nothing in this codebase infers buyer identity.
- **Rule 6(1)(a) Explanation III** carves out food articles into the Prevention of Food
  Adulteration Act, 1954 entirely — meaning a food package's manufacturer-declaration compliance
  should, strictly, be judged under a different Act this system does not model. `LM-PC-2011-v1`
  still evaluates the manufacturer-declaration rule uniformly, since PFA-specific text was not
  supplied either. This is a real scope boundary, not an oversight.
- **Rule 9(1)'s "conspicuous contrast"** and **Rule 7's numeral-height thresholds** need a
  physical calibration reference to verify from a 2D photo (see `docs/AI_PIPELINE.md`). Treating
  OCR confidence as a stand-in for legibility is a reasonable heuristic, not a legal proxy — it is
  documented as such everywhere it is used.
- **Rule 23 "deceptive package"** is, by its own text, an inspector's judgement (with a
  manufacturer-side defence about protective packaging or filling-machine requirements) — this
  registry deliberately keeps it `HUMAN_REVIEW_REQUIRED` rather than trying to encode "package
  looks oversized for its contents" as an automated NON_COMPLIANT trigger, which would be exactly
  the kind of unsupported legal conclusion the brief prohibits.

## 4. What "real rules replace demo rules" means concretely here

`application.yml` now defaults `lmguard.rules.active-version` to `LM-PC-2011-v1` and
`lmguard.rules.sample-file` to `classpath:rules/lm-pc-2011-rules.json` for fresh dev/prod
deployments. `sample-rules.json` (`DEMO-2026.1`) is untouched and still exists on disk — the test
suite pins to it explicitly (`application-test.yml`, hardcoded fixtures in
`InspectionAnalysisServiceTest`/`InspectionControllerTest`), so nothing broke. Both env vars
(`RULES_ACTIVE_VERSION`, and a new `RULES_SAMPLE_FILE`) let an operator choose either ruleset
without a code change.
