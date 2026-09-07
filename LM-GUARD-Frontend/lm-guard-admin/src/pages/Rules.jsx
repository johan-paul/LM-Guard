import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Scale, Info, ArrowRight, CircleDot } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import DataTable, { PrimaryCell } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import EmptyState from '../components/EmptyState';
import { useRules, useViolations, useDebounced } from '../hooks/useData';
import { formatDate } from '../utils/format';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'DEPRECATED', label: 'Deprecated' },
];

const SEVERITY_TONE = {
  CRITICAL: 'border-danger-100 bg-critical-50 text-critical-600',
  HIGH: 'border-danger-100 bg-danger-50 text-danger-700',
  MEDIUM: 'border-warning-100 bg-warning-50 text-warning-700',
  LOW: 'border-line bg-canvas text-ink-500',
};

export default function Rules() {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [selectedId, setSelectedId] = useState(params.get('rule'));

  const debouncedSearch = useDebounced(search);
  const { ruleset, categories, rules, loading } = useRules({ search: debouncedSearch, category, status });

  useEffect(() => {
    const q = params.get('rule');
    if (q) setSelectedId(q);
  }, [params]);

  const selected = useMemo(() => rules.find((r) => r.ruleId === selectedId) || rules[0], [rules, selectedId]);

  const { violations: allViolations } = useViolations();
  const relatedCases = useMemo(
    () => (selected ? allViolations.filter((v) => v.ruleId === selected.ruleId) : []),
    [selected, allViolations],
  );

  const select = (ruleId) => {
    setSelectedId(ruleId);
    setParams({ rule: ruleId }, { replace: true });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Rules & Compliance Engine"
        subtitle="The deterministic ruleset applied to every extracted declaration. Rules decide compliance; the engine never infers it."
      />

      {/* Ruleset status band */}
      <section className="grid grid-cols-2 divide-line rounded-xl border border-line bg-surface shadow-card lg:grid-cols-5 lg:divide-x">
        <div className="px-5 py-4">
          <p className="eyebrow">Ruleset version</p>
          <p className="mt-1.5 font-mono text-[17px] font-semibold text-ink-900">{ruleset?.version || '—'}</p>
        </div>
        <div className="px-5 py-4">
          <p className="eyebrow">Status</p>
          <p className="mt-2">
            <StatusBadge status={ruleset?.status} size="lg" />
          </p>
        </div>
        <div className="px-5 py-4">
          <p className="eyebrow">Effective from</p>
          <p className="mt-1.5 text-13 font-medium text-ink-900">{formatDate(ruleset?.effectiveFrom)}</p>
        </div>
        <div className="px-5 py-4">
          <p className="eyebrow">Last updated</p>
          <p className="mt-1.5 text-13 font-medium text-ink-900">{formatDate(ruleset?.lastUpdated)}</p>
        </div>
        <div className="px-5 py-4">
          <p className="eyebrow">Rules in registry</p>
          <p className="tabular mt-1.5 text-13 font-medium text-ink-900">
            {ruleset?.activeRules} active
            <span className="text-ink-400"> / {ruleset?.totalRules} total</span>
          </p>
        </div>
      </section>

      {/* Demonstration ruleset notice */}
      <div className="flex items-start gap-3 rounded-xl border border-warning-100 bg-warning-50 px-5 py-3.5">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-warning-700" strokeWidth={2} />
        <p className="text-13 text-warning-700">
          <span className="font-semibold">Demonstration ruleset.</span> These rules were authored for the LM-GUARD
          prototype to illustrate engine behaviour. They are not an official or complete reproduction of any statutory
          instrument and must not be relied on for enforcement.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        {/* Registry */}
        <section className="panel-flush xl:col-span-7">
          <SearchFilterBar
            search={search}
            onSearchChange={setSearch}
            searchPlaceholder="Search rules by ID, name or condition"
            onReset={() => {
              setSearch('');
              setCategory('ALL');
              setStatus('ALL');
            }}
            filters={[
              {
                key: 'category',
                label: 'Category',
                value: category,
                onChange: setCategory,
                options: [{ value: 'ALL', label: 'All categories' }, ...categories.map((c) => ({ value: c, label: c }))],
              },
              { key: 'status', label: 'Status', value: status, onChange: setStatus, options: STATUS_OPTIONS },
            ]}
          />

          <DataTable
            loading={loading}
            rows={rules}
            keyField="ruleId"
            onRowClick={(row) => select(row.ruleId)}
            emptyTitle="No rules match this search"
            columns={[
              {
                key: 'ruleId',
                header: 'Rule',
                render: (r) => (
                  <span className="flex items-center gap-2.5">
                    <CircleDot
                      className={`h-3 w-3 shrink-0 ${
                        selected?.ruleId === r.ruleId ? 'text-brand-600' : 'text-transparent'
                      }`}
                      strokeWidth={2.5}
                    />
                    <PrimaryCell title={r.ruleId} subtitle={r.name} mono />
                  </span>
                ),
              },
              {
                key: 'category',
                header: 'Category',
                hideBelow: 'lg',
                className: 'whitespace-nowrap',
                render: (r) => r.category,
              },
              {
                key: 'severity',
                header: 'Severity',
                hideBelow: 'md',
                width: 108,
                render: (r) => <span className={`badge ${SEVERITY_TONE[r.severity]}`}>{r.severity}</span>,
              },
              { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              {
                key: 'version',
                header: 'Version',
                hideBelow: 'xl',
                width: 108,
                render: (r) => <span className="font-mono text-[12px] text-ink-600">{r.version}</span>,
              },
            ]}
          />
        </section>

        {/* Rule detail */}
        <div className="xl:col-span-5">
          {selected ? (
            <section className="panel-flush xl:sticky xl:top-[76px]">
              <div className="panel-header">
                <div className="min-w-0">
                  <p className="font-mono text-13 font-medium text-ink-900">{selected.ruleId}</p>
                  <h2 className="panel-title mt-0.5">{selected.name}</h2>
                </div>
                <StatusBadge status={selected.status} />
              </div>

              <dl className="grid grid-cols-2 gap-px bg-line">
                <Cell label="Category" value={selected.category} />
                <Cell label="Version" value={selected.version} mono />
                <Cell label="Severity" value={selected.severity} />
                <Cell label="Declaration field" value={selected.field} mono />
              </dl>

              <div className="px-5 py-4">
                <p className="eyebrow mb-2">Requirement</p>
                <p className="text-13 leading-relaxed text-ink-700">{selected.summary}</p>

                <p className="eyebrow mb-2 mt-5">Applies to</p>
                <p className="text-13 text-ink-700">{selected.appliesTo}</p>

                <p className="eyebrow mb-2 mt-5">Evaluation logic</p>
                <div className="rounded-lg border border-line bg-navy-950 px-4 py-3">
                  <code className="block whitespace-pre-wrap break-words font-mono text-[12px] leading-relaxed text-brand-200">
                    {selected.logic}
                  </code>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-px border-t border-line bg-line">
                <Cell label="Times triggered" value={String(selected.triggeredCount)} />
                <Cell label="Last triggered" value={selected.lastTriggered ? formatDate(selected.lastTriggered) : 'Never'} />
              </div>

              {relatedCases.length > 0 && (
                <>
                  <div className="border-t border-line px-5 py-2.5">
                    <p className="eyebrow">Open cases citing this rule</p>
                  </div>
                  <ul className="divide-y divide-line-soft">
                    {relatedCases.slice(0, 4).map((c) => (
                      <li key={c.id}>
                        <Link to={`/violations/${c.id}`} className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-canvas">
                          <span className="mono-id shrink-0">{c.id}</span>
                          <span className="min-w-0 flex-1 truncate text-13 text-ink-700">{c.productName}</span>
                          <ArrowRight className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={2} />
                        </Link>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </section>
          ) : (
            <section className="panel">
              <EmptyState icon={Scale} title="Select a rule" description="Choose a rule from the registry to inspect its logic." />
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

function Cell({ label, value, mono = false }) {
  return (
    <div className="bg-surface px-5 py-3.5">
      <dt className="text-micro font-semibold uppercase text-ink-400">{label}</dt>
      <dd className={`mt-1 text-13 text-ink-900 ${mono ? 'font-mono' : 'font-medium'}`}>{value}</dd>
    </div>
  );
}
