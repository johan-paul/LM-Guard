/**
 * Platform data service.
 *
 * Backed by the in-memory mock layer in `src/data`, behind the same async
 * contract the REST backend exposes. Swap the bodies for `api.js` calls when the
 * Spring Boot service is wired in — page components never talk to data directly.
 */
import { INSPECTIONS, INSPECTION_BY_ID } from '../data/inspections';
import { VIOLATIONS, VIOLATION_BY_ID, violationsForInspection } from '../data/violations';
import { PRODUCT_BY_ID } from '../data/products';
import { RULE_BY_ID } from '../data/rules';
import { riskLevelFor } from '../data/riskData';
import api from './api';

const clone = (v) => JSON.parse(JSON.stringify(v));
const delay = (ms) => new Promise((r) => setTimeout(r, ms));

/* ------------------------------------------------------------------ */
/* Mutable in-memory store                                             */
/* ------------------------------------------------------------------ */
const store = {
  inspections: clone(INSPECTIONS),
  violations: clone(VIOLATIONS),
};

const matches = (haystack, q) => String(haystack || '').toLowerCase().includes(q);

/* ------------------------------------------------------------------ */
/* Real-backend response adapters                                      */
/*                                                                      */
/* The backend's inspection lifecycle (PENDING/IN_PROGRESS/PROCESSING   */
/* before a verdict, then COMPLIANT/NON_COMPLIANT/INCONCLUSIVE/FAILED)   */
/* is richer than this console's original four-value mock vocabulary.   */
/* Every non-final state maps to REVIEW_REQUIRED so the existing        */
/* StatusBadge/summary-count UI keeps working unmodified; backendStatus */
/* carries the real value through for anything that needs it.           */
/* ------------------------------------------------------------------ */
function mapVerdictStatus(status) {
  if (status === 'COMPLIANT' || status === 'NON_COMPLIANT' || status === 'INCONCLUSIVE') return status;
  return 'REVIEW_REQUIRED';
}

function mapInspectionSummary(dto) {
  return {
    id: dto.inspectionId,
    productId: dto.productId,
    productName: dto.productName || 'Product not yet identified',
    manufacturer: dto.brand || '—',
    category: dto.category || '—',
    inspector: dto.inspectorName || 'Unassigned',
    inspectorId: dto.inspectorId,
    zone: dto.zoneName || '—',
    establishment: dto.establishment,
    status: mapVerdictStatus(dto.status),
    backendStatus: dto.status,
    riskScore: dto.riskScore ?? 0,
    date: dto.createdAt,
  };
}

// Backend severities (MINOR/MAJOR/CRITICAL) don't line up one-to-one with the
// console's four-tone badge vocabulary - MEDIUM simply never appears for a
// rule sourced from the real ruleset, which is honest rather than fabricated.
function mapRuleSeverity(severity) {
  if (severity === 'CRITICAL') return 'CRITICAL';
  if (severity === 'MAJOR') return 'HIGH';
  return 'LOW';
}

function mapRuleResponse(dto) {
  return {
    ruleId: dto.ruleCode,
    name: dto.ruleName,
    category: dto.fieldName,
    status: dto.active ? 'ACTIVE' : 'DEPRECATED',
    version: dto.version,
    severity: mapRuleSeverity(dto.severity),
    field: dto.fieldName,
    summary: dto.description || 'No description recorded for this rule.',
    appliesTo: `Declarations for ${dto.fieldName}`,
    logic: dto.ruleDefinition || `type=${dto.ruleType}`,
    // Per-rule trigger history isn't tracked anywhere in the backend yet -
    // left unset rather than approximated from unrelated aggregate counts.
    triggeredCount: 0,
    lastTriggered: null,
  };
}

const ANALYTICS_TREND_RANGES = [
  { key: '7d', label: 'Last 7 days' },
  { key: '30d', label: 'Last 30 days' },
  { key: '90d', label: 'Last 90 days' },
];

const CONFIDENCE_BAND_DECISIONS = [
  'Auto-flagged for review',
  'Flagged for review',
  'Advisory only',
  'Re-scan requested',
];

function formatFieldCategory(fieldName) {
  const words = String(fieldName || '').toLowerCase().split('_');
  return words.map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w)).join(' ');
}

