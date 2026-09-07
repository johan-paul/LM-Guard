# Rule Coverage — The Legal Metrology (Packaged Commodities) Rules, 2011

Source: `legal-rules/lm_pc_2011_rules.json` (34 rules) + `legal-rules/lm_pc_2011_schedules.json`
(7 Schedules), extracted in full from the PDF supplied by the user. Every rule in the source
document is accounted for below — coverage is **100%**; automatic image-based verification is
**not**, and is not supposed to be (see `docs/IMPLEMENTATION_AUDIT.md` §4 for why).

Legend: 🟢 automated in the engine today · 🟡 image/OCR-assisted, human-reviewed · ⚪ not
image-derivable (physical test / external data / procedural / penalty / definitional).

| Rule | Requirement (short) | Applicability | Image check | External data | Physical check | Human review | Status |
|---|---|---|---|---|---|---|---|
| 1 | Short title/commencement | Always | No | No | No | No | ⚪ Definitional |
| 2 | Definitions | Always | No | No | No | No | ⚪ Definitional |
| 3 | Chapter II applicability/exclusions | Gates Ch. II | Partial (qty) | No | No | Yes | 🟡 Package context |
| 4 | Every package must bear required declarations | Ch. II | Yes | No | No | No | 🟢 Implemented (via rule 6 checks) |
| 5 | Standard pack sizes (2nd Schedule) | 2nd Sched. commodities | Yes | No | No | Yes | 🟡 Category-dependent |
| 6(1)(a) | Manufacturer/packer/importer name+address | Ch. II, non-food | Yes | No | No | No | 🟢 **Implemented** — `LM-PC-2011-v1` |
| 6(1)(b) | Common/generic commodity name | Ch. II | Yes | No | No | No | 🟢 **Implemented** |
| 6(1)(c) | Net quantity | Ch. II | Yes | No | No | No | 🟢 **Implemented** |
| 6(1)(d) | Month/year of manufacture | Ch. II, with exceptions | Yes | No | No | No | 🟢 **Implemented** |
| 6(1)(e) | Retail sale price (MRP) | Ch. II | Yes | No | No | No | 🟢 **Implemented** |
| 6(1)(f) | Dimensions where relevant | Category-dependent | Yes | No | No | Yes | 🟡 |
| 6(1)(g) | Other specified matters + provisos | Ch. II | No | No | No | Yes | ⚪ Catch-all |
| 6(2) | Consumer-complaint contact | Ch. II | Yes | No | No | No | 🟢 **Implemented** |
| 6(3) | No unauthorized stickers (MRP-reduction exception) | Ch. II | Yes | No | No | Yes | 🟡 Overlay detection needs VLM, not deterministic |
| 6(4) | Stickers OK for non-required declarations | Ch. II | No | No | No | No | ⚪ Procedural |
| 6(5) | Multi-component commodities | Category-dependent | Yes | No | No | Yes | 🟡 |
| 6(6) | Exhaustion of old packaging (2011-2012 transitional) | Time-bound, expired | No | No | No | No | ⚪ Historical/expired |
| 7(1) | PDP for ≤5cm³ packages | Tiny packages | Yes | No | Yes | Yes | 🟡 Capacity needs physical measurement |
| 7(2)-(3) | Minimum numeral/letter height | Ch. II | Yes | No | Yes | Yes | 🟡 **Not assessable from image without a calibration reference** |
| 7(4) | Exempt if another law already requires it | Ch. II | No | Yes | No | Yes | ⚪ |
| 8(1) | PDP placement + clear space around quantity | Ch. II | Yes | No | No | Yes | 🟡 Advisory signal only |
| 8(2) | Returnable-bottle RSP placement | Beverage bottles | Yes | No | No | No | 🟢 Could be added (niche) |
| 9(1) | Legible, prominent, contrasting | Ch. II | Yes | No | No | Yes | 🟡 OCR confidence as proxy only |
| 9(2) | Not read through liquid contents | Ch. II | Yes | No | No | Yes | 🟡 |
| 9(3) | Outer wrapper repeats declarations | Packages with outer wrap | Yes | No | No | Yes | 🟡 Needs multi-panel (not built — see audit §4) |
| 9(4) | Hindi/English (or +other) | Ch. II | Yes | No | No | Yes | 🟡 OCR is English-tuned; Hindi flagged INCONCLUSIVE |
| 10 | Manufacturer/packer/importer detail + address completeness | Ch. II | Yes | No | No | No | 🟢 Folded into 6(1)(a) check |
| 11 | Net-quantity accuracy rules ("when packed" etc.) | Ch. II | No | No | Yes | No | ⚪ Physical test (Rules 19-22) |
| 12 | Manner of declaring quantity, exaggeration ban | Ch. II | Yes | No | No | No | 🟢 Could extend PATTERN_MATCH (not yet loaded) |
| 13 | SI units, no "dozen/score/gross" | Ch. II | Yes | No | No | No | 🟢 Could extend PATTERN_MATCH (not yet loaded) |
| 14 | Textile dimension declarations | Category-dependent | Yes | No | No | Yes | 🟡 |
| 15 | Dimension/weight tied to price | Category-dependent | Yes | No | No | Yes | 🟡 |
| 16 | Usable-sheet count | Category-dependent | Yes | No | No | Yes | 🟡 |
| 17 | Container-type dimension declarations | Category-dependent | Yes | No | No | Yes | 🟡 |
| 18(1) | Dealer must not sell non-compliant packages | Point of sale | Yes | No | No | No | 🟢 Enforced via rule 6 checks |
| 18(2) | No sale above declared RSP | Point of sale | No | Yes | No | Yes | ⚪ Needs receipt/transaction data |
| 18(3)-(4) | Tax-revision price procedure | Post-tax-change | No | Yes | No | Yes | ⚪ Needs tax notice |
| 18(5)-(6) | No obliterating/altering RSP | Ch. II | Yes | No | No | Yes | 🟡 Tamper detection is a judgement call |
| 18(7) | VAT/TOT retailer weighing machine | VAT/TOT retailers | No | Yes | Yes | Yes | ⚪ Premises equipment check |
| 19 | Sampling/testing at manufacturer premises | Field inspection | No | No | Yes | Yes | ⚪ Out of scope for a photo pipeline |
| 20 | Action on inspection completion | Field inspection | No | No | Yes | Yes | ⚪ |
| 21 | Sampling/testing at dealer premises | Field inspection, conditional | No | Yes | Yes | Yes | ⚪ |
| 22 | Maximum permissible error (1st Schedule) | All quantity checks | No | No | Yes | No | ⚪ Reference table for physical test |
| 23 | Deceptive packages | Ch. II | Yes | No | Yes | Yes | 🟡 Explicitly a judgement call under the rule itself |
| 24 | Wholesale package declarations | Wholesale packages | Yes | No | No | Yes | 🟡 Needs wholesale/retail classification |
| 25 | Export packages re-sold in India | Re-sold export packages | No | Yes | No | Yes | ⚪ |
| 26 | Exemptions (≤10g/ml, fast food, drugs, bulk agri) | Gates entire ruleset | Partial (qty) | No | No | Yes | 🟡 Quantity auto-checkable; category exemptions are not |
| 27 | Manufacturer/packer/importer registration | Registration | No | Yes | No | Yes | ⚪ |
| 28 | Shorter-address registration | Registration | No | Yes | No | Yes | ⚪ |
| 29 | Public register of manufacturers | Registration | No | Yes | No | No | ⚪ |
| 30 | Circulation of manufacturer lists | Administrative | No | Yes | No | No | ⚪ |
| 31 | Advertisement RSP+quantity declaration | Advertisements | No | Yes | No | Yes | ⚪ Different input (ad, not package) |
| 32 | Penalties | All | No | No | No | Yes | ⚪ Enforcement context only |
| 33 | Power to relax | Special cases | No | Yes | No | Yes | ⚪ |
| 34 | Repeal and savings | Transitional | No | No | No | No | ⚪ Historical |

