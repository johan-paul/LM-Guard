import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { formatNumber } from '../utils/format';

/**
 * KPI tile: micro label, large figure, one line of context.
 * `deltaIntent` decides whether a rise reads as good ('positive'), bad
 * ('negative') or is simply reported without colour ('neutral').
 */
export default function StatCard({
  label,
  value,
  delta,
  deltaIntent = 'neutral',
  description,
  icon: Icon,
  to,
  accent = 'default',
}) {
  const hasDelta = delta !== undefined && delta !== null;
  const rising = hasDelta && delta > 0;

  const deltaTone =
    deltaIntent === 'neutral'
      ? 'text-ink-500'
      : (rising && deltaIntent === 'positive') || (!rising && deltaIntent === 'negative')
        ? 'text-success-700'
        : 'text-danger-600';

  const accentRing =
    accent === 'critical'
      ? 'text-critical-600 bg-critical-50'
      : accent === 'warning'
        ? 'text-warning-700 bg-warning-50'
        : accent === 'success'
          ? 'text-success-700 bg-success-50'
          : 'text-ink-500 bg-canvas';

  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="eyebrow">{label}</p>
        {Icon && (
          <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md ${accentRing}`}>
            <Icon className="h-[15px] w-[15px]" strokeWidth={1.9} />
          </span>
        )}
      </div>

      <p className="tabular mt-3 text-kpi font-bold text-ink-900">
        {typeof value === 'number' ? formatNumber(value) : value}
      </p>

      <div className="mt-1.5 flex items-center gap-1.5 text-xs">
        {hasDelta && (
          <span className={`inline-flex items-center gap-0.5 font-medium ${deltaTone}`}>
            {rising ? <ArrowUpRight className="h-3.5 w-3.5" strokeWidth={2.25} /> : <ArrowDownRight className="h-3.5 w-3.5" strokeWidth={2.25} />}
            {Math.abs(delta).toFixed(1)}%
          </span>
        )}
        {description && <span className="truncate text-ink-500">{description}</span>}
      </div>
    </>
  );

  const base = 'panel px-5 py-4 transition-colors duration-150';

  return to ? (
    <Link to={to} className={`${base} block hover:border-line-strong`}>
      {body}
    </Link>
  ) : (
    <div className={base}>{body}</div>
  );
}

/** Compact figure used inside panels (risk overview, analytics summaries). */
export function MiniStat({ label, value, tone = 'default', caption }) {
  const tones = {
    default: 'text-ink-900',
    critical: 'text-critical-600',
    warning: 'text-warning-700',
    success: 'text-success-700',
    brand: 'text-brand-600',
  };
  return (
    <div className="min-w-0">
      <p className="eyebrow">{label}</p>
      <p className={`tabular mt-1.5 text-[24px] font-bold leading-7 ${tones[tone]}`}>
        {typeof value === 'number' ? formatNumber(value) : value}
      </p>
      {caption && <p className="mt-0.5 truncate text-xs text-ink-500">{caption}</p>}
    </div>
  );
}
