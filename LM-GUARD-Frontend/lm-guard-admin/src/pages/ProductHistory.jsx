import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Download, TrendingDown, TrendingUp, Minus, Package } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import DataTable, { PrimaryCell, Pagination } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import { useProductHistory, usePagination, useDebounced } from '../hooks/useData';
import { formatDate, formatMonthYear } from '../utils/format';

const FLAG_OPTIONS = [
  { value: 'ALL', label: 'All entries' },
  { value: 'VIOLATION', label: 'Violation recorded' },
  { value: 'REVIEW', label: 'Review raised' },
  { value: 'CHANGE', label: 'Declaration changed' },
  { value: 'COMPLIANT', label: 'Compliant' },
];

const parseQty = (s = '') => {
  const m = String(s).match(/([\d.]+)\s*(kg|g|ml|l)/i);
  if (!m) return null;
  const n = parseFloat(m[1]);
  const unit = m[2].toLowerCase();
  return unit === 'kg' || unit === 'l' ? n * 1000 : n;
};
const parseMoney = (s = '') => {
  const m = String(s).match(/([\d,.]+)/);
  return m ? parseFloat(m[1].replace(/,/g, '')) : null;
};

export default function ProductHistory() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [flag, setFlag] = useState('ALL');

  const debouncedSearch = useDebounced(search);
  const { history, loading } = useProductHistory({ search: debouncedSearch, flag });
  const pager = usePagination(history, 12);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Product History"
        subtitle="Chronological ledger of declared pack size and price changes recorded across every monitored product."
        actions={
          <button type="button" className="btn-secondary btn-lg">
            <Download className="h-4 w-4" strokeWidth={1.9} />
            Export ledger
          </button>
        }
      />

      <section className="panel-flush">
        <SearchFilterBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by product, manufacturer or change note"
          onReset={() => {
            setSearch('');
            setFlag('ALL');
          }}
          filters={[{ key: 'flag', label: 'Entry type', value: flag, onChange: setFlag, options: FLAG_OPTIONS }]}
        />

        <DataTable
          loading={loading}
          rows={pager.rows}
          keyField="key"
          onRowClick={(row) => navigate(`/products/${row.productId}`)}
          emptyTitle="No history entries match these filters"
          columns={[
            {
              key: 'period',
              header: 'Period',
              width: 148,
              render: (r) => (
                <span className="block">
                  <span className="block text-13 font-medium text-ink-900">{formatMonthYear(r.date)}</span>
                  <span className="block text-xs text-ink-400">{formatDate(r.date)}</span>
                </span>
              ),
            },
            {
              key: 'product',
              header: 'Product',
              render: (r) => (
                <span className="flex items-center gap-3">
                  {r.image ? (
                    <img src={r.image} alt="" className="h-9 w-8 shrink-0 rounded border border-line object-cover" aria-hidden="true" />
                  ) : (
                    <span className="flex h-9 w-8 shrink-0 items-center justify-center rounded border border-line bg-canvas" aria-hidden="true">
                      <Package className="h-4 w-4 text-ink-300" strokeWidth={1.5} />
                    </span>
                  )}
                  <PrimaryCell title={r.shortName} subtitle={r.manufacturer} />
                </span>
              ),
            },
            {
              key: 'declared',
              header: 'Declared',
              width: 168,
              render: (r) => (
                <span className="tabular block text-13 text-ink-900">
                  <span className="font-medium">{r.netQuantity}</span>
                  <span className="mx-1.5 text-ink-300">·</span>
                  <span className="font-medium">{r.mrp}</span>
                </span>
              ),
            },
            {
              key: 'delta',
              header: 'Change',
              width: 132,
              hideBelow: 'md',
              render: (r) => <DeltaCell entry={r} />,
            },
            {
              key: 'note',
              header: 'Recorded observation',
              hideBelow: 'lg',
              render: (r) => <span className="text-ink-600">{r.note}</span>,
            },
            {
              key: 'flag',
              header: 'Outcome',
              align: 'right',
              render: (r) => <StatusBadge status={r.flag} />,
            },
            {
              key: 'action',
              header: '',
              align: 'right',
              width: 92,
              render: (r) => (
                <Link
                  to={`/products/${r.productId}`}
                  onClick={(e) => e.stopPropagation()}
                  className="inline-flex items-center gap-1 text-13 font-medium text-brand-600 hover:text-brand-700"
                >
                  Profile <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
                </Link>
              ),
            },
          ]}
        />

        <Pagination
          page={pager.page}
          pageCount={pager.pageCount}
          from={pager.from}
          to={pager.to}
          total={pager.total}
          onPrev={pager.prev}
          onNext={pager.next}
          label="entries"
        />
      </section>
    </div>
  );
}

function DeltaCell({ entry }) {
  if (!entry.previous) return <span className="text-13 text-ink-400">First record</span>;

  const q = parseQty(entry.netQuantity);
  const pq = parseQty(entry.previous.netQuantity);
  const m = parseMoney(entry.mrp);
  const pm = parseMoney(entry.previous.mrp);

  const qtyDown = q !== null && pq !== null && q < pq;
  const priceUp = m !== null && pm !== null && m > pm;
  const changed = (q !== pq && q !== null && pq !== null) || (m !== pm && m !== null && pm !== null);

  if (!changed) {
    return (
      <span className="inline-flex items-center gap-1.5 text-13 text-ink-400">
        <Minus className="h-3.5 w-3.5" strokeWidth={2} /> Unchanged
      </span>
    );
  }

  return (
    <span className="flex flex-col gap-0.5">
      {q !== pq && q !== null && pq !== null && (
        <span className={`tabular inline-flex items-center gap-1.5 text-13 font-medium ${qtyDown ? 'text-danger-600' : 'text-ink-700'}`}>
          {qtyDown ? <TrendingDown className="h-3.5 w-3.5" strokeWidth={2} /> : <TrendingUp className="h-3.5 w-3.5" strokeWidth={2} />}
          {qtyDown ? '−' : '+'}
          {Math.abs(q - pq)} {String(entry.netQuantity).match(/l/i) ? 'mL' : 'g'}
        </span>
      )}
      {m !== pm && m !== null && pm !== null && (
        <span className={`tabular inline-flex items-center gap-1.5 text-13 font-medium ${priceUp ? 'text-warning-700' : 'text-ink-700'}`}>
          {priceUp ? <TrendingUp className="h-3.5 w-3.5" strokeWidth={2} /> : <TrendingDown className="h-3.5 w-3.5" strokeWidth={2} />}
          {priceUp ? '+' : '−'}₹{Math.abs(m - pm).toFixed(2)}
        </span>
      )}
    </span>
  );
}
