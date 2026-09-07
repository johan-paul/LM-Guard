import React from 'react';

/** Neutral skeleton primitives — no spinners in the data surfaces. */

export function SkeletonLine({ className = 'h-3 w-full' }) {
  return <span className={`block skeleton ${className}`} />;
}

export function TableSkeleton({ rows = 6, cols = 6 }) {
  return (
    <div className="px-5 py-2" aria-hidden="true">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex items-center gap-6 py-3.5 border-b border-line-soft last:border-b-0">
          {Array.from({ length: cols }).map((__, c) => (
            <SkeletonLine key={c} className={`h-3 ${c === 0 ? 'w-28' : c === 1 ? 'w-48' : 'w-20'}`} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function CardSkeleton({ height = 'h-[132px]' }) {
  return <div className={`panel ${height} animate-pulse-soft`} aria-hidden="true" />;
}

export function StatGridSkeleton({ count = 4 }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <CardSkeleton key={i} />
      ))}
    </div>
  );
}

export function DetailSkeleton() {
  return (
    <div className="space-y-6" aria-hidden="true">
      <div className="panel h-24 animate-pulse-soft" />
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-7 panel h-[460px] animate-pulse-soft" />
        <div className="lg:col-span-5 panel h-[460px] animate-pulse-soft" />
      </div>
    </div>
  );
}

export default function LoadingState({ label = 'Loading records' }) {
  return (
    <div className="flex items-center gap-3 px-5 py-6 text-13 text-ink-500">
      <span className="w-1.5 h-1.5 rounded-full bg-brand-600 animate-pulse-soft" />
      {label}…
    </div>
  );
}
