import React from 'react';
import { Check, AlertTriangle, Clock } from 'lucide-react';
import { formatTime, formatDate } from '../utils/format';

const NODE = {
  done: { icon: Check, cls: 'bg-navy-900 text-white border-navy-900' },
  alert: { icon: AlertTriangle, cls: 'bg-danger-600 text-white border-danger-600' },
  pending: { icon: Clock, cls: 'bg-surface text-ink-400 border-line-strong border-dashed' },
};

/**
 * Vertical processing timeline: image → OCR → facts → rules → finding → review.
 */
export default function Timeline({ steps = [], showDate = false, className = '' }) {
  return (
    <ol className={`relative ${className}`}>
      {steps.map((step, i) => {
        const cfg = NODE[step.state] || NODE.done;
        const Icon = cfg.icon;
        const last = i === steps.length - 1;
        const pending = step.state === 'pending';

        return (
          <li key={`${step.label}-${i}`} className="relative flex gap-3.5 pb-5 last:pb-0">
            {!last && (
              <span
                className={`absolute left-[11px] top-[22px] bottom-0 w-px ${pending ? 'bg-line' : 'bg-line-strong'}`}
                aria-hidden="true"
              />
            )}

            <span
              className={`relative z-10 mt-0.5 flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full border ${cfg.cls}`}
            >
              <Icon className="h-3 w-3" strokeWidth={3} />
            </span>

            <div className="min-w-0 flex-1 pt-0.5">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5">
                <p className={`text-13 font-medium ${pending ? 'text-ink-500' : 'text-ink-900'}`}>{step.label}</p>
                <p className="tabular shrink-0 font-mono text-[11px] text-ink-400">
                  {step.at ? (showDate ? `${formatDate(step.at)} · ${formatTime(step.at)}` : formatTime(step.at)) : 'Pending'}
                </p>
              </div>
              {step.detail && <p className="mt-0.5 text-xs text-ink-500">{step.detail}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/**
 * Product change timeline — month heading, pack facts, and the outcome recorded
 * against that revision.
 */
export function ChangeTimeline({ entries = [], renderBadge, className = '' }) {
  return (
    <ol className={`relative ${className}`}>
      {entries.map((entry, i) => {
        const last = i === entries.length - 1;
        const isViolation = entry.flag === 'VIOLATION';
        const isReview = entry.flag === 'REVIEW';
        const dot = isViolation ? 'bg-danger-600' : isReview ? 'bg-warning-600' : entry.flag === 'CHANGE' ? 'bg-brand-600' : 'bg-success-600';

        return (
          <li key={`${entry.date}-${i}`} className="relative flex gap-4 pb-6 last:pb-0">
            {!last && <span className="absolute left-[5px] top-4 bottom-0 w-px bg-line" aria-hidden="true" />}
            <span className={`relative z-10 mt-1.5 h-[11px] w-[11px] shrink-0 rounded-full ${dot} ring-4 ring-surface`} />

            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
                <p className="text-13 font-semibold text-ink-900">{entry.heading}</p>
                {renderBadge ? renderBadge(entry) : null}
              </div>

              <p className="tabular mt-1 text-13 text-ink-700">
                <span className="font-medium">{entry.netQuantity}</span>
                <span className="mx-1.5 text-ink-300">·</span>
                <span className="font-medium">{entry.mrp}</span>
                {entry.deltaNote && (
                  <span className={`ml-2 text-xs font-medium ${isViolation ? 'text-danger-600' : 'text-warning-700'}`}>
                    {entry.deltaNote}
                  </span>
                )}
              </p>

              {entry.note && <p className="mt-1 text-xs text-ink-500">{entry.note}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
