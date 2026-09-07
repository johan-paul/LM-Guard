/**
 * LM-GUARD demonstration ruleset.
 *
 * IMPORTANT: These are illustrative demo rules authored for the LM-GUARD
 * prototype. They are modelled on the structure of packaged-commodity
 * declaration requirements but are NOT an official or complete reproduction of
 * any statutory instrument. The UI surfaces this disclaimer wherever rules are
 * shown.
 */

export const RULESET = {
  version: 'LMPC 2026.1',
  status: 'ACTIVE',
  effectiveFrom: '2026-04-01',
  lastUpdated: '2026-08-27',
  publishedBy: 'Rules & Compliance Engine',
  totalRules: 14,
  activeRules: 12,
  demo: true,
};

export const RULE_CATEGORIES = [
  'Mandatory Declaration',
  'Quantity & Measure',
  'Price Declaration',
  'Legibility & Font',
  'Origin & Import',
  'Identity & Address',
];

export const RULES = [
  {
    ruleId: 'LMPC-DECL-001',
    name: 'Consumer Care Declaration',
    category: 'Mandatory Declaration',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'HIGH',
    field: 'consumerCare',
    summary:
      'Every pre-packaged commodity must carry the name, address, telephone number and email of the office to be contacted for consumer complaints.',
    logic: 'REQUIRE consumerCare.name AND consumerCare.address AND (consumerCare.phone OR consumerCare.email)',
    appliesTo: 'All pre-packaged commodities offered for retail sale',
    triggeredCount: 64,
    lastTriggered: '2026-09-04',
  },
  {
    ruleId: 'LMPC-QTY-002',
    name: 'Standard Unit of Net Quantity',
    category: 'Quantity & Measure',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'HIGH',
    field: 'netQuantity',
    summary:
      'Net quantity must be declared in standard units of weight, measure or number. Approximations and non-standard abbreviations are not accepted.',
    logic: 'MATCH netQuantity AGAINST /^\\d+(\\.\\d+)?\\s?(g|kg|ml|l|N)$/ AND REJECT qualifier tokens',
    appliesTo: 'All commodities sold by weight, measure or number',
    triggeredCount: 38,
    lastTriggered: '2026-09-03',
  },
  {
    ruleId: 'LMPC-PRC-003',
    name: 'Retail Sale Price Inclusivity',
    category: 'Price Declaration',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'CRITICAL',
    field: 'mrp',
    summary:
      'Retail sale price must be printed as a single inclusive figure. Altering, over-stickering or obscuring the printed price is a violation.',
    logic: 'REQUIRE mrp.value AND mrp.taxInclusiveStatement AND NOT overlay.detected',
    appliesTo: 'All pre-packaged commodities',
    triggeredCount: 51,
    lastTriggered: '2026-09-04',
  },
  {
    ruleId: 'LMPC-ORG-004',
    name: 'Country of Origin Declaration',
    category: 'Origin & Import',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'HIGH',
    field: 'origin',
    summary:
      'Imported pre-packaged commodities must declare the country of origin on the principal display panel.',
    logic: 'IF product.imported THEN REQUIRE origin.country ON principalDisplayPanel',
    appliesTo: 'Imported and part-imported commodities',
    triggeredCount: 19,
    lastTriggered: '2026-09-02',
  },
  {
    ruleId: 'LMPC-IDN-005',
    name: 'Manufacturer & Packer Identity',
    category: 'Identity & Address',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'MEDIUM',
    field: 'manufacturer',
    summary:
      'The complete name and full postal address of the manufacturer, packer or importer must be legibly declared. A post-box number alone is insufficient.',
    logic: 'REQUIRE manufacturer.name AND manufacturer.street AND manufacturer.city AND manufacturer.pin',
    appliesTo: 'All pre-packaged commodities',
    triggeredCount: 27,
    lastTriggered: '2026-09-01',
  },
  {
    ruleId: 'LMPC-CHR-006',
    name: 'Date of Manufacture or Packing',
    category: 'Mandatory Declaration',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'MEDIUM',
    field: 'mfgDate',
    summary:
      'Month and year of manufacture, packing or import must be declared on the principal display panel.',
    logic: 'REQUIRE mfgDate MATCHING /^(0[1-9]|1[0-2])\\/20\\d{2}$/',
    appliesTo: 'All pre-packaged commodities',
    triggeredCount: 22,
    lastTriggered: '2026-08-30',
  },
  {
    ruleId: 'LMPC-FNT-007',
    name: 'Minimum Font Height & Panel Ratio',
    category: 'Legibility & Font',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'MEDIUM',
    field: 'legibility',
    summary:
      'Declaration characters must meet the prescribed minimum height relative to the area of the principal display panel and must be printed in a contrasting colour.',
    logic: 'COMPUTE glyphHeight_mm FROM ocr.metrics; REQUIRE glyphHeight_mm >= threshold(panelArea)',
    appliesTo: 'All declarations on the principal display panel',
    triggeredCount: 44,
    lastTriggered: '2026-09-04',
  },
  {
    ruleId: 'LMPC-QTY-008',
    name: 'Quantity Deficiency Tolerance',
    category: 'Quantity & Measure',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'CRITICAL',
    field: 'netQuantity',
    summary:
      'Declared net quantity must fall within the permitted maximum error band when verified against measured contents.',
    logic: 'REQUIRE abs(declared - measured) <= tolerance(declared)',
    appliesTo: 'Commodities selected for physical verification',
    triggeredCount: 12,
    lastTriggered: '2026-08-28',
  },
  {
    ruleId: 'LMPC-PRC-009',
    name: 'Physical–Digital Price Consistency',
    category: 'Price Declaration',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'HIGH',
    field: 'mrp',
    summary:
      'The retail sale price declared on the package must match the price declared for the same pack size on e-commerce listings.',
    logic: 'COMPARE package.mrp WITH listing.mrp FOR identical netQuantity; FLAG IF delta != 0',
    appliesTo: 'Commodities also offered through e-commerce platforms',
    triggeredCount: 17,
    lastTriggered: '2026-09-03',
  },
  {
    ruleId: 'LMPC-QTY-010',
    name: 'Pack Size Change Disclosure',
    category: 'Quantity & Measure',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'MEDIUM',
    field: 'netQuantity',
    summary:
      'A reduction in declared net quantity without a corresponding price revision is flagged for inspector review against the product history.',
    logic: 'IF history.netQuantity DECREASED AND history.mrp UNCHANGED THEN FLAG review',
    appliesTo: 'Products with two or more recorded inspections',
    triggeredCount: 9,
    lastTriggered: '2026-09-04',
  },
  {
    ruleId: 'LMPC-DECL-011',
    name: 'Commodity Common Name',
    category: 'Mandatory Declaration',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'LOW',
    field: 'commonName',
    summary: 'The common or generic name of the commodity contained in the package must be declared.',
    logic: 'REQUIRE commonName NOT NULL AND commonName NOT IN brandNames',
    appliesTo: 'All pre-packaged commodities',
    triggeredCount: 6,
    lastTriggered: '2026-08-25',
  },
  {
    ruleId: 'LMPC-FNT-012',
    name: 'Declaration Panel Placement',
    category: 'Legibility & Font',
    status: 'ACTIVE',
    version: '2026.1',
    severity: 'LOW',
    field: 'legibility',
    summary:
      'Mandatory declarations must appear grouped on a single panel and must not be obscured by folds, seams or secondary labelling.',
    logic: 'REQUIRE declarations.panelId UNIQUE AND occlusion.score < 0.2',
    appliesTo: 'All pre-packaged commodities',
    triggeredCount: 14,
    lastTriggered: '2026-08-29',
  },
  {
    ruleId: 'LMPC-ORG-013',
    name: 'Importer Registration Reference',
    category: 'Origin & Import',
    status: 'DRAFT',
    version: '2026.2-draft',
    severity: 'MEDIUM',
    field: 'origin',
    summary:
      'Draft rule under evaluation: imported commodities to carry the importer registration reference alongside the origin declaration.',
    logic: 'IF product.imported THEN REQUIRE importer.registrationRef',
    appliesTo: 'Imported commodities (pending activation)',
    triggeredCount: 0,
    lastTriggered: null,
  },
  {
    ruleId: 'LMPC-DECL-014',
    name: 'Legacy Contact Declaration',
    category: 'Mandatory Declaration',
    status: 'DEPRECATED',
    version: '2025.3',
    severity: 'LOW',
    field: 'consumerCare',
    summary:
      'Superseded by LMPC-DECL-001. Retained for evaluating inspections recorded before the 2026.1 ruleset came into effect.',
    logic: 'REQUIRE consumerCare.phone',
    appliesTo: 'Inspections dated before 2026-04-01',
    triggeredCount: 0,
    lastTriggered: '2026-03-28',
  },
];

export const RULE_BY_ID = RULES.reduce((acc, r) => {
  acc[r.ruleId] = r;
  return acc;
}, {});
