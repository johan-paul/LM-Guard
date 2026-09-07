import React from 'react';

const LEVELS = {
  HIGH: { label: 'High', cls: 'bg-critical-50 text-critical-600 border-danger-100', bar: 'bg-critical-600' },
  CRITICAL: { label: 'Critical', cls: 'bg-critical-50 text-critical-600 border-danger-100', bar: 'bg-critical-600' },
  MEDIUM: { label: 'Medium', cls: 'bg-warning-50 text-warning-700 border-warning-100', bar: 'bg-warning-600' },
  LOW: { label: 'Low', cls: 'bg-success-50 text-success-700 border-success-100', bar: 'bg-success-600' },
};

export const levelFromScore = (score) => (score >= 70 ? 'HIGH' : score >= 40 ? 'MEDIUM' : 'LOW');

export function riskTone(level) {
  return LEVELS[String(level || '').toUpperCase()] || LEVELS.LOW;
}

export default function RiskBadge({ level, score, className = '' }) {
  const key = String(level || levelFromScore(score || 0)).toUpperCase();
  const cfg = LEVELS[key] || LEVELS.LOW;
  return <span className={`badge ${cfg.cls} ${className}`}>{cfg.label} risk</span>;
}

/**
 * Compact score meter — "92 / 100" with a 4px indicator bar.
 * Used in every ranked table so risk reads consistently across the platform.
 */
export function RiskScore({ score = 0, showTrack = true, width = 'w-[92px]', className = '' }) {
  const cfg = riskTone(levelFromScore(score));
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <span className="tabular w-[62px] shrink-0 whitespace-nowrap text-13 font-semibold text-ink-900">
        {score}
        <span className="font-normal text-ink-400"> / 100</span>
      </span>
      {showTrack && (
        <span className={`${width} h-1 rounded-full bg-line overflow-hidden shrink-0`} aria-hidden="true">
          <span
            className={`block h-full rounded-full ${cfg.bar} transition-[width] duration-500 ease-out-expo`}
            style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
          />
        </span>
      )}
    </div>
  );
}

/** Confidence readout — figure plus a quiet neutral bar; never colour-coded as a verdict. */
export function ConfidenceMeter({ value = 0, width = 'w-[64px]', className = '' }) {
  const pct = Math.round(value * 100);
  const low = pct < 70;
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <span className={`tabular text-13 font-medium w-[38px] shrink-0 ${low ? 'text-warning-700' : 'text-ink-900'}`}>
        {pct}%
      </span>
      <span className={`${width} h-1 rounded-full bg-line overflow-hidden shrink-0`} aria-hidden="true">
        <span
          className={`block h-full rounded-full ${low ? 'bg-warning-600' : 'bg-navy-700'}`}
          style={{ width: `${pct}%` }}
        />
      </span>
    </div>
  );
}