function trendSlice(dailyTrend, days) {
  return dailyTrend.slice(-days).map((row, i) => ({
    date: row.date,
    label: row.label,
    inspections: row.inspections,
    violations: row.violations,
    complianceRate:
      row.inspections === 0 ? 0 : Math.round(((row.inspections - row.violations) / row.inspections) * 1000) / 10,
    index: i,
  }));
}

function mapAnalyticsResponse(dto) {
  const dailyTrend = dto.dailyTrend || [];
  const totalFieldBreaches = (dto.violationCategories || []).reduce((s, c) => s + c.count, 0) || 1;

  return {
    trends: {
      '7d': trendSlice(dailyTrend, 7),
      '30d': trendSlice(dailyTrend, 30),
      '90d': trendSlice(dailyTrend, 90),
    },
    ranges: ANALYTICS_TREND_RANGES,
    violationCategories: (dto.violationCategories || []).map((c) => ({
      category: formatFieldCategory(c.fieldName),
      count: c.count,
      share: Math.round((c.count / totalFieldBreaches) * 1000) / 10,
    })),
    regional: dto.regional || [],
    confidenceBands: (dto.confidenceBands || []).map((b, i) => ({
      band: b.band,
      count: b.count,
      decision: CONFIDENCE_BAND_DECISIONS[i] || 'Advisory only',
    })),
    offenderTrend: dto.offenderTrend || [],
    riskDistribution: [
      { level: 'HIGH', label: 'High risk', count: dto.highRiskCount ?? 0, tone: 'critical' },
      { level: 'MEDIUM', label: 'Medium risk', count: dto.mediumRiskCount ?? 0, tone: 'warning' },
      { level: 'LOW', label: 'Low risk', count: dto.lowRiskCount ?? 0, tone: 'success' },
    ],
    totals: {
      avgResolutionDays: dto.avgResolutionDays ?? 0,
    },
  };
}

const VIOLATION_DECISION_TO_CASE_STATUS = {
  COMPLIANT: 'DISMISSED',
  RESCAN: 'UNDER_REVIEW',
  ESCALATE: 'ESCALATED',
  CONFIRM: 'CONFIRMED',
};

function mapViolationSummary(dto) {
  return {
    id: dto.id,
    inspectionId: dto.inspectionId,
    productId: dto.productId,
    productName: dto.productName || 'Product not yet identified',
    manufacturer: dto.manufacturer || '—',
    title: dto.finding,
    type: formatFieldCategory(dto.fieldName),
    ruleId: dto.ruleCode,
    ruleCategory: formatFieldCategory(dto.fieldName),
    confidence: dto.confidence,
    riskLevel: dto.riskLevel || 'LOW',
    status: dto.caseStatus,
    detectedAt: dto.detectedAt,
    // Null for an absence finding (no region to point to) - real for a present-but-flawed
    // value, e.g. MRP printed but unreadable. See ViolationCaseMapper.toSummary on the backend.
    evidenceImageUrl: dto.evidence?.imageUrl || null,
    boundingBox: dto.evidence
      ? { x: dto.evidence.x, y: dto.evidence.y, width: dto.evidence.width, height: dto.evidence.height }
      : null,
  };
}

