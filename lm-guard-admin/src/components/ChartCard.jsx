import React from 'react';

/** Shared chart palette — blue carries data, status colours carry meaning only. */
export const CHART = {
  primary: '#2563EB',
  primarySoft: '#93B4F8',
  navy: '#16324F',
  grid: '#EEF1F5',
  axis: '#667085',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
  critical: '#B42318',
  neutral: '#98A2B3',
};

/**
 * Panel wrapper for a visualisation: title, optional description, right-hand
 * controls, and a fixed-height plot area so charts align across a grid.
 */
export default function ChartCard({ title, subtitle, actions, legend, height = 280, children, className = '', bodyClassName = '' }) {
  return (
    <section className={`panel-flush flex flex-col ${className}`}>
      <div className="panel-header">
        <div className="min-w-0">
          <h2 className="panel-title">{title}</h2>
          {subtitle && <p className="panel-subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>

      {legend && <div className="flex flex-wrap items-center gap-4 border-b border-line-soft px-5 py-2.5">{legend}</div>}

      <div className={`min-w-0 flex-1 px-2 py-4 sm:px-4 ${bodyClassName}`} style={{ minHeight: height }}>
        {children}
      </div>
    </section>
  );
}

export function LegendItem({ color, label, value, dashed = false }) {
  return (
    <span className="flex items-center gap-2 text-xs text-ink-500">
      <span
        className="h-[3px] w-4 shrink-0 rounded-full"
        style={{ background: dashed ? `repeating-linear-gradient(90deg, ${color} 0 4px, transparent 4px 7px)` : color }}
      />
      {label}
      {value !== undefined && <span className="tabular font-semibold text-ink-800">{value}</span>}
    </span>
  );
}

/** Custom recharts tooltip — matches the platform surface treatment. */
export function ChartTooltip({ active, payload, label, formatter, labelFormatter }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2 shadow-pop">
      <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-ink-400">
        {labelFormatter ? labelFormatter(label) : label}
      </p>
      <ul className="space-y-1">
        {payload.map((p) => (
          <li key={p.dataKey} className="flex items-center gap-2.5 text-xs">
            <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: p.color || p.fill }} />
            <span className="text-ink-500">{p.name}</span>
            <span className="tabular ml-auto font-semibold text-ink-900">
              {formatter ? formatter(p.value, p.dataKey) : p.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
