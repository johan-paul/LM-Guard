/**
 * Risk intelligence layer — programme-level counters, the ranked priority queue
 * and repeat-offender aggregates.
 *
 * Counters describe the whole inspection programme (1,248 records to date), not
 * only the 18 detailed records held in inspections.js.
 */
import { PRODUCTS, MANUFACTURERS } from './products';

export const PROGRAMME_TOTALS = {
  totalInspections: 1248,
  totalInspectionsDelta: 12.5,
  potentialViolations: 237,
  potentialViolationsDelta: 8.2,
  highRiskProducts: 84,
  repeatOffenders: 31,
  complianceRate: 81.0,
  complianceRateDelta: -1.4,
  avgResolutionDays: 4.2,
  pendingReview: 46,
  escalated: 12,
  reviewedThisWeek: 118,
};

export const RISK_DISTRIBUTION = [
  { level: 'HIGH', label: 'High risk', count: 84, tone: 'critical' },
  { level: 'MEDIUM', label: 'Medium risk', count: 126, tone: 'warning' },
  { level: 'LOW', label: 'Low risk', count: 538, tone: 'success' },
];

export const RISK_THRESHOLDS = { high: 70, medium: 40 };

export const riskLevelFor = (score) => {
  if (score >= RISK_THRESHOLDS.high) return 'HIGH';
  if (score >= RISK_THRESHOLDS.medium) return 'MEDIUM';
  return 'LOW';
};

/** Products ranked by composite risk score — the inspector's work queue. */
export const RISK_QUEUE = [...PRODUCTS]
  .sort((a, b) => b.riskScore - a.riskScore)
  .map((p, i) => ({
    rank: i + 1,
    productId: p.id,
    product: p.name,
    shortName: p.shortName,
    category: p.category,
    manufacturer: p.manufacturer,
    riskScore: p.riskScore,
    riskLevel: p.riskLevel,
    riskFactors: p.riskFactors,
    previousViolations: p.violationCount,
    openViolations: p.openViolations,
    lastInspection: p.lastInspection,
    primaryIssue: p.primaryIssue,
    image: p.image,
  }));

/** Manufacturers with more than one substantiated violation on record. */
export const REPEAT_OFFENDERS = MANUFACTURERS.filter((m) => m.openViolations >= 3)
  .map((m) => {
    const products = PRODUCTS.filter((p) => p.manufacturerId === m.id);
    const avgRisk = products.length
      ? Math.round(products.reduce((s, p) => s + p.riskScore, 0) / products.length)
      : 0;
    return {
      ...m,
      trackedProducts: products.length,
      avgRisk,
      totalViolations: products.reduce((s, p) => s + p.violationCount, 0),
      topProduct: products.sort((a, b) => b.riskScore - a.riskScore)[0]?.name || '—',
    };
  })
  .sort((a, b) => b.totalViolations - a.totalViolations);

/** Weighted contributors behind the composite risk score. */
export const RISK_FACTOR_WEIGHTS = [
  { factor: 'Repeated declaration violations', weight: 30, occurrences: 46 },
  { factor: 'Physical–digital mismatch', weight: 25, occurrences: 17 },
  { factor: 'Recent package changes', weight: 18, occurrences: 23 },
  { factor: 'Low confidence declarations', weight: 15, occurrences: 39 },
  { factor: 'Quantity deficiency history', weight: 12, occurrences: 12 },
];
