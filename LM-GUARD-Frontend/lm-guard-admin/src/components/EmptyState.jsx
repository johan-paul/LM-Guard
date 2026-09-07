import React from 'react';
import { SearchX } from 'lucide-react';

export default function EmptyState({
  icon: Icon = SearchX,
  title = 'No records found',
  description = 'Adjust the filters or search terms to widen the result set.',
  action,
  compact = false,
}) {
  return (
    <div className={`flex flex-col items-center justify-center text-center ${compact ? 'py-10 px-6' : 'py-16 px-8'}`}>
      <div className="w-10 h-10 rounded-lg bg-canvas border border-line flex items-center justify-center text-ink-400 mb-4">
        <Icon className="w-5 h-5" strokeWidth={1.75} />
      </div>
      <p className="text-sm font-semibold text-ink-900">{title}</p>
      <p className="mt-1 text-13 text-ink-500 max-w-sm">{description}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
