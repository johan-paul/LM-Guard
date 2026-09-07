import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ComposedChart,
  Area,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { ClipboardList, AlertOctagon, Radar, Building2, Download, ArrowRight, ShieldAlert } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import RiskBadge, { RiskScore } from '../components/RiskBadge';
import DataTable, { PrimaryCell } from '../components/DataTable';
import ChartCard, { CHART, LegendItem, ChartTooltip } from '../components/ChartCard';
import { StatGridSkeleton } from '../components/LoadingState';
import { useStats, useInspections, useRiskQueue, useAnalytics } from '../hooks/useData';
import { formatDateTime, formatDate, formatNumber, greeting } from '../utils/format';
import { useAuth } from '../context/AuthContext';

const DASHBOARD_TREND_RANGES = [
  { key: '7d', label: 'Last 7 days' },
  { key: '30d', label: 'Last 30 days' },
  { key: '90d', label: 'Last 90 days' },
];

export default function Dashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [range, setRange] = useState('30d');

  const { stats, loading: statsLoading } = useStats();
  const { inspections, loading: inspectionsLoading } = useInspections({});
  const { queue, loading: queueLoading } = useRiskQueue(5);
  const { analytics } = useAnalytics();

  const series = analytics?.trends?.[range] || [];
  const summaryTotals = series.reduce(
    (acc, r) => ({ inspections: acc.inspections + r.inspections, violations: acc.violations + r.violations }),
    { inspections: 0, violations: 0 },
  );
  const half = Math.floor(series.length / 2) || 1;
  const firstHalf = series.slice(0, half).reduce((s, r) => s + r.inspections, 0) || 1;
  const secondHalf = series.slice(half).reduce((s, r) => s + r.inspections, 0);
  const summary = {
    ...summaryTotals,
    complianceRate:
      summaryTotals.inspections === 0
        ? 0
        : Math.round(((summaryTotals.inspections - summaryTotals.violations) / summaryTotals.inspections) * 1000) / 10,
    momentum: Math.round(((secondHalf - firstHalf) / firstHalf) * 1000) / 10,
  };
  const distribution = stats?.riskDistribution || [];
  const distributionTotal = distribution.reduce((s, d) => s + d.count, 0);
  const donutColors = { critical: CHART.critical, warning: CHART.warning, success: CHART.success };

  return (
    <div className="space-y-6">
      <PageHeader
        title={`${greeting()}, ${user?.name?.split(' ')[0] || 'Officer'}`}
        subtitle="Here's the latest Legal Metrology compliance overview across your jurisdiction."
        actions={
          <>
            <label className="relative">
              <span className="sr-only">Date range</span>
              <select value={range} onChange={(e) => setRange(e.target.value)} className="select input-lg w-auto">
                {DASHBOARD_TREND_RANGES.map((r) => (
                  <option key={r.key} value={r.key}>
                    {r.label}
                  </option>
                ))}
              </select>
            </label>
            <button type="button" className="btn-secondary btn-lg">
              <Download className="h-4 w-4" strokeWidth={1.9} />
              Export
            </button>
          </>
        }
      />

      {/* KPI row */}
      {statsLoading ? (
        <StatGridSkeleton />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Total inspections"
            value={stats.totalInspections}
            delta={stats.totalInspectionsDelta}
            deltaIntent="positive"
            description="from last month"
            icon={ClipboardList}
            to="/inspections"
          />
          <StatCard
            label="Potential violations"
            value={stats.potentialViolations}
            delta={stats.potentialViolationsDelta}
            deltaIntent="negative"
            description="from last month"
            icon={AlertOctagon}
            accent="warning"
            to="/violations"
          />
          <StatCard
            label="High risk products"
            value={stats.highRiskProducts}
            description="Requires attention"
            icon={Radar}
            accent="critical"
            to="/risk"
          />
          <StatCard
            label="Repeat offenders"
            value={stats.repeatOffenders}
            description="Across manufacturers and sellers"
            icon={Building2}
            to="/risk"
          />
        </div>
      )}

      {/* Intelligence grid */}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        <ChartCard
          className="xl:col-span-8"
          title="Compliance trend"
          subtitle={`Inspections against violations detected · ${DASHBOARD_TREND_RANGES.find((r) => r.key === range)?.label.toLowerCase()}`}
          height={300}
          actions={
            <span
              className={`badge ${
                Math.abs(summary.momentum) < 0.5
                  ? 'border-line bg-canvas text-ink-500'
                  : summary.momentum > 0
                    ? 'border-brand-100 bg-brand-50 text-brand-700'
                    : 'border-warning-100 bg-warning-50 text-warning-700'
              }`}
            >
              {Math.abs(summary.momentum) < 0.5
                ? 'Steady volume'
                : `${summary.momentum > 0 ? '+' : ''}${summary.momentum}% volume`}
            </span>
          }
          legend={
            <>
              <LegendItem color={CHART.primary} label="Inspections" value={formatNumber(summary.inspections)} />
              <LegendItem color={CHART.danger} label="Violations" value={formatNumber(summary.violations)} />
              <LegendItem color="#B4BAC6" label="Compliance rate (right axis)" value={`${summary.complianceRate}%`} dashed />
            </>
          }
        >
          <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={series} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <defs>
                <linearGradient id="inspectionsFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={CHART.primary} stopOpacity={0.16} />
                  <stop offset="100%" stopColor={CHART.primary} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={CHART.grid} vertical={false} />
              <XAxis
                dataKey="label"
                tickLine={false}
                axisLine={{ stroke: CHART.grid }}
                interval={range === '90d' ? 12 : range === '30d' ? 4 : 0}
                tickMargin={10}
              />
              <YAxis yAxisId="left" tickLine={false} axisLine={false} width={44} tickMargin={4} />
              <YAxis
                yAxisId="right"
                orientation="right"
                domain={[50, 100]}
                ticks={[50, 75, 100]}
                tickFormatter={(v) => `${v}%`}
                tickLine={false}
                axisLine={false}
                width={40}
                tickMargin={4}
              />
              <Tooltip
                content={<ChartTooltip formatter={(v, key) => (key === 'complianceRate' ? `${v}%` : v)} />}
                cursor={{ stroke: CHART.axis, strokeDasharray: '3 3', strokeOpacity: 0.4 }}
              />
              <Area
                yAxisId="left"
                type="monotone"
                dataKey="inspections"
                name="Inspections"
                stroke={CHART.primary}
                strokeWidth={2}
                fill="url(#inspectionsFill)"
                dot={false}
                activeDot={{ r: 3.5, strokeWidth: 0 }}
              />
              <Line
                yAxisId="left"
                type="monotone"
                dataKey="violations"
                name="Violations"
                stroke={CHART.danger}
                strokeWidth={1.75}
                dot={false}
                activeDot={{ r: 3.5, strokeWidth: 0 }}
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="complianceRate"
                name="Compliance rate"
                stroke="#B4BAC6"
                strokeWidth={1.25}
                strokeDasharray="4 4"
                dot={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </ChartCard>

        {/* Risk distribution */}
        <section className="panel-flush xl:col-span-4">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Risk distribution</h2>
              <p className="panel-subtitle">Products under active monitoring</p>
            </div>
          </div>

          <div className="relative px-4 pt-5">
            <ResponsiveContainer width="100%" height={196}>
              <PieChart>
                <Pie
                  data={distribution}
                  dataKey="count"
                  nameKey="label"
                  innerRadius={62}
                  outerRadius={86}
                  paddingAngle={2}
                  stroke="none"
                  startAngle={90}
                  endAngle={-270}
                >
                  {distribution.map((d) => (
                    <Cell key={d.level} fill={donutColors[d.tone]} />
                  ))}
                </Pie>
                <Tooltip content={<ChartTooltip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-x-0 top-5 flex h-[196px] flex-col items-center justify-center">
              <span className="tabular text-[26px] font-bold leading-7 text-ink-900">
                {formatNumber(distributionTotal)}
              </span>
              <span className="text-[11px] text-ink-500">products tracked</span>
            </div>
          </div>

          <ul className="mt-2 divide-y divide-line-soft border-t border-line">
            {distribution.map((d) => (
              <li key={d.level} className="flex items-center gap-3 px-5 py-2.5">
                <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: donutColors[d.tone] }} />
                <span className="flex-1 text-13 text-ink-700">{d.label}</span>
                <span className="tabular text-13 font-semibold text-ink-900">{formatNumber(d.count)}</span>
                <span className="tabular w-11 text-right text-xs text-ink-400">
                  {Math.round((d.count / distributionTotal) * 100)}%
                </span>
              </li>
            ))}
          </ul>

          <Link
            to="/risk"
            className="flex items-center justify-center gap-1.5 border-t border-line px-5 py-3 text-13 font-medium text-brand-600 transition-colors hover:bg-canvas"
          >
            Open risk intelligence <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
          </Link>
        </section>
      </div>

      {/* High priority products */}
      <section className="panel-flush">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">High priority products</h2>
            <p className="panel-subtitle">Ranked by composite risk score, compliance history and detected anomalies</p>
          </div>
          <Link to="/risk" className="btn-secondary btn-sm">
            Full queue <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
          </Link>
        </div>

        <DataTable
          loading={queueLoading}
          rows={queue}
          keyField="productId"
          onRowClick={(row) => navigate(`/products/${row.productId}`)}
          emptyTitle="No products in the priority queue"
          columns={[
            {
              key: 'product',
              header: 'Product',
              render: (r) => <PrimaryCell title={r.shortName} subtitle={r.productId} />,
            },
            { key: 'manufacturer', header: 'Manufacturer', hideBelow: 'md', render: (r) => r.manufacturer },
            { key: 'risk', header: 'Risk score', width: 180, render: (r) => <RiskScore score={r.riskScore} /> },
            {
              key: 'issue',
              header: 'Primary issue',
              hideBelow: 'lg',
              render: (r) => <span className="text-ink-700">{r.primaryIssue}</span>,
            },
            {
              key: 'last',
              header: 'Last inspection',
              hideBelow: 'xl',
              className: 'whitespace-nowrap',
              render: (r) => <span className="text-ink-600">{formatDate(r.lastInspection)}</span>,
            },
            {
              key: 'status',
              header: 'Status',
              align: 'right',
              render: (r) => <RiskBadge level={r.riskLevel} />,
            },
          ]}
        />
      </section>

      {/* Recent inspections */}
      <section className="panel-flush">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Recent inspections</h2>
            <p className="panel-subtitle">Latest records submitted across all zones</p>
          </div>
          <Link to="/inspections" className="btn-secondary btn-sm">
            View all <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
          </Link>
        </div>

        <DataTable
          loading={inspectionsLoading}
          rows={inspections.slice(0, 6)}
          onRowClick={(row) => navigate(`/inspections/${row.id}`)}
          emptyTitle="No inspections recorded yet"
          emptyDescription="Records appear here as inspecting officers submit them from the field."
          columns={[
            { key: 'id', header: 'Inspection ID', width: 148, render: (r) => <span className="mono-id">{r.id}</span> },
            {
              key: 'product',
              header: 'Product',
              render: (r) => <PrimaryCell title={r.productName} subtitle={r.manufacturer} />,
            },
            { key: 'inspector', header: 'Inspector', hideBelow: 'lg', render: (r) => r.inspector },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            {
              key: 'risk',
              header: 'Risk',
              hideBelow: 'md',
              render: (r) => <RiskBadge score={r.riskScore} />,
            },
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
              width: 90,
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
      </section>

      {/* Operating principle */}
      <div className="flex items-start gap-3 rounded-xl border border-line bg-surface px-5 py-4">
        <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-ink-400" strokeWidth={1.9} />
        <p className="text-13 text-ink-500">
          <span className="font-medium text-ink-800">AI observes. Rules validate. Humans decide.</span> Findings shown
          here are produced by OCR extraction and a deterministic rule engine against demonstration ruleset LMPC 2026.1.
          No enforcement action is taken without an inspecting officer's decision.
        </p>
      </div>
    </div>
  );
}