function mapViolationDetail(dto) {
  const timeline = [{ label: 'Violation detected', at: dto.detectedAt, state: 'done' }];
  if (dto.decidedAt) {
    timeline.push({
      label: `Case marked ${String(dto.caseStatus || '').toLowerCase().replace('_', ' ')}${dto.decidedBy ? ` by ${dto.decidedBy}` : ''}`,
      at: dto.decidedAt,
      state: 'done',
    });
  } else {
    timeline.push({ label: 'Awaiting inspector review', at: null, state: 'pending' });
  }

  const evidence = dto.evidence || [];
  const primary = evidence[0];

  return {
    id: dto.id,
    inspectionId: dto.inspectionId,
    productId: dto.productId,
    productName: dto.productName || 'Product not yet identified',
    manufacturer: dto.manufacturer || '—',
    title: dto.finding,
    type: formatFieldCategory(dto.fieldName),
    riskLevel: dto.riskLevel || 'LOW',
    status: dto.caseStatus,
    confidence: dto.confidence,
    ruleId: dto.ruleCode,
    ruleName: dto.ruleName || formatFieldCategory(dto.fieldName),
    ruleCategory: formatFieldCategory(dto.fieldName),
    ruleSummary: dto.ruleDescription || 'This rule is no longer on record under this code.',
    ruleLogic: dto.ruleDefinition || 'Evaluation logic is no longer on record for this rule.',
    rulesetVersion: dto.rulesetVersion || '—',
    detectedAt: dto.detectedAt,
    inspector: dto.inspector || 'Unassigned',
    zone: dto.zone || '—',
    detectedValue: dto.observedValue || 'Not detected',
    expectedValue: dto.remediation,
    decision: dto.decisionNote || (dto.caseStatus === 'OPEN' ? 'Awaiting inspector review' : dto.caseStatus),
    evidenceImage: primary?.imageUrl,
    boxes: evidence.map((e) => ({
      x: e.x,
      y: e.y,
      width: e.width,
      height: e.height,
      label: dto.fieldName,
      note: e.description,
    })),
    notes: primary?.description,
    relatedCases: (dto.relatedCases || []).map((c) => ({
      id: c.id,
      title: c.finding,
      status: c.caseStatus,
      detectedAt: c.detectedAt,
    })),
    inspection: { timeline },
  };
}

const OPEN_CASE_STATUSES = new Set(['OPEN', 'UNDER_REVIEW', 'ESCALATED']);

function mapProductSummary(dto) {
  return {
    id: dto.id,
    name: dto.productName,
    shortName: dto.productName,
    category: dto.category || '—',
    manufacturer: dto.brand || '—',
    barcode: dto.barcode,
    riskScore: dto.riskScore ?? 0,
    riskLevel: dto.riskLevel || 'LOW',
    violationCount: dto.violationCount ?? 0,
    openViolations: dto.openViolationCount ?? 0,
    lastInspection: dto.lastInspectionAt,
    // The most recently captured package photo for this product (backend now looks this up
    // from that product's most recent inspection, regardless of whether it was submitted) -
    // previously this was hardcoded null, so the registry list never showed a real thumbnail.
    image: dto.imageUrl || null,
  };
}

function mapProductDetail(productDto, historyDto, summaryDto, riskDto, violationDtos, listingDto) {
  const versions = historyDto.versions || [];
  const latestVersion = versions[0];
  const recentInspections = (summaryDto.recentInspections || []).map(mapInspectionSummary);
  const violations = (violationDtos || []).map(mapViolationSummary);

  const totalInspections = summaryDto.previousInspections ?? 0;
  const complianceRate =
    totalInspections === 0 ? 0 : Math.round((summaryDto.compliantCount / totalInspections) * 1000) / 10;

  const packageChanges = versions.map((v) => ({
    date: v.capturedAt,
    mrp: v.mrp || '—',
    netQuantity: v.netQuantity || '—',
    // A declared-value snapshot carries no compliance verdict of its own - every
    // real entry is honestly a "CHANGE", not an invented pass/fail classification.
    flag: 'CHANGE',
    note: v.source === 'INSPECTION' ? 'Recorded during a field inspection' : `Recorded (${v.source})`,
  }));

  const digitalListing = listingDto
    ? {
        platform: listingDto.source,
        checkedAt: listingDto.capturedAt,
        packageMrp: latestVersion?.mrp || '—',
        packageQuantity: latestVersion?.netQuantity || '—',
        listedMrp: listingDto.mrp || '—',
        listedQuantity: listingDto.quantity || '—',
        mismatch:
          (latestVersion?.mrp || null) !== (listingDto.mrp || null) ||
          (latestVersion?.netQuantity || null) !== (listingDto.quantity || null),
      }
    : null;

  return {
    id: productDto.id,
    name: productDto.productName,
    category: productDto.category || '—',
    manufacturer: productDto.brand || '—',
    barcode: productDto.barcode || '—',
    netQuantity: latestVersion?.netQuantity || '—',
    mrp: latestVersion?.mrp || '—',
    firstSeen: productDto.createdAt,
    lastInspection: recentInspections[0]?.date || null,
    riskScore: riskDto?.riskScore ?? 0,
    riskLevel: riskDto?.riskLevel || 'LOW',
    riskFactors: riskDto?.factors || [],
    primaryIssue: violations[0]?.title || '—',
    totalInspections,
    violationCount: summaryDto.previousViolations ?? 0,
    openViolations: violations.filter((v) => OPEN_CASE_STATUSES.has(v.status)).length,
    complianceRate,
    inspections: recentInspections,
    violations,
    packageChanges,
    digitalListing,
    // The product's own most recent package photo (hero image), plus every evidence region the
    // rule engine drew a box around, still pointing at that same image - a gallery of what was
    // actually flagged, not just a single generic picture. Both were hardcoded null before.
    image: productDto.imageUrl || null,
    evidenceImages: violations
      .filter((v) => v.evidenceImageUrl)
      .map((v) => ({
        url: v.evidenceImageUrl,
        ruleCode: v.ruleId,
        title: v.title,
        boundingBox: v.boundingBox,
      })),
  };
}

