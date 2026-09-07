import React from 'react';
import { CheckCircle2, AlertTriangle, AlertOctagon, HelpCircle, CircleDot, ShieldAlert, Ban, Archive, FileEdit, PauseCircle } from 'lucide-react';

/**
 * One badge vocabulary for the whole platform.
 * Stored verdicts keep the backend contract (NON_COMPLIANT / REVIEW_REQUIRED);
 * the label shown to inspectors is VIOLATION / REVIEW.
 */
const MAP = {
  /* Inspection verdicts */
  COMPLIANT: { label: 'Compliant', icon: CheckCircle2, cls: 'bg-success-50 text-success-700 border-success-100' },
  NON_COMPLIANT: { label: 'Violation', icon: AlertOctagon, cls: 'bg-danger-50 text-danger-700 border-danger-100' },
  VIOLATION: { label: 'Violation', icon: AlertOctagon, cls: 'bg-danger-50 text-danger-700 border-danger-100' },
  REVIEW_REQUIRED: { label: 'Review', icon: AlertTriangle, cls: 'bg-warning-50 text-warning-700 border-warning-100' },
  REVIEW: { label: 'Review', icon: AlertTriangle, cls: 'bg-warning-50 text-warning-700 border-warning-100' },
  INCONCLUSIVE: { label: 'Inconclusive', icon: HelpCircle, cls: 'bg-neutralbadge-50 text-neutralbadge-600 border-line-strong' },

  /* Case statuses */
  OPEN: { label: 'Open', icon: CircleDot, cls: 'bg-brand-50 text-brand-700 border-brand-100' },
  UNDER_REVIEW: { label: 'Under review', icon: AlertTriangle, cls: 'bg-warning-50 text-warning-700 border-warning-100' },
  ESCALATED: { label: 'Escalated', icon: ShieldAlert, cls: 'bg-critical-50 text-critical-600 border-danger-100' },
  CONFIRMED: { label: 'Confirmed', icon: AlertOctagon, cls: 'bg-danger-50 text-danger-700 border-danger-100' },
  DISMISSED: { label: 'Dismissed', icon: Ban, cls: 'bg-neutralbadge-50 text-neutralbadge-600 border-line-strong' },

  /* Rule statuses */
  ACTIVE: { label: 'Active', icon: CheckCircle2, cls: 'bg-success-50 text-success-700 border-success-100' },
  INACTIVE: { label: 'Inactive', icon: PauseCircle, cls: 'bg-neutralbadge-50 text-neutralbadge-600 border-line-strong' },
  DRAFT: { label: 'Draft', icon: FileEdit, cls: 'bg-brand-50 text-brand-700 border-brand-100' },
  DEPRECATED: { label: 'Deprecated', icon: Archive, cls: 'bg-neutralbadge-50 text-neutralbadge-600 border-line-strong' },

  /* Ruleset provenance (backend RuleSetResponse.status) */
  CUSTOM: { label: 'Custom ruleset', icon: CheckCircle2, cls: 'bg-success-50 text-success-700 border-success-100' },
  SAMPLE: { label: 'Sample ruleset', icon: AlertTriangle, cls: 'bg-warning-50 text-warning-700 border-warning-100' },

  /* Package-change ledger */
  CHANGE: { label: 'Change', icon: FileEdit, cls: 'bg-brand-50 text-brand-700 border-brand-100' },
};

const FALLBACK = { label: 'Unknown', icon: HelpCircle, cls: 'bg-neutralbadge-50 text-neutralbadge-600 border-line-strong' };

export default function StatusBadge({ status, size = 'sm', showIcon = true, className = '' }) {
  const key = String(status || '').toUpperCase().replace(/[\s-]/g, '_');
  const cfg = MAP[key] || FALLBACK;
  const Icon = cfg.icon;
  const large = size === 'lg';

  return (
    <span className={`badge ${large ? 'badge-lg' : ''} ${cfg.cls} ${className}`}>
      {showIcon && <Icon className={large ? 'w-3.5 h-3.5' : 'w-3 h-3'} strokeWidth={2.25} />}
      {cfg.label}
    </span>
  );
}

export function statusLabel(status) {
  const key = String(status || '').toUpperCase().replace(/[\s-]/g, '_');
  return (MAP[key] || FALLBACK).label;
}
