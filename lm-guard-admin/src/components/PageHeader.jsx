import React from 'react';

/**
 * Page title block. `actions` sits right on desktop and wraps below on mobile.
 */
export default function PageHeader({ title, subtitle, eyebrow, actions, meta, className = '' }) {
  return (
    <div className={`flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between ${className}`}>
      <div className="min-w-0">
        {eyebrow && <div className="eyebrow mb-2">{eyebrow}</div>}
        <h1 className="text-[26px] leading-8 sm:text-page font-semibold text-ink-900">{title}</h1>
        {subtitle && <p className="mt-1.5 text-sm text-ink-500 max-w-2xl">{subtitle}</p>}
        {meta && <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2">{meta}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 shrink-0">{actions}</div>}
    </div>
  );
}

/** Small labelled figure used in header meta rows and detail summaries. */
export function MetaItem({ label, value, mono = false }) {
  return (
    <div className="flex flex-col">
      <span className="text-micro font-semibold uppercase text-ink-400">{label}</span>
      <span className={`text-13 text-ink-800 ${mono ? 'font-mono' : 'font-medium'}`}>{value}</span>
    </div>
  );
}
