import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Download, Package } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import RiskBadge, { RiskScore } from '../components/RiskBadge';
import DataTable, { PrimaryCell, Pagination } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import { useProducts, useReferenceData, usePagination, useDebounced } from '../hooks/useData';
import { formatDate } from '../utils/format';

const RISK_OPTIONS = [
  { value: 'ALL', label: 'All risk levels' },
  { value: 'HIGH', label: 'High risk' },
  { value: 'MEDIUM', label: 'Medium risk' },
  { value: 'LOW', label: 'Low risk' },
];

export default function Products() {
  const navigate = useNavigate();
  const { reference } = useReferenceData();

  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('ALL');
  const [risk, setRisk] = useState('ALL');
  const [manufacturer, setManufacturer] = useState('ALL');

  const debouncedSearch = useDebounced(search);
  const { products, loading } = useProducts({ search: debouncedSearch, category, risk, manufacturer });
  const pager = usePagination(products, 10);

  const reset = () => {
    setSearch('');
    setCategory('ALL');
    setRisk('ALL');
    setManufacturer('ALL');
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Products"
        subtitle="Registry of monitored packaged commodities with their compliance profile and inspection record."
        actions={
          <button type="button" className="btn-secondary btn-lg">
            <Download className="h-4 w-4" strokeWidth={1.9} />
            Export registry
          </button>
        }
      />

      <section className="panel-flush">
        <SearchFilterBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by product, ID or manufacturer"
          onReset={reset}
          filters={[
            {
              key: 'category',
              label: 'Category',
              value: category,
              onChange: setCategory,
              options: [
                { value: 'ALL', label: 'All categories' },
                ...(reference?.categories || []).map((c) => ({ value: c, label: c })),
              ],
            },
            {
              key: 'manufacturer',
              label: 'Manufacturer',
              value: manufacturer,
              onChange: setManufacturer,
              options: [
                { value: 'ALL', label: 'All manufacturers' },
                ...(reference?.manufacturers || []).map((m) => ({ value: m.name, label: m.name })),
              ],
            },
            { key: 'risk', label: 'Risk', value: risk, onChange: setRisk, options: RISK_OPTIONS },
          ]}
        />

        <DataTable
          loading={loading}
          rows={pager.rows}
          onRowClick={(row) => navigate(`/products/${row.id}`)}
          emptyTitle="No products match these filters"
          columns={[
            {
              key: 'product',
              header: 'Product',
              render: (r) => (
                <span className="flex items-center gap-3">
                  {r.image ? (
                    <img
                      src={r.image}
                      alt=""
                      className="h-10 w-9 shrink-0 rounded border border-line object-cover"
                      aria-hidden="true"
                    />
                  ) : (
                    <span
                      className="flex h-10 w-9 shrink-0 items-center justify-center rounded border border-line bg-canvas"
                      aria-hidden="true"
                    >
                      <Package className="h-4 w-4 text-ink-300" strokeWidth={1.5} />
                    </span>
                  )}
                  <PrimaryCell title={r.shortName} subtitle={r.id} />
                </span>
              ),
            },
            { key: 'category', header: 'Category', hideBelow: 'lg', render: (r) => r.category },
            { key: 'manufacturer', header: 'Manufacturer', hideBelow: 'md', render: (r) => r.manufacturer },
            { key: 'score', header: 'Risk score', width: 176, render: (r) => <RiskScore score={r.riskScore} /> },
            {
              key: 'violations',
              header: 'Violations',
              align: 'center',
              width: 96,
              hideBelow: 'xl',
              render: (r) => (
                <span className="tabular font-medium text-ink-900">
                  {r.violationCount}
                  {r.openViolations > 0 && <span className="ml-1 text-xs font-normal text-danger-600">({r.openViolations} open)</span>}
                </span>
              ),
            },
            {
              key: 'last',
              header: 'Last inspection',
              hideBelow: 'xl',
              className: 'whitespace-nowrap',
              render: (r) => <span className="text-ink-600">{formatDate(r.lastInspection)}</span>,
            },
            { key: 'risk', header: 'Risk', render: (r) => <RiskBadge level={r.riskLevel} /> },
            {
              key: 'action',
              header: '',
              align: 'right',
              width: 92,
              render: (r) => (
                <Link
                  to={`/products/${r.id}`}
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
          label="products"
        />
      </section>
    </div>
  );
}
