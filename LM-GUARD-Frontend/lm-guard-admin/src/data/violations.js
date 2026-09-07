/**
 * Violation case records — IDs follow VIO-2026-0XX.
 *
 * Every violation carries the evidence that supports it: the package artwork it
 * was detected on, plus bounding boxes in the LABEL_CANVAS coordinate space.
 * Nothing in this file asserts a verdict — the rule engine produces a finding,
 * an inspector decides.
 */
import { LABEL_REGIONS } from './assets';
import { INSPECTION_BY_ID } from './inspections';
import { PRODUCT_BY_ID } from './products';
import { RULE_BY_ID } from './rules';

/** Case status vocabulary used across the violations workspace. */
export const VIOLATION_STATUSES = ['OPEN', 'UNDER_REVIEW', 'ESCALATED', 'CONFIRMED', 'DISMISSED'];

export const VIOLATION_TYPES = [
  'Missing mandatory declaration',
  'Physical–digital mismatch',
  'Price declaration irregularity',
  'Quantity deficiency',
  'Legibility below threshold',
  'Origin not declared',
  'Incomplete packer address',
];

const box = (region, label, note) => ({ ...LABEL_REGIONS[region], region, label, note });

const V = (o) => {
  const inspection = INSPECTION_BY_ID[o.inspectionId];
  const product = PRODUCT_BY_ID[inspection?.productId];
  const rule = RULE_BY_ID[o.ruleId];
  return {
    rulesetVersion: '2026.1',
    productId: product?.id,
    productName: product?.name,
    category: product?.category,
    manufacturer: product?.manufacturer,
    inspector: inspection?.inspector,
    inspectorId: inspection?.inspectorId,
    zone: inspection?.zone,
    detectedAt: inspection?.date,
    ruleName: rule?.name,
    ruleCategory: rule?.category,
    ruleSummary: rule?.summary,
    ruleLogic: rule?.logic,
    evidenceImage: inspection?.evidenceImage,
    ...o,
  };
};

