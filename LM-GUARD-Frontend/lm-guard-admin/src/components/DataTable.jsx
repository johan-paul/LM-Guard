import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { TableSkeleton } from './LoadingState';
import EmptyState from './EmptyState';

/**
 * Investigation-style data table.
 *
 * columns: [{ key, header, render?, align?, width?, className?, headerClassName?, hideBelow? }]
 * `hideBelow` accepts a Tailwind breakpoint ('md' | 'lg' | 'xl') to drop
 * secondary columns on narrow screens rather than squeezing them.
 */
const HIDE = {
  sm: 'hidden sm:table-cell',
  md: 'hidden md:table-cell',
  lg: 'hidden lg:table-cell',
  xl: 'hidden xl:table-cell',
};

export default function DataTable({
  columns,
  rows,
  keyField = 'id',
  onRowClick,
  loading = false,
  empty,
  emptyTitle,
  emptyDescription,
  className = '',
  dense = false,
}) {
  if (loading) return <TableSkeleton rows={6} cols={Math.min(columns.length, 6)} />;

  if (!rows.length) {
    return (
      empty || (
        <EmptyState
          title={emptyTitle || 'No records found'}
          description={emptyDescription || 'Adjust the filters or search terms to widen the result set.'}
        />
      )
    );
  }

  return (
    <div className={`table-wrap scrollbar-slim ${className}`}>
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                scope="col"
                style={col.width ? { width: col.width } : undefined}
                className={`${col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : ''} ${
                  col.hideBelow ? HIDE[col.hideBelow] : ''
                } ${col.headerClassName || ''}`}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row[keyField]}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={onRowClick ? 'row-link' : ''}
            >
              {columns.map((col) => (
                <td
                  key={col.key}
                  // A width hint on <th> alone doesn't reliably protect a narrow column under
                  // `table-layout: auto` - a neighbouring column with long unbounded text (a
                  // "Finding" description, say) can still starve it down to a couple of
                  // pixels, which is exactly the failure mode for a thumbnail image: it loads
                  // successfully and sits at opacity 1, just rendered too narrow to see.
                  style={col.width ? { width: col.width, minWidth: col.width } : undefined}
                  className={`${dense ? 'py-2.5' : ''} ${
                    col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : ''
                  } ${col.hideBelow ? HIDE[col.hideBelow] : ''} ${col.className || ''}`}
                >
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Table footer pagination — pairs with the usePagination hook. */
export function Pagination({ page, pageCount, from, to, total, onPrev, onNext, label = 'records' }) {
  if (total === 0) return null;
  return (
    <div className="flex flex-col gap-3 border-t border-line px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="tabular text-xs text-ink-500">
        Showing <span className="font-medium text-ink-800">{from}</span>–
        <span className="font-medium text-ink-800">{to}</span> of{' '}
        <span className="font-medium text-ink-800">{total}</span> {label}
      </p>
      <div className="flex items-center gap-2">
        <button type="button" onClick={onPrev} disabled={page <= 1} className="btn-secondary btn-sm">
          <ChevronLeft className="h-3.5 w-3.5" /> Previous
        </button>
        <span className="tabular px-1 text-xs text-ink-500">
          Page {page} of {pageCount}
        </span>
        <button type="button" onClick={onNext} disabled={page >= pageCount} className="btn-secondary btn-sm">
          Next <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

/** Primary cell: a bold identifier over a quiet secondary line. */
export function PrimaryCell({ title, subtitle, mono = false }) {
  return (
    <span className="block min-w-0">
      <span className={`block truncate font-medium text-ink-900 ${mono ? 'font-mono text-13' : ''}`}>{title}</span>
      {subtitle && <span className="block truncate text-xs text-ink-500">{subtitle}</span>}
    </span>
  );
}
