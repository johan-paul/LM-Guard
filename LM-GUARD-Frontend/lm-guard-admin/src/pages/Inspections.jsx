import React, { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Download, ArrowRight, Plus } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import { RiskScore } from '../components/RiskBadge';
import DataTable, { PrimaryCell, Pagination } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import { useInspections, useReferenceData, usePagination, useDebounced } from '../hooks/useData';
import { formatDateTime, formatNumber } from '../utils/format';
import { downloadCsv } from '../utils/csvExport';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'COMPLIANT', label: 'Compliant' },
  { value: 'REVIEW_REQUIRED', label: 'Review' },
  { value: 'NON_COMPLIANT', label: 'Violation' },
  { value: 'INCONCLUSIVE', label: 'Inconclusive' },
];

const RISK_OPTIONS = [
  { value: 'ALL', label: 'All risk levels' },
  { value: 'HIGH', label: 'High risk' },
  { value: 'MEDIUM', label: 'Medium risk' },
  { value: 'LOW', label: 'Low risk' },
];

const SORT_OPTIONS = [
  { value: 'recent', label: 'Most recent' },
  { value: 'risk', label: 'Highest risk first' },
];

export default function Inspections() {
  const navigate = useNavigate();
  const { reference } = useReferenceData();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [category, setCategory] = useState('ALL');
  const [inspector, setInspector] = useState('ALL');
  const [risk, setRisk] = useState('ALL');
  const [sort, setSort] = useState('recent');

  const debouncedSearch = useDebounced(search);
  const { inspections, loading } = useInspections({ search: debouncedSearch, status, category, inspector, risk, sort });
  const pager = usePagination(inspections, 10);

  const counts = useMemo(() => {
    const base = { COMPLIANT: 0, REVIEW_REQUIRED: 0, NON_COMPLIANT: 0, INCONCLUSIVE: 0 };
    inspections.forEach((i) => {
      base[i.status] = (base[i.status] || 0) + 1;
    });
    return base;
  }, [inspections]);

  const reset = () => {
    setSearch('');
    setStatus('ALL');
    setCategory('ALL');
    setInspector('ALL');
    setRisk('ALL');
    setSort('recent');
  };

  const exportCsv = () => {
    downloadCsv(
      `inspections-${new Date().toISOString().slice(0, 10)}.csv`,
      [
        { header: 'Inspection ID', value: (r) => r.id },
        { header: 'Product', value: (r) => r.productName },
        { header: 'Manufacturer', value: (r) => r.manufacturer },
        { header: 'Category', value: (r) => r.category },
        { header: 'Inspector', value: (r) => r.inspector },
        { header: 'Zone', value: (r) => r.zone },
        { header: 'Status', value: (r) => r.status },
        { header: 'Risk score', value: (r) => r.riskScore },
        { header: 'Date', value: (r) => formatDateTime(r.date) },
      ],
      inspections,
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inspections"
        subtitle="Monitor and review all package inspections carried out across the jurisdiction."
        actions={
          <>
            <button type="button" onClick={exportCsv} disabled={!inspections.length} className="btn-secondary btn-lg">
              <Download className="h-4 w-4" strokeWidth={1.9} />
              Export
            </button>
            <button type="button" onClick={() => navigate('/new-inspection')} className="btn-primary btn-lg">
              <Plus className="h-4 w-4" strokeWidth={2} />
              New Inspection
            </button>
          </>
        }
      />

      {/* Result summary */}
      <div className="grid grid-cols-2 divide-line rounded-xl border border-line bg-surface shadow-card sm:grid-cols-4 sm:divide-x">
        <SummaryCell label="Records in view" value={inspections.length} tone="text-ink-900" />
        <SummaryCell label="Compliant" value={counts.COMPLIANT} tone="text-success-700" />
        <SummaryCell label="Awaiting review" value={counts.REVIEW_REQUIRED + counts.INCONCLUSIVE} tone="text-warning-700" />
        <SummaryCell label="Violations" value={counts.NON_COMPLIANT} tone="text-danger-600" />
      </div>

      <section className="panel-flush">
        <SearchFilterBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by ID, product, manufacturer or inspector"
          onReset={reset}
          filters={[
            { key: 'status', label: 'Status', value: status, onChange: setStatus, options: STATUS_OPTIONS },
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
              key: 'inspector',
              label: 'Inspector',
              value: inspector,
              onChange: setInspector,
              options: [
                { value: 'ALL', label: 'All inspectors' },
                ...(reference?.inspectors || []).map((i) => ({ value: i.name, label: i.name })),
              ],
            },
            { key: 'risk', label: 'Risk', value: risk, onChange: setRisk, options: RISK_OPTIONS },
            { key: 'sort', label: 'Sort', value: sort, onChange: setSort, options: SORT_OPTIONS },
          ]}
        />

        <DataTable
          loading={loading}
          rows={pager.rows}
          onRowClick={(row) => navigate(`/inspections/${row.id}`)}
          emptyTitle="No inspections match these filters"
          emptyDescription="Clear the filters or widen the date range to see more records."
          columns={[
            { key: 'id', header: 'Inspection ID', width: 148, render: (r) => <span className="mono-id">{r.id}</span> },
            {
              key: 'product',
              header: 'Product',
              render: (r) => <PrimaryCell title={r.productName} subtitle={r.manufacturer} />,
            },
            { key: 'category', header: 'Category', hideBelow: 'xl', render: (r) => r.category },
            {
              key: 'inspector',
              header: 'Inspector',
              hideBelow: 'lg',
              render: (r) => <PrimaryCell title={r.inspector} subtitle={r.zone} />,
            },
            { key: 'status', header: 'Compliance', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'risk', header: 'Risk score', width: 176, hideBelow: 'md', render: (r) => <RiskScore score={r.riskScore} /> },
            {
              key: 'date',
              header: 'Date',
              hideBelow: 'xl',
              className: 'whitespace-nowrap',
              render: (r) => <span className="text-ink-600">{formatDateTime(r.date)}</span>,
            },
            {
              key: 'action',
              header: '',
              align: 'right',
              width: 84,
              render: (r) => (
                <Link
                  to={`/inspections/${r.id}`}
                  onClick={(e) => e.stopPropagation()}
                  className="inline-flex items-center gap-1 text-13 font-medium text-brand-600 hover:text-brand-700"
                >
                  View <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
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
          label="inspections"
        />
      </section>
    </div>
  );
}

function SummaryCell({ label, value, tone }) {
  return (
    <div className="px-5 py-4">
      <p className="eyebrow">{label}</p>
      <p className={`tabular mt-1.5 text-[22px] font-bold leading-7 ${tone}`}>{formatNumber(value)}</p>
    </div>
  );
}