function mapInspectionDetail(dto) {
  const timeline = [
    { label: 'Inspection opened', at: dto.createdAt, state: 'done', detail: dto.establishment || undefined },
  ];
  if (dto.analyzedAt) {
    timeline.push({
      label: 'AI analysis completed (advisory)',
      at: dto.analyzedAt,
      state: 'done',
      detail: dto.aiSuggestedStatus ? `Suggested: ${dto.aiSuggestedStatus}` : undefined,
    });
  }
  if (dto.completedAt) {
    timeline.push({ label: 'Inspector submitted final decision', at: dto.completedAt, state: 'done' });
  } else {
    timeline.push({ label: 'Awaiting inspector submission', at: null, state: 'pending' });
  }

  return {
    id: dto.inspectionId,
    productId: dto.product?.id ?? null,
    productName: dto.product?.productName || 'Product not yet identified',
    manufacturer: dto.product?.brand || '—',
    category: dto.product?.category || '—',
    zone: dto.zoneName || '—',
    zoneId: dto.zoneId ?? null,
    establishment: dto.establishment,
    address: dto.address,
    inspectionType: dto.inspectionType,
    priority: dto.priority,
    dueDate: dto.dueDate,
    date: dto.createdAt,
    status: mapVerdictStatus(dto.status),
    backendStatus: dto.status,
    aiSuggestedStatus: dto.aiSuggestedStatus,
    riskScore: dto.riskScore ?? 0,
    inspector: dto.inspectorName || 'Unassigned',
    inspectorId: dto.inspectorId ?? null,
    rulesetVersion: dto.rulesetVersion,
    processingMs: null,
    evidenceImage: dto.imageUrl,
    capturedImage: dto.imageUrl,
    declarations: (dto.fields || []).map((f) => ({
      field: f.name,
      label: f.name,
      detected: f.value,
      status: f.status,
      confidence: f.confidence,
      ruleId: null,
    })),
    violations: (dto.violations || []).map((v) => ({
      id: v.id,
      title: v.finding,
      type: v.fieldName,
      ruleId: v.ruleCode,
      status: v.status,
      severity: v.severity,
      confidence: v.decisionConfidence,
      detectedValue: v.observedValue,
      expectedValue: v.remediation,
      detectedAt: dto.analyzedAt,
      evidenceImage: v.evidence?.imageUrl || dto.imageUrl,
      boxes: (v.allEvidence || []).map((e) => ({
        x: e.x,
        y: e.y,
        width: e.width,
        height: e.height,
        label: v.fieldName,
        note: e.description,
      })),
    })),
    timeline,
  };
}