export const VIOLATIONS = [
  V({
    id: 'VIO-2026-015',
    inspectionId: 'INS-2026-001',
    title: 'Consumer care declaration missing',
    type: 'Missing mandatory declaration',
    field: 'consumerCare',
    ruleId: 'LMPC-DECL-001',
    confidence: 0.92,
    riskLevel: 'HIGH',
    severity: 'HIGH',
    status: 'OPEN',
    decision: 'Requires human review',
    detectedValue: 'Not detected',
    expectedValue: 'Name, address and telephone or email of the consumer complaints office',
    boxes: [box('consumerCare', 'Consumer care region', 'No contact block found in the expected panel area')],
    notes:
      'OCR resolved every other declaration on this panel at high confidence, so the absence is unlikely to be an extraction failure. Prior inspection INS-2026-013 on the same product was inconclusive due to glare.',
  }),
  V({
    id: 'VIO-2026-014',
    inspectionId: 'INS-2026-001',
    title: 'Package price differs from e-commerce listing',
    type: 'Physical–digital mismatch',
    field: 'mrp',
    ruleId: 'LMPC-PRC-009',
    confidence: 0.88,
    riskLevel: 'HIGH',
    severity: 'HIGH',
    status: 'UNDER_REVIEW',
    decision: 'Requires human review',
    detectedValue: '₹185.00 on package · ₹172.00 listed online',
    expectedValue: 'Identical retail sale price for the same pack size',
    boxes: [box('mrp', 'Printed retail sale price', 'Compared against the aggregated listing captured 2026-09-03')],
    notes:
      'Listing snapshot taken one day before the field inspection. Same pack size (1 L), difference of ₹13.00.',
  }),
  V({
    id: 'VIO-2026-013',
    inspectionId: 'INS-2026-002',
    title: 'Net quantity reduced without price revision',
    type: 'Quantity deficiency',
    field: 'netQuantity',
    ruleId: 'LMPC-QTY-010',
    confidence: 0.95,
    riskLevel: 'HIGH',
    severity: 'HIGH',
    status: 'OPEN',
    decision: 'Requires human review',
    detectedValue: '280 g (previously 320 g at the same ₹62.00)',
    expectedValue: 'Pack size change reflected in the declared retail sale price',
    boxes: [
      box('netQuantity', 'Declared net quantity', 'Reduced by 40 g against the May 2026 record'),
      box('mrp', 'Retail sale price', 'Unchanged at ₹62.00 across both pack sizes'),
    ],
    notes: 'Flagged by comparison against product history for PRD-2026-045. E-commerce listing still shows 320 g.',
  }),
  V({
    id: 'VIO-2026-012',
    inspectionId: 'INS-2026-003',
    title: 'Declaration font height below threshold',
    type: 'Legibility below threshold',
    field: 'legibility',
    ruleId: 'LMPC-FNT-007',
    confidence: 0.61,
    riskLevel: 'MEDIUM',
    severity: 'MEDIUM',
    status: 'UNDER_REVIEW',
    decision: 'Manual verification required',
    detectedValue: 'Estimated 0.9 mm glyph height',
    expectedValue: 'Minimum prescribed height for the measured panel area',
    boxes: [box('consumerCare', 'Consumer care block', 'Character height measured below the computed threshold')],
    notes:
      'Confidence is below the decision threshold — the finding is advisory only and must be confirmed by physical measurement.',
  }),
  V({
    id: 'VIO-2026-011',
    inspectionId: 'INS-2026-004',
    title: 'Country of origin not declared',
    type: 'Origin not declared',
    field: 'origin',
    ruleId: 'LMPC-ORG-004',
    confidence: 0.98,
    riskLevel: 'HIGH',
    severity: 'HIGH',
    status: 'ESCALATED',
    decision: 'Escalated to zonal controller',
    detectedValue: 'Not detected',
    expectedValue: 'Country of origin declared on the principal display panel',
    boxes: [box('origin', 'Origin declaration region', 'Expected origin block absent on an imported commodity')],
    notes: 'Imported commodity. Importer details are present; the origin declaration itself is absent.',
  }),
  V({
    id: 'VIO-2026-010',
    inspectionId: 'INS-2026-005',
    title: 'Date of packing not legible',
    type: 'Legibility below threshold',
    field: 'mfgDate',
    ruleId: 'LMPC-CHR-006',
    confidence: 0.52,
    riskLevel: 'MEDIUM',
    severity: 'MEDIUM',
    status: 'OPEN',
    decision: 'Requires re-scan',
    detectedValue: 'Illegible — printed across the bottle seam',
    expectedValue: 'Month and year of packing, legible on the display panel',
    boxes: [box('mfgDate', 'Packing date region', 'Ink-jet coding falls across a moulding seam')],
    notes: 'A second capture at a different angle is likely to resolve this without enforcement action.',
  }),
  V({
    id: 'VIO-2026-009',
    inspectionId: 'INS-2026-006',
    title: 'Packer address incomplete',
    type: 'Incomplete packer address',
    field: 'manufacturer',
    ruleId: 'LMPC-IDN-005',
    confidence: 0.69,
    riskLevel: 'MEDIUM',
    severity: 'MEDIUM',
    status: 'UNDER_REVIEW',
    decision: 'Requires human review',
    detectedValue: 'Unit 7, Ambattur Industrial Estate — PIN code absent',
    expectedValue: 'Complete postal address including PIN code',
    boxes: [box('manufacturer', 'Manufacturer / packer block', 'Address terminates without a PIN code')],
    notes: 'Same manufacturer declared a complete address on PRD-2026-113 in the same week.',
  }),
  V({
    id: 'VIO-2026-008',
    inspectionId: 'INS-2026-007',
    title: 'Retail price obscured by secondary sticker',
    type: 'Price declaration irregularity',
    field: 'mrp',
    ruleId: 'LMPC-PRC-003',
    confidence: 0.95,
    riskLevel: 'HIGH',
    severity: 'CRITICAL',
    status: 'ESCALATED',
    decision: 'Escalated to zonal controller',
    detectedValue: '₹149.00 sticker applied over printed ₹680.00',
    expectedValue: 'Single unobscured printed retail sale price',
    boxes: [box('mrp', 'Retail sale price region', 'Adhesive label detected overlaying the printed price')],
    notes: 'Overlay detected by edge discontinuity and colour differential across the price block.',
  }),
  V({
    id: 'VIO-2026-007',
    inspectionId: 'INS-2026-011',
    title: 'Net quantity below declared tolerance',
    type: 'Quantity deficiency',
    field: 'netQuantity',
    ruleId: 'LMPC-QTY-008',
    confidence: 0.99,
    riskLevel: 'HIGH',
    severity: 'CRITICAL',
    status: 'CONFIRMED',
    decision: 'Confirmed by inspector',
    detectedValue: '9.82 kg measured against 10 kg declared',
    expectedValue: 'Measured quantity within the permitted error band',
    boxes: [box('netQuantity', 'Declared net quantity', 'Physical verification recorded a 180 g deficiency')],
    notes: 'Verified on calibrated field scale, reading witnessed and recorded at the premises.',
  }),
];

export const VIOLATION_BY_ID = VIOLATIONS.reduce((acc, v) => {
  acc[v.id] = v;
  return acc;
}, {});

export const violationsForInspection = (inspectionId) =>
  VIOLATIONS.filter((v) => v.inspectionId === inspectionId);

export const violationsForProduct = (productId) => VIOLATIONS.filter((v) => v.productId === productId);
