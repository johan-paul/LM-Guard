import React, { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Download, ArrowRight } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import RiskBadge, { ConfidenceMeter } from '../components/RiskBadge';
import DataTable, { PrimaryCell, Pagination } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import { useViolations, useViolationTypes, usePagination, useDebounced } from '../hooks/useData';
import { formatDateTime, formatNumber } from '../utils/format';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_REVIEW', label: 'Under review' },
  { value: 'ESCALATED', label: 'Escalated' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'DISMISSED', label: 'Dismissed' },
];

const RISK_OPTIONS = [
  { value: 'ALL', label: 'All risk levels' },
  { value: 'HIGH', label: 'High risk' },
  { value: 'MEDIUM', label: 'Medium risk' },
  { value: 'LOW', label: 'Low risk' },
];

const DATE_OPTIONS = [
  { value: 'ALL', label: 'All time' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
];

export default function Violations() {
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [type, setType] = useState('ALL');
  const [risk, setRisk] = useState('ALL');
  const [since, setSince] = useState('ALL');

  const debouncedSearch = useDebounced(search);
  const { violations, loading } = useViolations({ search: debouncedSearch, status, type, risk, since });
  const violationTypes = useViolationTypes();
  const pager = usePagination(violations, 10);

  const counts = useMemo(() => {
    const open = violations.filter((v) => v.status === 'OPEN').length;
    const review = violations.filter((v) => v.status === 'UNDER_REVIEW').length;
    const escalated = violations.filter((v) => v.status === 'ESCALATED').length;
    const closed = violations.filter((v) => ['CONFIRMED', 'DISMISSED'].includes(v.status)).length;
    return { open, review, escalated, closed };
  }, [violations]);

  const reset = () => {
    setSearch('');
    setStatus('ALL');
    setType('ALL');
    setRisk('ALL');
    setSince('ALL');
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Violations"
        subtitle="Review and manage detected compliance issues. Every case carries the evidence and the rule behind it."
        actions={
          <button type="button" className="btn-secondary btn-lg">
            <Download className="h-4 w-4" strokeWidth={1.9} />
            Export case list
          </button>
        }
      />

      <div className="grid grid-cols-2 divide-line rounded-xl border border-line bg-surface shadow-card sm:grid-cols-4 sm:divide-x">
        <SummaryCell label="Open" value={counts.open} tone="text-brand-700" />
        <SummaryCell label="Under review" value={counts.review} tone="text-warning-700" />
        <SummaryCell label="Escalated" value={counts.escalated} tone="text-critical-600" />
        <SummaryCell label="Closed" value={counts.closed} tone="text-ink-900" />
      </div>

      <section className="panel-flush">
        <SearchFilterBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by case ID, product, rule or finding"
          onReset={reset}
          filters={[
            { key: 'status', label: 'Status', value: status, onChange: setStatus, options: STATUS_OPTIONS },
            {
              key: 'type',
              label: 'Violation type',
              value: type,
              onChange: setType,
              options: [{ value: 'ALL', label: 'All types' }, ...violationTypes.map((t) => ({ value: t, label: t }))],
            },
            { key: 'risk', label: 'Risk level', value: risk, onChange: setRisk, options: RISK_OPTIONS },
            { key: 'since', label: 'Date range', value: since, onChange: setSince, options: DATE_OPTIONS },
          ]}
        />

        <DataTable
          loading={loading}
          rows={pager.rows}
          onRowClick={(row) => navigate(`/violations/${row.id}`)}
          emptyTitle="No cases match these filters"
          emptyDescription="Clear the filters to see the full case queue."
          columns={[
            { key: 'id', header: 'Violation ID', width: 140, render: (r) => <span className="mono-id">{r.id}</span> },
            {
              key: 'product',
              header: 'Product',
              render: (r) => <PrimaryCell title={r.productName} subtitle={r.manufacturer} />,
            },
            {
              key: 'title',
              header: 'Violation',
              render: (r) => <PrimaryCell title={r.title} subtitle={r.type} />,
            },
            {
              key: 'rule',
              header: 'Rule',
              hideBelow: 'lg',
              width: 148,
              render: (r) => (
                <span className="block">
                  <span className="block font-mono text-[12px] text-ink-800">{r.ruleId}</span>
                  <span className="block truncate text-xs text-ink-500">{r.ruleCategory}</span>
                </span>
              ),
            },
            {
              key: 'confidence',
              header: 'Confidence',
              hideBelow: 'md',
              width: 132,
              render: (r) => <ConfidenceMeter value={r.confidence} />,
            },
            { key: 'risk', header: 'Risk', render: (r) => <RiskBadge level={r.riskLevel} /> },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            {
              key: 'detected',
              header: 'Detected',
              hideBelow: 'xl',
              className: 'whitespace-nowrap',
              render: (r) => <span className="text-ink-600">{formatDateTime(r.detectedAt)}</span>,
            },
            {
              key: 'action',
              header: '',
              align: 'right',
              width: 84,
              render: (r) => (
                <Link
                  to={`/violations/${r.id}`}
                  onClick={(e) => e.stopPropagation()}
                  className="inline-flex items-center gap-1 text-13 font-medium text-brand-600 hover:text-brand-700"
                >
                  Open <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
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
          label="cases"
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