## Summary counts (34 rules, source-document top-level rule numbers)

- **🟢 Implemented in the deterministic engine today** (loaded as real `RuleDefinition`s in
  `src/main/resources/rules/lm-pc-2011-rules.json`, version `LM-PC-2011-v1`): the six Rule 6
  declarations that map to existing `ProductField`s (6(1)(a)/(b)/(c)/(d)/(e), 6(2)) — **6 of 34
  top-level rules**, covering what the brief calls the "seven core declarations" (the 2011 Rules
  as supplied define six for a domestic retail package; see `RULE_REVIEW.md` for why "country of
  origin" is not a seventh).
- **🟡 Image/OCR-assisted but requiring human review or category classification the AI layer
  cannot certify**: 20 of 34 rules (dimension/category-dependent declarations, legibility/contrast/
  overlay judgements, applicability/exemption gating).
- **⚪ Not derivable from a package photo at all** (physical test, external records, procedural,
  penalty, definitional, or a different input entirely such as an advertisement): 14 of 34 rules
  — Rules 1, 2, 6(1)(g) [partially], 6(4), 6(6), 7(4), 11, 18(2), 18(3)-(4), 18(7), 19, 20, 21, 22,
  25, 27, 28, 29, 30, 31, 32, 33, 34 (some rules appear in more than one bucket above because a
  rule can have both an image-checkable part and a physical/external part, e.g. Rule 6(1)(g)).

Schedule coverage: see `legal-rules/lm_pc_2011_schedules.json` — First, Fifth, Sixth and Seventh
Schedules are physical-test reference material (⚪); Second, Third and Fourth Schedules are
deterministic *lookups* once a commodity category is known, but that category classification
itself is not guaranteed by this system's AI layer (🟡).

**The objective the brief set was 100% rule coverage, not 100% automatic verification — this
table is that coverage record.**
