import React from 'react';
import { Search, X, SlidersHorizontal } from 'lucide-react';

/**
 * Filter strip that sits directly above a data table.
 *
 * filters: [{ key, label, value, options: [{value,label}], onChange }]
 */
export default function SearchFilterBar({
  search,
  onSearchChange,
  searchPlaceholder = 'Search records',
  filters = [],
  onReset,
  trailing,
  className = '',
}) {
  const active = filters.filter((f) => f.value && f.value !== 'ALL').length + (search ? 1 : 0);

  return (
    <div className={`flex flex-col gap-3 border-b border-line px-5 py-3.5 xl:flex-row xl:items-center ${className}`}>
      <div className="relative min-w-0 flex-1 xl:max-w-[320px]">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-[15px] w-[15px] -translate-y-1/2 text-ink-400" />
        <input
          type="search"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder={searchPlaceholder}
          className="input pl-9"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {filters.length > 0 && (
          <SlidersHorizontal className="hidden h-4 w-4 shrink-0 text-ink-400 xl:block" strokeWidth={1.75} />
        )}
        {filters.map((f) => (
          <label key={f.key} className="relative">
            <span className="sr-only">{f.label}</span>
            <select value={f.value} onChange={(e) => f.onChange(e.target.value)} className="select w-auto min-w-[132px]">
              {f.options.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        ))}

        {active > 0 && onReset && (
          <button type="button" onClick={onReset} className="btn-ghost btn-sm">
            <X className="h-3.5 w-3.5" /> Clear
            <span className="tabular ml-0.5 rounded-sm bg-line-soft px-1.5 text-[10px] font-semibold text-ink-500">
              {active}
            </span>
          </button>
        )}
      </div>

      {trailing && <div className="flex items-center gap-2 xl:ml-auto">{trailing}</div>}
    </div>
  );
}

/** Segmented control — used for time ranges and tab-like switches. */
export function SegmentedControl({ options, value, onChange, size = 'sm', className = '' }) {
  return (
    <div className={`inline-flex items-center rounded-md border border-line bg-canvas p-0.5 ${className}`}>
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            onClick={() => onChange(o.value)}
            className={`rounded-[5px] px-2.5 font-medium transition-colors ${size === 'sm' ? 'h-7 text-xs' : 'h-8 text-13'} ${
              active ? 'bg-surface text-ink-900 shadow-card' : 'text-ink-500 hover:text-ink-800'
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
