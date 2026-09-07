import React, { useState } from 'react';
import {
  ComposedChart,
  Area,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
} from 'recharts';
import { Download } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import ChartCard, { CHART, LegendItem, ChartTooltip } from '../components/ChartCard';
import { SegmentedControl } from '../components/SearchFilterBar';
import { CardSkeleton } from '../components/LoadingState';
import { useAnalytics } from '../hooks/useData';
import { formatNumber } from '../utils/format';

export default function Analytics() {
  const { analytics, loading } = useAnalytics();
  const [range, setRange] = useState('30d');

  if (loading || !analytics) {
    return (
      <div className="space-y-6">
        <PageHeader title="Analytics" subtitle="Programme-level compliance insight across zones, categories and manufacturers." />
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
          <div className="xl:col-span-8">
            <CardSkeleton height="h-[360px]" />
          </div>
          <div className="xl:col-span-4">
            <CardSkeleton height="h-[360px]" />
          </div>
        </div>
      </div>
    );
  }

  const series = analytics.trends[range];
  // Computed from the same real per-day series the chart renders - never a separate mock summary.
  const summaryTotals = series.reduce(
    (acc, r) => ({ inspections: acc.inspections + r.inspections, violations: acc.violations + r.violations }),
    { inspections: 0, violations: 0 },
  );
  const summary = {
    ...summaryTotals,
    complianceRate:
      summaryTotals.inspections === 0
        ? 0
        : Math.round(((summaryTotals.inspections - summaryTotals.violations) / summaryTotals.inspections) * 1000) / 10,
  };
  const donutColors = { critical: CHART.critical, warning: CHART.warning, success: CHART.success };
  const distributionTotal = analytics.riskDistribution.reduce((s, d) => s + d.count, 0);
  const maxRegional = Math.max(...analytics.regional.map((r) => r.inspections));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        subtitle="Programme-level compliance insight across zones, categories and manufacturers."
        actions={
          <>
            <SegmentedControl
              options={analytics.ranges.map((r) => ({ value: r.key, label: r.label.replace('Last ', '') }))}
              value={range}
              onChange={setRange}
              size="md"
            />
            <button type="button" className="btn-secondary btn-lg">
              <Download className="h-4 w-4" strokeWidth={1.9} /> Export
            </button>
          </>
        }
      />

      {/* Summary strip */}
      <section className="grid grid-cols-2 divide-line rounded-xl border border-line bg-surface shadow-card lg:grid-cols-4 lg:divide-x">
        <Summary label="Inspections in range" value={formatNumber(summary.inspections)} />
        <Summary label="Violations detected" value={formatNumber(summary.violations)} tone="text-danger-600" />
        <Summary label="Compliance rate" value={`${summary.complianceRate}%`} tone="text-success-700" />
        <Summary label="Avg. days to resolve" value={analytics.totals.avgResolutionDays.toFixed(1)} />
      </section>

      {/* Trends + risk distribution */}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        <ChartCard
          className="xl:col-span-8"
          title="Inspection trends"
          subtitle="Volume of inspections against violations detected"
          height={300}
          legend={
            <>
              <LegendItem color={CHART.primary} label="Inspections" />
              <LegendItem color={CHART.danger} label="Violations" />
            </>
          }
        >
          <ResponsiveContainer width="100%" height={300}>
            <ComposedChart data={series} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <defs>
                <linearGradient id="analyticsFill" x1="0" y1="0" x2="0" y2="1">
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
              <YAxis tickLine={false} axisLine={false} width={44} tickMargin={4} />
              <Tooltip content={<ChartTooltip />} cursor={{ stroke: CHART.axis, strokeDasharray: '3 3', strokeOpacity: 0.4 }} />
              <Area
                type="monotone"
                dataKey="inspections"
                name="Inspections"
                stroke={CHART.primary}
                strokeWidth={2}
                fill="url(#analyticsFill)"
                dot={false}
                activeDot={{ r: 3.5, strokeWidth: 0 }}
              />
              <Line type="monotone" dataKey="violations" name="Violations" stroke={CHART.danger} strokeWidth={1.75} dot={false} />
            </ComposedChart>
          </ResponsiveContainer>
        </ChartCard>

        <section className="panel-flush xl:col-span-4">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Risk distribution</h2>
              <p className="panel-subtitle">Monitored products by composite score</p>
            </div>
          </div>
          <div className="relative px-4 pt-5">
            <ResponsiveContainer width="100%" height={190}>
              <PieChart>
                <Pie
                  data={analytics.riskDistribution}
                  dataKey="count"
                  nameKey="label"
                  innerRadius={60}
                  outerRadius={84}
                  paddingAngle={2}
                  stroke="none"
                  startAngle={90}
                  endAngle={-270}
                >
                  {analytics.riskDistribution.map((d) => (
                    <Cell key={d.level} fill={donutColors[d.tone]} />
                  ))}
                </Pie>
                <Tooltip content={<ChartTooltip />} />
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-x-0 top-5 flex h-[190px] flex-col items-center justify-center">
              <span className="tabular text-[26px] font-bold leading-7 text-ink-900">{formatNumber(distributionTotal)}</span>
              <span className="text-[11px] text-ink-500">products</span>
            </div>
          </div>
          <ul className="mt-2 divide-y divide-line-soft border-t border-line">
            {analytics.riskDistribution.map((d) => (
              <li key={d.level} className="flex items-center gap-3 px-5 py-2.5">
                <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: donutColors[d.tone] }} />
                <span className="flex-1 text-13 text-ink-700">{d.label}</span>
                <span className="tabular text-13 font-semibold text-ink-900">{formatNumber(d.count)}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      {/* Categories + regional */}
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        <ChartCard
          className="xl:col-span-6"
          title="Violation categories"
          subtitle="Detected findings grouped by declaration type"
          height={300}
        >
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={analytics.violationCategories}
              layout="vertical"
              margin={{ top: 4, right: 24, bottom: 4, left: 8 }}
              barSize={16}
            >
              <CartesianGrid stroke={CHART.grid} horizontal={false} />
              <XAxis type="number" tickLine={false} axisLine={false} />
              <YAxis
                type="category"
                dataKey="category"
                width={148}
                tickLine={false}
                axisLine={false}
                tick={{ fontSize: 12, fill: '#344054' }}
              />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(37,99,235,0.05)' }} />
              <Bar dataKey="count" name="Findings" fill={CHART.primary} radius={[0, 3, 3, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <section className="panel-flush xl:col-span-6">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Regional insights</h2>
              <p className="panel-subtitle">Inspection load and compliance rate by zone</p>
            </div>
          </div>
          <ul className="divide-y divide-line-soft">
            {analytics.regional.map((r) => (
              <li key={r.zone} className="px-5 py-3.5">
                <div className="flex items-center justify-between gap-4">
                  <span className="min-w-0 truncate text-13 font-medium text-ink-900">{r.zone}</span>
                  <span className="tabular shrink-0 text-13 text-ink-500">
                    {formatNumber(r.inspections)} inspections
                    <span className="mx-2 text-ink-300">·</span>
                    <span
                      className={`font-semibold ${
                        r.complianceRate >= 83 ? 'text-success-700' : r.complianceRate >= 79 ? 'text-ink-900' : 'text-warning-700'
                      }`}
                    >
                      {r.complianceRate}%
                    </span>
                  </span>
                </div>
                <div className="mt-2 flex h-1.5 w-full overflow-hidden rounded-full bg-line">
                  <span
                    className="bg-navy-700"
                    style={{ width: `${((r.inspections - r.violations) / maxRegional) * 100}%` }}
                  />
                  <span className="bg-danger-600" style={{ width: `${(r.violations / maxRegional) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
          <div className="flex items-center gap-4 border-t border-line px-5 py-2.5">
            <LegendItem color={CHART.navy} label="Compliant" />
            <LegendItem color={CHART.danger} label="Violations" />
          </div>
        </section>
      </div>

      {/* Repeat offender analysis */}
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-12">
        <ChartCard
          className="xl:col-span-7"
          title="Repeat offender analysis"
          subtitle="Manufacturers carrying two or more substantiated violations"
          height={252}
          legend={
            <>
              <LegendItem color={CHART.navy} label="Tracked offenders" />
              <LegendItem color={CHART.warning} label="Newly identified" dashed />
            </>
          }
        >
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={analytics.offenderTrend} margin={{ top: 8, right: 12, bottom: 0, left: -18 }}>
              <CartesianGrid stroke={CHART.grid} vertical={false} />
              <XAxis dataKey="month" tickLine={false} axisLine={{ stroke: CHART.grid }} tickMargin={10} />
              <YAxis tickLine={false} axisLine={false} width={44} tickMargin={4} domain={[0, 36]} />
              <Tooltip content={<ChartTooltip />} cursor={{ stroke: CHART.axis, strokeDasharray: '3 3', strokeOpacity: 0.4 }} />
              <Line
                type="monotone"
                dataKey="offenders"
                name="Tracked offenders"
                stroke={CHART.navy}
                strokeWidth={2}
                dot={{ r: 2.5, strokeWidth: 0, fill: CHART.navy }}
              />
              <Line
                type="monotone"
                dataKey="newOffenders"
                name="Newly identified"
                stroke={CHART.warning}
                strokeWidth={1.75}
                strokeDasharray="4 4"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <section className="panel-flush xl:col-span-5">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Findings by confidence band</h2>
              <p className="panel-subtitle">Where the engine stops and defers to an inspector</p>
            </div>
          </div>
          <ul className="divide-y divide-line-soft">
            {analytics.confidenceBands.map((b) => {
              const total = analytics.confidenceBands.reduce((s, x) => s + x.count, 0);
              return (
                <li key={b.band} className="px-5 py-3.5">
                  <div className="flex items-center justify-between gap-4">
                    <span className="tabular text-13 font-medium text-ink-900">{b.band}</span>
                    <span className="tabular text-13 font-semibold text-ink-900">{formatNumber(b.count)}</span>
                  </div>
                  <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-line">
                    <span
                      className="block h-full rounded-full bg-navy-700"
                      style={{ width: `${(b.count / total) * 100}%` }}
                    />
                  </div>
                  <p className="mt-1.5 text-xs text-ink-500">{b.decision}</p>
                </li>
              );
            })}
          </ul>
        </section>
      </div>
    </div>
  );
}

function Summary({ label, value, tone = 'text-ink-900' }) {
  return (
    <div className="px-5 py-4">
      <p className="eyebrow">{label}</p>
      <p className={`tabular mt-1.5 text-[22px] font-bold leading-7 ${tone}`}>{value}</p>
    </div>
  );
}
