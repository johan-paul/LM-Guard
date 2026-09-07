/**
 * Analytics datasets. Series are generated from a fixed seed so the charts are
 * stable across renders and reloads — no random jitter between navigations.
 */

/* Deterministic pseudo-random in [0,1) */
function seeded(seed) {
  let s = seed % 2147483647;
  if (s <= 0) s += 2147483646;
  return () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

const DAY = 86400000;
const END = new Date('2026-09-04T00:00:00+05:30').getTime();

/**
 * Builds a daily series with a gentle upward drift, a mild weekend dip and a
 * small amount of noise — smoothed so the chart reads as a trend rather than a
 * seismograph.
 */
function buildSeries(days, seed, base, violationRate) {
  const rand = seeded(seed);
  const raw = [];

  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(END - i * DAY);
    const weekday = d.getDay();
    const weekendDrop = weekday === 0 ? 0.62 : weekday === 6 ? 0.82 : 1;
    const drift = 1 + ((days - 1 - i) / days) * 0.18; // slow growth in programme volume
    const noise = 1 + (rand() - 0.5) * 0.16;
    raw.push({ date: d, value: base * weekendDrop * drift * noise });
  }

  // 3-point moving average keeps the curve calm without flattening the shape.
  const smoothed = raw.map((row, i) => {
    const window = [raw[i - 1]?.value, row.value, raw[i + 1]?.value].filter((v) => v !== undefined);
    return { ...row, value: window.reduce((s, v) => s + v, 0) / window.length };
  });

  return smoothed.map((row, i) => {
    const inspections = Math.max(3, Math.round(row.value));
    const rateNoise = (rand() - 0.5) * 0.05;
    const violations = Math.max(0, Math.round(inspections * (violationRate + rateNoise)));
    const complianceRate = Math.round(((inspections - violations) / inspections) * 1000) / 10;
    return {
      date: row.date.toISOString().slice(0, 10),
      label: row.date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
      inspections,
      violations,
      complianceRate,
      index: i,
    };
  });
}

export const TREND_SERIES = {
  '7d': buildSeries(7, 4211, 22, 0.19),
  '30d': buildSeries(30, 9137, 20, 0.2),
  '90d': buildSeries(90, 5521, 18, 0.21),
};

export const TREND_RANGES = [
  { key: '7d', label: 'Last 7 days' },
  { key: '30d', label: 'Last 30 days' },
  { key: '90d', label: 'Last 90 days' },
];

/** Aggregate summary for a trend range — drives the deltas under the chart. */
export function summariseTrend(key) {
  const rows = TREND_SERIES[key] || TREND_SERIES['30d'];
  const inspections = rows.reduce((s, r) => s + r.inspections, 0);
  const violations = rows.reduce((s, r) => s + r.violations, 0);
  const complianceRate = Math.round(((inspections - violations) / inspections) * 1000) / 10;
  const half = Math.floor(rows.length / 2);
  const firstHalf = rows.slice(0, half).reduce((s, r) => s + r.inspections, 0) || 1;
  const secondHalf = rows.slice(half).reduce((s, r) => s + r.inspections, 0);
  const momentum = Math.round(((secondHalf - firstHalf) / firstHalf) * 1000) / 10;
  return { inspections, violations, complianceRate, momentum };
}

export const VIOLATION_CATEGORIES = [
  { category: 'Consumer care', count: 64, share: 27 },
  { category: 'Price declaration', count: 51, share: 21.5 },
  { category: 'Legibility & font', count: 44, share: 18.6 },
  { category: 'Net quantity', count: 38, share: 16 },
  { category: 'Packer identity', count: 21, share: 8.9 },
  { category: 'Country of origin', count: 19, share: 8 },
];

export const REGIONAL_INSIGHTS = [
  { zone: 'Coimbatore North', inspections: 312, violations: 71, complianceRate: 77.2, inspectors: 4 },
  { zone: 'Coimbatore South', inspections: 268, violations: 48, complianceRate: 82.1, inspectors: 3 },
  { zone: 'Coimbatore West', inspections: 194, violations: 33, complianceRate: 83.0, inspectors: 3 },
  { zone: 'Tiruppur', inspections: 246, violations: 52, complianceRate: 78.9, inspectors: 4 },
  { zone: 'Erode', inspections: 228, violations: 33, complianceRate: 85.5, inspectors: 2 },
];

export const CATEGORY_PERFORMANCE = [
  { category: 'Edible Oils', inspections: 214, violations: 58 },
  { category: 'Staples & Grains', inspections: 268, violations: 61 },
  { category: 'Bakery & Biscuits', inspections: 186, violations: 34 },
  { category: 'Personal Care', inspections: 172, violations: 27 },
  { category: 'Packaged Water', inspections: 158, violations: 22 },
  { category: 'Confectionery', inspections: 124, violations: 21 },
  { category: 'Spices & Masala', inspections: 126, violations: 14 },
];

export const RESOLUTION_FUNNEL = [
  { stage: 'Detected', count: 237 },
  { stage: 'Under review', count: 118 },
  { stage: 'Escalated', count: 62 },
  { stage: 'Enforcement action', count: 34 },
];

export const CONFIDENCE_BANDS = [
  { band: '90–100%', count: 128, decision: 'Auto-flagged for review' },
  { band: '70–89%', count: 71, decision: 'Flagged for review' },
  { band: '50–69%', count: 29, decision: 'Advisory only' },
  { band: 'Below 50%', count: 9, decision: 'Re-scan requested' },
];

export const OFFENDER_TREND = [
  { month: 'Apr', offenders: 18, newOffenders: 4 },
  { month: 'May', offenders: 21, newOffenders: 5 },
  { month: 'Jun', offenders: 24, newOffenders: 4 },
  { month: 'Jul', offenders: 27, newOffenders: 6 },
  { month: 'Aug', offenders: 29, newOffenders: 3 },
  { month: 'Sep', offenders: 31, newOffenders: 2 },
];
