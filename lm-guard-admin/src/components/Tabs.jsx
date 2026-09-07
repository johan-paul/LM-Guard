import React from 'react';

/**
 * Underlined tab strip. Scrolls horizontally on narrow screens rather than wrapping.
 */
export default function Tabs({ tabs, value, onChange, className = '' }) {
  return (
    <div className={`scrollbar-slim -mb-px flex gap-1 overflow-x-auto border-b border-line ${className}`}>
      {tabs.map((tab) => {
        const active = tab.value === value;
        return (
          <button
            key={tab.value}
            type="button"
            onClick={() => onChange(tab.value)}
            className={`relative whitespace-nowrap px-3.5 pb-3 pt-2 text-13 font-medium transition-colors ${
              active ? 'text-ink-900' : 'text-ink-500 hover:text-ink-800'
            }`}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span
                className={`tabular ml-2 rounded-sm px-1.5 py-0.5 text-[10.5px] font-semibold ${
                  active ? 'bg-brand-50 text-brand-700' : 'bg-line-soft text-ink-500'
                }`}
              >
                {tab.count}
              </span>
            )}
            <span
              className={`absolute inset-x-0 -bottom-px h-0.5 rounded-t transition-colors ${
                active ? 'bg-brand-600' : 'bg-transparent'
              }`}
            />
          </button>
        );
      })}
    </div>
  );
}
