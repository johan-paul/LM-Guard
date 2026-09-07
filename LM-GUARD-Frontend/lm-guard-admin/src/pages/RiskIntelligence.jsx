import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Building2, CalendarPlus, Download, Radar } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import RiskBadge, { RiskScore } from '../components/RiskBadge';
import DataTable, { PrimaryCell } from '../components/DataTable';
import { MiniStat } from '../components/StatCard';
import { CardSkeleton } from '../components/LoadingState';
import { useRiskQueue, useRepeatOffenders, useRiskFactors, useStats } from '../hooks/useData';
import { formatDate, formatNumber, plural } from '../utils/format';

export default function RiskIntelligence() {
  const navigate = useNavigate();
  const { stats, loading: statsLoading } = useStats();
  const { queue, loading: queueLoading } = useRiskQueue();
  const { offenders } = useRepeatOffenders();
  const { factors } = useRiskFactors();

  const distribution = stats?.riskDistribution || [];
  const total = distribution.reduce((s, d) => s + d.count, 0) || 1;
  const barColor = { critical: 'bg-critical-600', warning: 'bg-warning-600', success: 'bg-success-600' };
  const textColor = { critical: 'critical', warning: 'warning', success: 'success' };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Risk Intelligence"
        subtitle="Prioritise inspections using compliance history, package changes and detected anomalies."
        actions={
          <button type="button" className="btn-secondary btn-lg">
            <Download className="h-4 w-4" strokeWidth={1.9} />
            Export queue
          </button>
        }
      />

      {/* Risk overview */}
      {statsLoading ? (
        <CardSkeleton height="h-[168px]" />
      ) : (
        <section className="panel-flush">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Risk overview</h2>
              <p className="panel-subtitle">Composite scoring across {formatNumber(total)} monitored products</p>
            </div>
            <span className="badge border-line bg-canvas text-ink-500">Thresholds · High ≥ 70 · Medium ≥ 40</span>
          </div>

          <div className="grid grid-cols-1 divide-y divide-line sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            {distribution.map((d) => (
              <div key={d.level} className="px-5 py-4">
                <MiniStat
                  label={d.label}
                  value={d.count}
                  tone={textColor[d.tone]}
                  caption={`${Math.round((d.count / total) * 100)}% of monitored products`}
                />
              </div>
            ))}
          </div>

          {/* Composition bar */}
          <div className="border-t border-line px-5 py-4">
            <div className="flex h-2 w-full overflow-hidden rounded-full bg-line">
              {distribution.map((d) => (
                <span
                  key={d.level}
                  className={barColor[d.tone]}
                  style={{ width: `${(d.count / total) * 100}%` }}
                  title={`${d.label}: ${d.count}`}
                />
              ))}
            </div>
          </div>
        </section>
      )}

      <div className="grid grid-cols-1 gap-5">
        {/* Priority queue */}
        <section className="panel-flush">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Risk priority queue</h2>
              <p className="panel-subtitle">Ranked products awaiting inspection attention</p>
            </div>
            <span className="tabular text-13 text-ink-500">{queue.length} products</span>
          </div>

          <DataTable
            loading={queueLoading}
            rows={queue}
            keyField="productId"
            onRowClick={(row) => navigate(`/products/${row.productId}`)}
            emptyTitle="Queue is clear"
            emptyDescription="No products currently exceed the review threshold."
            columns={[
              {
                key: 'rank',
                header: '#',
                width: 52,
                render: (r) => <span className="tabular font-mono text-13 text-ink-400">{String(r.rank).padStart(2, '0')}</span>,
              },
              {
                key: 'product',
                header: 'Product',
                render: (r) => <PrimaryCell title={r.shortName} subtitle={r.manufacturer} />,
              },
              { key: 'score', header: 'Risk score', width: 176, render: (r) => <RiskScore score={r.riskScore} /> },
              {
                key: 'factors',
                header: 'Risk factors',
                hideBelow: 'lg',
                render: (r) => (
                  <span className="flex flex-wrap gap-1.5">
                    {r.riskFactors.slice(0, 2).map((f) => (
                      <span key={f} className="tag">
                        {f}
                      </span>
                    ))}
                    {r.riskFactors.length > 2 && <span className="tag">+{r.riskFactors.length - 2}</span>}
                  </span>
                ),
              },
              {
                key: 'violations',
                header: 'Previous',
                align: 'center',
                hideBelow: 'md',
                width: 88,
                render: (r) => <span className="tabular font-medium text-ink-900">{r.previousViolations}</span>,
              },
              {
                key: 'last',
                header: 'Last inspection',
                hideBelow: 'xl',
                className: 'whitespace-nowrap',
                render: (r) => <span className="text-ink-600">{formatDate(r.lastInspection)}</span>,
              },
              {
                key: 'action',
                header: '',
                align: 'right',
                width: 210,
                render: (r) => (
                  <span className="flex items-center justify-end gap-3">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate('/new-inspection', {
                          state: {
                            prefillFromRisk: {
                              productId: r.productId,
                              productName: r.product,
                              riskScore: r.riskScore,
                              establishment: r.establishment,
                              address: r.address,
                            },
                          },
                        });
                      }}
                      className="inline-flex items-center gap-1 text-13 font-medium text-brand-600 hover:text-brand-700"
                      title="Open a follow-up inspection for this product, pre-filled from this row"
                    >
                      <CalendarPlus className="h-3.5 w-3.5" strokeWidth={2} /> Follow up
                    </button>
                    <Link
                      to={`/products/${r.productId}`}
                      onClick={(e) => e.stopPropagation()}
                      className="inline-flex items-center gap-1 text-13 font-medium text-ink-500 hover:text-ink-900"
                    >
                      Profile <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
                    </Link>
                  </span>
                ),
              },
            ]}
          />
        </section>

        <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
          {/* Repeat offenders */}
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Repeat offenders</h2>
                <p className="panel-subtitle">Manufacturers with sustained violation history</p>
              </div>
            </div>
            <ul className="divide-y divide-line-soft">
              {offenders.map((o) => (
                <li key={o.id} className="flex items-start gap-3 px-5 py-3.5">
                  <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-canvas text-ink-500">
                    <Building2 className="h-4 w-4" strokeWidth={1.9} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-13 font-medium text-ink-900">{o.name}</p>
                    <p className="truncate text-xs text-ink-500">
                      {o.zone} · {plural(o.trackedProducts, 'product')} tracked
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="tabular text-13 font-semibold text-ink-900">{o.totalViolations}</p>
                    <p className="text-[11px] text-ink-400">violations</p>
                  </div>
                </li>
              ))}
            </ul>
          </section>

          {/* Scoring model */}
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Scoring contributors</h2>
                <p className="panel-subtitle">Weighting applied to the composite score</p>
              </div>
            </div>
            <ul className="px-5 py-4">
              {factors.map((f) => (
                <li key={f.factor} className="mb-3.5 last:mb-0">
                  <div className="mb-1.5 flex items-baseline justify-between gap-3">
                    <span className="text-13 text-ink-700">{f.factor}</span>
                    <span className="tabular text-xs font-semibold text-ink-900">{f.weight}%</span>
                  </div>
                  <div className="h-1 w-full overflow-hidden rounded-full bg-line">
                    <div
                      className="h-full rounded-full bg-navy-700"
                      style={{ width: `${(f.weight / Math.max(...factors.map((x) => x.weight), 1)) * 100}%` }}
                    />
                  </div>
                  <p className="mt-1 text-[11px] text-ink-400">{f.occurrences} occurrences on record</p>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <div className="flex items-start gap-3 rounded-xl border border-line bg-surface px-5 py-4">
          <Radar className="mt-0.5 h-4 w-4 shrink-0 text-ink-400" strokeWidth={1.9} />
          <p className="text-13 leading-relaxed text-ink-500">
            Risk scores prioritise inspector attention. They are not a finding of non-compliance and carry no
            enforcement weight on their own.
          </p>
        </div>
      </div>
    </div>
  );
}
