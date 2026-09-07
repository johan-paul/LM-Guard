import React from 'react';
import { Check, AlertTriangle, X, HelpCircle } from 'lucide-react';
import { ConfidenceMeter } from './RiskBadge';

const STATE = {
  COMPLIANT: {
    icon: Check,
    ring: 'bg-success-50 text-success-700',
    note: 'Verified',
    noteCls: 'text-success-700',
  },
  VIOLATION: {
    icon: X,
    ring: 'bg-danger-50 text-danger-600',
    note: 'Rule not satisfied',
    noteCls: 'text-danger-600',
  },
  REVIEW_REQUIRED: {
    icon: AlertTriangle,
    ring: 'bg-warning-50 text-warning-700',
    note: 'Manual review required',
    noteCls: 'text-warning-700',
  },
  INCONCLUSIVE: {
    icon: HelpCircle,
    ring: 'bg-neutralbadge-50 text-neutralbadge-600',
    note: 'Insufficient evidence',
    noteCls: 'text-ink-500',
  },
};

/**
 * One line of the compliance summary: field, detected value, confidence and the
 * rule that produced the outcome.
 */
export default function ComplianceItem({ declaration, ruleId, onRuleClick }) {
  const cfg = STATE[declaration.status] || STATE.INCONCLUSIVE;
  const Icon = cfg.icon;
  const rule = ruleId || declaration.ruleId;

  return (
    <div className="flex items-start gap-3.5 border-b border-line-soft px-5 py-3.5 last:border-b-0">
      <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${cfg.ring}`}>
        <Icon className="h-3 w-3" strokeWidth={3} />
      </span>

      <div className="grid min-w-0 flex-1 gap-x-6 gap-y-1 sm:grid-cols-[1fr_1fr_auto] sm:items-center">
        <div className="min-w-0">
          <p className="text-13 font-medium text-ink-900">{declaration.label}</p>
          <p className={`text-xs ${cfg.noteCls}`}>{cfg.note}</p>
        </div>

        <p className="min-w-0 truncate text-13 text-ink-700" title={declaration.detected}>
          {declaration.detected || '—'}
        </p>

        <div className="flex items-center gap-4">
          <ConfidenceMeter value={declaration.confidence} />
          {rule &&
            (onRuleClick ? (
              <button
                type="button"
                onClick={() => onRuleClick(rule)}
                className="font-mono text-[11px] text-ink-400 transition-colors hover:text-brand-600"
              >
                {rule}
              </button>
            ) : (
              <span className="font-mono text-[11px] text-ink-400">{rule}</span>
            ))}
        </div>
      </div>
    </div>
  );
}