/* ------------------------------------------------------------------ */
/* Service                                                             */
/* ------------------------------------------------------------------ */
export const inspectionService = {
  /* ---------- Dashboard ---------- */
  // Real backend: GET /api/dashboard/statistics. Deltas/repeat-offender counts
  // have no real historical baseline server-side yet, so they read 0 rather
  // than a fabricated trend - see the final integration report.
  async getStats() {
    const { data } = await api.get('/dashboard/statistics');
    const s = data.data;
    return {
      totalInspections: s.totalInspections,
      totalInspectionsDelta: s.inspectionsLast7Days,
      potentialViolations: s.nonCompliantCount + s.inconclusiveCount,
      potentialViolationsDelta: 0,
      highRiskProducts: s.highRiskCount,
      repeatOffenders: s.repeatOffenderCount,
      complianceRate: 100 - s.nonComplianceRate,
      complianceRateDelta: 0,
      avgResolutionDays: s.avgResolutionDays,
      pendingReview: s.pendingCount,
      escalated: s.escalatedCaseCount,
      reviewedThisWeek: s.inspectionsLast7Days,
      openViolations: s.nonCompliantCount + s.inconclusiveCount,
      riskDistribution: [
        { level: 'HIGH', label: 'High risk', count: s.highRiskCount, tone: 'critical' },
        { level: 'MEDIUM', label: 'Medium risk', count: s.mediumRiskCount, tone: 'warning' },
        { level: 'LOW', label: 'Low risk', count: s.lowRiskCount, tone: 'success' },
      ],
      recordsOnFile: s.totalInspections,
    };
  },

  async getRiskQueue(limit) {
    const { data } = await api.get('/dashboard/high-risk', { params: { size: limit || 100 } });
    const rows = data.data.items || [];
    return rows.map((r, i) => ({
      rank: i + 1,
      productId: r.productId,
      product: r.productName,
      shortName: r.productName,
      category: r.category,
      manufacturer: r.brand,
      riskScore: r.riskScore,
      riskLevel: r.riskLevel,
      // The composite score's per-factor breakdown lives on the inspection that produced it,
      // not on this summary row - the plain-language explanation is the honest substitute for
      // the mock's invented multi-tag list.
      riskFactors: r.explanation ? [r.explanation] : [],
      previousViolations: r.previousViolations,
      lastInspection: r.assessedAt,
      // Where the scoring inspection was carried out - lets the admin console pre-fill a
      // follow-up inspection for this exact product without a separate lookup.
      establishment: r.establishment,
      address: r.address,
    }));
  },

  async getRepeatOffenders() {
    const { data } = await api.get('/dashboard/repeat-offenders');
    return (data.data || []).map((o, i) => ({
      id: `${o.manufacturer}-${i}`,
      name: o.manufacturer,
      zone: o.zone,
      trackedProducts: o.trackedProducts,
      totalViolations: o.totalViolations,
    }));
  },

  async getRiskFactors() {
    const { data } = await api.get('/dashboard/risk-factors');
    return data.data || [];
  },

  /* ---------- Inspections ---------- */
  // Real backend: GET /api/inspections. The endpoint filters server-side by a
  // single status/productId/inspectorId/zoneId; this page's richer filter set
  // (inspector name, risk band, free text) is applied client-side against the
  // mapped rows, same as the mock version did against its in-memory array.
  async getInspections(filters = {}) {
    const { status = 'ALL', search = '', category = 'ALL', inspector = 'ALL', risk = 'ALL', sort = 'recent' } = filters;
    const q = search.trim().toLowerCase();

    // `sort=risk` asks the backend itself to order by risk score (see GET /api/inspections'
    // `sort` param) so a genuinely risk-prioritised queue exists for open/pending cases too, not
    // just the retroactive "already scored high" view on the Risk Intelligence page. The other
    // filters here still apply client-side against that same ordered set.
    const { data } = await api.get('/inspections', { params: { size: 100, sort: sort === 'risk' ? 'risk' : undefined } });
    let rows = data.data.items.map(mapInspectionSummary);

    if (status !== 'ALL') rows = rows.filter((i) => i.status === status);
    if (category !== 'ALL') rows = rows.filter((i) => i.category === category);
    if (inspector !== 'ALL') rows = rows.filter((i) => i.inspector === inspector);
    if (risk !== 'ALL') rows = rows.filter((i) => riskLevelFor(i.riskScore) === risk);
    if (q) {
      rows = rows.filter(
        (i) => matches(i.id, q) || matches(i.productName, q) || matches(i.manufacturer, q) || matches(i.inspector, q),
      );
    }
    // The backend already returned risk-sorted rows when asked; re-sorting client-side by date
    // would undo that, so only impose the date ordering for the default case.
    return sort === 'risk' ? rows : rows.sort((a, b) => new Date(b.date) - new Date(a.date));
  },

  async getInspectionById(id) {
    const { data } = await api.get(`/inspections/${encodeURIComponent(id)}`);
    return mapInspectionDetail(data.data);
  },

  /* ---------- Zones (reference data for New Inspection) ---------- */
  async getZones() {
    const { data } = await api.get('/zones');
    return data.data;
  },

  /**
   * Admin workflow: create a case (establishment/address/type/priority/due
   * date/notes) and assign it to an inspector within a zone. No product,
   * image or AI step here - the assigned inspector identifies the product
   * and runs the six-step workflow themselves.
   */
  async createAssignedInspection(payload) {
    const { data } = await api.post('/inspections', payload);
    return mapInspectionDetail(data.data);
  },

  /**
   * Admin-only: move an inspection to a different inspector and/or zone.
   * Recorded in the backend's assignment audit trail.
   */
  async reassignInspection(id, { inspectorId, zoneId, reason }) {
    const { data } = await api.patch(`/inspections/${encodeURIComponent(id)}/assignment`, {
      inspectorId,
      zoneId: zoneId || undefined,
      reason: reason?.trim() || undefined,
    });
    return mapInspectionDetail(data.data);
  },

  async getAssignmentHistory(id) {
    const { data } = await api.get(`/inspections/${encodeURIComponent(id)}/assignment-history`);
    return data.data;
  },

  /* ---------- Violations ---------- */
  // Real backend: GET /api/violations. Status/risk/date-range filter server-side;
  // free-text search and the "type" filter (there is no separate violation-type
  // taxonomy - the declaration field a rule applies to is the closest real
  // equivalent) are applied client-side against the returned page, the same
  // convention getInspections already uses for its richer filter set.
  async getViolations(filters = {}) {
    const { status = 'ALL', type = 'ALL', risk = 'ALL', search = '', since = 'ALL' } = filters;
    const params = { size: 200 };
    if (status !== 'ALL') params.caseStatus = status;
    if (risk !== 'ALL') params.riskLevel = risk;
    if (since !== 'ALL') params.sinceDays = Number(since);

    const { data } = await api.get('/violations', { params });
    let rows = (data.data.items || []).map(mapViolationSummary);

    if (type !== 'ALL') rows = rows.filter((v) => v.type === type);
    const q = search.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (v) =>
          matches(v.id, q) ||
          matches(v.productName, q) ||
          matches(v.title, q) ||
          matches(v.ruleId, q) ||
          matches(v.manufacturer, q),
      );
    }
    return rows;
  },

  /** The full, unfiltered universe of "violation type" values - kept separate from
   * getViolations() so the type filter's own option list doesn't shrink to just
   * whatever is currently selected. */
  async getViolationTypes() {
    const { data } = await api.get('/violations', { params: { size: 200 } });
    const types = (data.data.items || []).map((v) => formatFieldCategory(v.fieldName));
    return Array.from(new Set(types)).sort();
  },

  async getViolationById(id) {
    const { data } = await api.get(`/violations/${encodeURIComponent(id)}`);
    return mapViolationDetail(data.data);
  },

  /** Records an inspector decision on a case. AI observes, rules validate, humans decide. */
  async decideViolation(id, decisionKey) {
    const caseStatus = VIOLATION_DECISION_TO_CASE_STATUS[decisionKey];
    const { data } = await api.patch(`/violations/${encodeURIComponent(id)}/decision`, { caseStatus });
    return mapViolationDetail(data.data);
  },

  /* ---------- Products ---------- */
  // Real backend: GET /api/products. category filters server-side; risk band
  // and manufacturer (no such column on Product - only free-text brand) are
  // applied client-side against the returned page, same convention as
  // getInspections's richer filter set.
  async getProducts(filters = {}) {
    const { search = '', category = 'ALL', risk = 'ALL', manufacturer = 'ALL' } = filters;
    const params = { size: 100 };
    if (search.trim()) params.search = search.trim();
    if (category !== 'ALL') params.category = category;

    const { data } = await api.get('/products', { params });
    let rows = (data.data.items || []).map(mapProductSummary);

    if (risk !== 'ALL') rows = rows.filter((p) => p.riskLevel === risk);
    if (manufacturer !== 'ALL') rows = rows.filter((p) => p.manufacturer === manufacturer);
    return rows.sort((a, b) => (b.riskScore ?? 0) - (a.riskScore ?? 0));
  },

  async getProductById(id) {
    const encoded = encodeURIComponent(id);
    const [product, history, summary, risk, violations, listing] = await Promise.all([
      api.get(`/products/${encoded}`),
      api.get(`/products/${encoded}/history`),
      api.get(`/products/${encoded}/inspection-summary`),
      api.get(`/products/${encoded}/risk`),
      api.get(`/products/${encoded}/violations`),
      api.get(`/products/${encoded}/online-listing`),
    ]);
    return mapProductDetail(
      product.data.data, history.data.data, summary.data.data, risk.data.data, violations.data.data, listing.data.data,
    );
  },

  /* ---------- Product history (cross-product change ledger) ---------- */
  // Real backend: GET /api/products/history. `flag` has only one real value
  // ("CHANGE") - a declaration snapshot has no compliance-verdict dimension
  // of its own, so the other filter options simply match nothing rather than
  // being backed by an invented classification.
  async getProductHistory(filters = {}) {
    const { search = '', flag = 'ALL' } = filters;
    const params = { size: 100 };
    if (search.trim()) params.search = search.trim();

    const { data } = await api.get('/products/history', { params });
    let rows = (data.data.items || []).map((dto) => {
      const hasPrevious = dto.previousMrp != null || dto.previousNetQuantity != null;
      return {
        key: `${dto.productId}-${dto.versionNumber}`,
        date: dto.capturedAt,
        productId: dto.productId,
        product: dto.productName,
        shortName: dto.productName,
        category: dto.category || '—',
        manufacturer: dto.brand || '—',
        image: null,
        netQuantity: dto.netQuantity || '—',
        mrp: dto.mrp || '—',
        flag: 'CHANGE',
        note: hasPrevious ? 'Declared values changed since the prior record' : 'First recorded declaration for this product',
        previous: hasPrevious ? { mrp: dto.previousMrp, netQuantity: dto.previousNetQuantity } : null,
      };
    });

    if (flag !== 'ALL') rows = rows.filter((r) => r.flag === flag);
    return rows;
  },

  /* ---------- Rules ---------- */
  async getRules(filters = {}) {
    const { search = '', category = 'ALL', status = 'ALL' } = filters;
    const { data } = await api.get('/rules');
    const ruleSet = data.data;

    const allRows = (ruleSet.rules || []).map(mapRuleResponse);
    const categories = Array.from(new Set(allRows.map((r) => r.category))).sort();

    let rows = allRows;
    if (category !== 'ALL') rows = rows.filter((r) => r.category === category);
    if (status !== 'ALL') rows = rows.filter((r) => r.status === status);
    const q = search.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (r) => matches(r.ruleId, q) || matches(r.name, q) || matches(r.summary, q) || matches(r.category, q),
      );
    }

    return {
      ruleset: {
        version: ruleSet.version,
        status: ruleSet.status,
        effectiveFrom: null,
        lastUpdated: null,
        totalRules: ruleSet.ruleCount,
        activeRules: allRows.filter((r) => r.status === 'ACTIVE').length,
      },
      categories,
      rules: rows,
    };
  },

  /** Raw ruleset for one version - unlike getRules() this isn't filtered/re-shaped for the
   * table, and carries `locked` (whether an inspection has already been judged under it), which
   * the version-management UI needs to decide whether to show edit controls at all. */
  async getRuleSet(version) {
    const { data } = await api.get('/rules', { params: version ? { version } : {} });
    return data.data;
  },

  /** Create or amend one rule within a version. Rejected with a LOCKED error if that version
   * has already been referenced by an inspection - publish a new version instead. */
  async upsertRule(payload) {
    const { data } = await api.post('/rules', payload);
    return data.data;
  },

  /** `id` is the rule row's own UUID (RuleResponse.id) - not ruleCode, which isn't unique
   * across versions and isn't what the backend route keys on. */
  async setRuleActive(id, active) {
    const { data } = await api.patch(`/rules/${encodeURIComponent(id)}/active`, null, { params: { active } });
    return data.data;
  },

  /** Clones every rule of sourceVersion into a brand-new, editable newVersion. */
  async publishRuleset(sourceVersion, newVersion) {
    const { data } = await api.post('/rules/publish', { sourceVersion, newVersion });
    return data.data;
  },

  async getRuleById(ruleId) {
    await delay(160);
    const rule = RULE_BY_ID[ruleId];
    if (!rule) {
      const err = new Error(`Rule ${ruleId} was not found`);
      err.status = 404;
      throw err;
    }
    return clone({
      ...rule,
      cases: store.violations.filter((v) => v.ruleId === ruleId).map((v) => ({ id: v.id, productName: v.productName, status: v.status, detectedAt: v.detectedAt })),
    });
  },

  /* ---------- Analytics ---------- */
  async getAnalytics() {
    const { data } = await api.get('/dashboard/analytics');
    return mapAnalyticsResponse(data.data);
  },

  /* ---------- Inspector management (administrator) ---------- */
  // Real backend: GET/POST /api/inspectors. Zones and workload are computed
  // server-side; the response shape matches the mock shape this replaced
  // exactly, so callers (InspectorManagement.jsx, useData hooks) needed no changes.
  async getInspectors(filters = {}) {
    const { search = '', zone = 'ALL', status = 'ALL' } = filters;
    const params = {};
    if (search.trim()) params.search = search.trim();
    if (zone !== 'ALL') params.zone = zone;
    if (status !== 'ALL') params.status = status;
    const { data } = await api.get('/inspectors', { params });
    return data.data;
  },

  async getInspectorById(id) {
    const { data } = await api.get(`/inspectors/${encodeURIComponent(id)}`);
    return data.data;
  },

  /** Zone roster: every zone with the officers posted to it. */
  async getZoneSummary() {
    const { data } = await api.get('/inspectors/zones');
    return data.data;
  },

  /**
   * Creates a real backend account (role INSPECTOR) with a generated password -
   * this form collects no password of its own. The response's
   * `temporaryPassword` is present once; the caller is responsible for
   * showing it to the admin so it can be handed to the officer.
   */
  async createInspector(payload) {
    const { data } = await api.post('/inspectors', payload);
    return data.data;
  },

  async updateInspector(id, patch) {
    const { data } = await api.put(`/inspectors/${encodeURIComponent(id)}`, patch);
    return data.data;
  },

  /** Deactivating an officer disables their account outright (immediate, not just cosmetic). */
  async setInspectorStatus(id, status) {
    const { data } = await api.patch(`/inspectors/${encodeURIComponent(id)}/status`, { status });
    return data.data;
  },

  /**
   * There was previously no way to recover an officer's access once their one-time password
   * from account creation was lost - the stored hash can't be reversed to show it again. This
   * mints a brand-new one, shown once in the response the same way creation does.
   */
  async resetInspectorPassword(id) {
    const { data } = await api.post(`/inspectors/${encodeURIComponent(id)}/reset-password`);
    return data.data;
  },

  async assignZone(id, zone) {
    const { data } = await api.patch(`/inspectors/${encodeURIComponent(id)}/zone`, { zone });
    return data.data;
  },

  /* ---------- Reference data ---------- */
  // Filter-dropdown option lists for Inspections/Products. Categories and
  // manufacturers have no dedicated "distinct values" endpoint, so they're
  // derived from a broad product page - the same trade-off already made for
  // Rules' category list and Violations' type list.
  async getReferenceData() {
    const [productsPage, inspectors, zones] = await Promise.all([
      api.get('/products', { params: { size: 100 } }).catch(() => ({ data: { data: { items: [] } } })),
      api.get('/inspectors', { params: { status: 'ACTIVE' } }).catch(() => ({ data: { data: [] } })),
      api.get('/zones').catch(() => ({ data: { data: [] } })),
    ]);
    const products = productsPage.data.data.items || [];
    const categories = Array.from(new Set(products.map((p) => p.category).filter(Boolean))).sort();
    const manufacturers = Array.from(new Set(products.map((p) => p.brand).filter(Boolean)))
      .sort()
      .map((name) => ({ name }));

    return {
      categories,
      manufacturers,
      inspectors: (inspectors.data.data || []).map((i) => ({ name: i.fullName || i.name })),
      zones: (zones.data.data || []).map((z) => ({ name: z.name })),
    };
  },
};

export { INSPECTION_BY_ID, VIOLATION_BY_ID, PRODUCT_BY_ID };
export default inspectionService;
