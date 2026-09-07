import React, { useState, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Package, ArrowRight, Globe, AlertTriangle, CheckCircle2, Download } from 'lucide-react';

import StatusBadge from '../components/StatusBadge';
import RiskBadge, { RiskScore } from '../components/RiskBadge';
import DataTable, { PrimaryCell } from '../components/DataTable';
import Tabs from '../components/Tabs';
import { ChangeTimeline } from '../components/Timeline';
import EmptyState from '../components/EmptyState';
import { DetailSkeleton } from '../components/LoadingState';
import { MiniStat } from '../components/StatCard';
import { useProduct } from '../hooks/useData';
import { formatDate, formatDateTime, formatMonthYear, formatConfidence } from '../utils/format';

const parseQty = (s = '') => {
  const m = String(s).match(/([\d.]+)\s*(kg|g|ml|l)/i);
  if (!m) return null;
  const n = parseFloat(m[1]);
  const unit = m[2].toLowerCase();
  return { n, unit, base: unit === 'kg' || unit === 'l' ? n * 1000 : n };
};
const parseMoney = (s = '') => {
  // Must start matching AT a digit, not just any run of digits/commas/periods - "Rs. 10.00"
  // has a bare "." right after "Rs" that a leading-agnostic pattern matches first (as ".",
  // parseFloat(".") = NaN), so it never reached the real "10.00" that follows the space. That
  // silently broke every MRP comparison for the (very common) "Rs. X" printed format - the
  // delta check below always saw NaN (falsy) as if no value had been read at all.
  const m = String(s).match(/\d[\d,]*\.?\d*/);
  return m ? parseFloat(m[0].replace(/,/g, '')) : null;
};

export default function ProductDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { product, loading, error } = useProduct(id);
  const [tab, setTab] = useState('overview');

  const changeEntries = useMemo(() => {
    if (!product) return [];
    return product.packageChanges.map((c, i) => {
      const prev = product.packageChanges[i + 1];
      let deltaNote = '';
      if (prev) {
        const q = parseQty(c.netQuantity);
        const pq = parseQty(prev.netQuantity);
        const m = parseMoney(c.mrp);
        const pm = parseMoney(prev.mrp);
        const parts = [];

        // A value going from "not declared" to "declared" (or vice versa) is just as real a
        // difference as two declared values disagreeing - the previous version of this only
        // compared when *both* sides parsed, which silently dropped the far more common case of
        // a re-scan finally reading a field the first scan missed (or a rescan losing a field
        // the first one caught), understating exactly the kind of change an inspector needs to
        // see between two scans of the same product.
        if (q && pq && q.base !== pq.base) {
          const diff = q.base - pq.base;
          parts.push(`${diff > 0 ? '+' : ''}${Math.round(diff)} ${q.unit === 'l' || q.unit === 'ml' ? 'mL' : 'g'} qty`);
        } else if (q && !pq) {
          parts.push(`qty now declared (${c.netQuantity})`);
        } else if (!q && pq) {
          parts.push(`qty declaration lost (was ${prev.netQuantity})`);
        }

        if (m && pm && m !== pm) {
          parts.push(`${m > pm ? '+' : '−'}₹${Math.abs(m - pm).toFixed(2)}`);
        } else if (m && !pm) {
          parts.push(`MRP now declared (₹${m.toFixed(2)})`);
        } else if (!m && pm) {
          parts.push(`MRP declaration lost (was ₹${pm.toFixed(2)})`);
        }

        deltaNote = parts.join(' · ');
      }
      return {
        ...c,
        heading: formatMonthYear(c.date),
        deltaNote,
      };
    });
  }, [product]);

  if (loading) return <DetailSkeleton />;

  if (error || !product) {
    return (
      <div className="panel">
        <EmptyState
          icon={Package}
          title="Product not available"
          description={`No product profile could be located for ${id}.`}
          action={
            <Link to="/products" className="btn-secondary">
              <ArrowLeft className="h-4 w-4" /> Back to products
            </Link>
          }
        />
      </div>
    );
  }

  const listing = product.digitalListing;

  const tabs = [
    { value: 'overview', label: 'Overview' },
    { value: 'inspections', label: 'Inspection History', count: product.inspections.length },
    { value: 'violations', label: 'Violations', count: product.violations.length },
    { value: 'changes', label: 'Package Changes', count: product.packageChanges.length },
    { value: 'digital', label: 'Digital Comparison' },
  ];

  return (
    <div className="space-y-5">
      <Link to="/products" className="inline-flex items-center gap-1.5 text-13 text-ink-500 transition-colors hover:text-ink-900">
        <ArrowLeft className="h-3.5 w-3.5" strokeWidth={2} /> Products
      </Link>

      {/* Profile header */}
      <section className="panel px-5 py-5 sm:px-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex min-w-0 gap-4">
            {product.image ? (
              <img
                src={product.image}
                alt=""
                className="h-[86px] w-[76px] shrink-0 rounded-lg border border-line object-cover"
                aria-hidden="true"
              />
            ) : (
              <div
                className="flex h-[86px] w-[76px] shrink-0 items-center justify-center rounded-lg border border-line bg-canvas"
                aria-hidden="true"
              >
                <Package className="h-6 w-6 text-ink-300" strokeWidth={1.5} />
              </div>
            )}
            <div className="min-w-0">
              <div className="mb-2 flex flex-wrap items-center gap-2.5">
                <span className="mono-id text-ink-500">{product.id}</span>
                <RiskBadge level={product.riskLevel} />
                {product.openViolations > 0 && (
                  <span className="badge border-danger-100 bg-danger-50 text-danger-700">
                    {product.openViolations} open case{product.openViolations > 1 ? 's' : ''}
                  </span>
                )}
              </div>
              <h1 className="text-[24px] font-semibold leading-8 tracking-tight text-ink-900">{product.name}</h1>
              <p className="mt-1.5 text-sm text-ink-500">
                {product.category} · {product.manufacturer}
              </p>
            </div>
          </div>

          <div className="flex shrink-0 flex-col gap-4 sm:flex-row sm:items-start">
            <div className="rounded-lg border border-line bg-canvas/60 px-5 py-4">
              <p className="text-micro font-semibold uppercase text-ink-400">Composite risk score</p>
              <div className="mt-2">
                <RiskScore score={product.riskScore} width="w-[120px]" />
              </div>
              <p className="mt-2 text-[11px] text-ink-400">
                {product.violationCount} violations across {product.totalInspections} inspections
              </p>
            </div>
            <button type="button" className="btn-secondary btn-lg">
              <Download className="h-4 w-4" strokeWidth={1.9} /> Export profile
            </button>
          </div>
        </div>
      </section>

      <Tabs tabs={tabs} value={tab} onChange={setTab} />

      {/* Overview */}
      {tab === 'overview' && (
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
          <div className="space-y-5 xl:col-span-8">
            <section className="panel-flush">
              <div className="panel-header">
                <h2 className="panel-title">Declared particulars</h2>
              </div>
              <dl className="grid grid-cols-1 gap-px bg-line sm:grid-cols-2 lg:grid-cols-3">
                <Fact label="Product ID" value={product.id} mono />
                <Fact label="Category" value={product.category} />
                <Fact label="Manufacturer" value={product.manufacturer} />
                <Fact label="Net quantity" value={product.netQuantity} />
                <Fact label="Retail sale price" value={product.mrp} />
                <Fact label="Barcode" value={product.barcode} mono />
                <Fact label="First recorded" value={formatDate(product.firstSeen)} />
                <Fact label="Last inspection" value={formatDate(product.lastInspection)} />
                <Fact label="Compliance rate" value={`${product.complianceRate}%`} />
              </dl>
            </section>

            <section className="panel-flush">
              <div className="panel-header">
                <div>
                  <h2 className="panel-title">Risk factors</h2>
                  <p className="panel-subtitle">Signals contributing to this product's position in the queue</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 px-5 py-4">
                {product.riskFactors.map((f) => (
                  <span key={f} className="tag h-7 px-2.5 text-xs">
                    {f}
                  </span>
                ))}
              </div>
              {product.primaryIssue && product.primaryIssue !== '—' && (
                <div className="flex items-start gap-3 border-t border-line px-5 py-3.5">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-600" strokeWidth={1.9} />
                  <p className="text-13 text-ink-700">
                    <span className="font-medium text-ink-900">Primary issue:</span> {product.primaryIssue}
                  </p>
                </div>
              )}
            </section>
          </div>

          <div className="space-y-5 xl:col-span-4">
            <section className="panel px-5 py-5">
              <h2 className="panel-title mb-4">Compliance record</h2>
              <div className="grid grid-cols-2 gap-5">
                <MiniStat label="Inspections" value={product.totalInspections} />
                <MiniStat label="Violations" value={product.violationCount} tone={product.violationCount ? 'critical' : 'default'} />
                <MiniStat label="Open cases" value={product.openViolations} tone={product.openViolations ? 'warning' : 'default'} />
                <MiniStat label="Compliance" value={`${product.complianceRate}%`} tone="success" />
              </div>
            </section>

            <section className="panel-flush">
              <div className="panel-header">
                <h2 className="panel-title">Latest inspection</h2>
              </div>
              {product.inspections[0] ? (
                <Link to={`/inspections/${product.inspections[0].id}`} className="block px-5 py-4 transition-colors hover:bg-canvas">
                  <div className="flex items-center justify-between gap-3">
                    <span className="mono-id">{product.inspections[0].id}</span>
                    <StatusBadge status={product.inspections[0].status} />
                  </div>
                  <p className="mt-2 text-13 text-ink-700">{product.inspections[0].inspector} · {product.inspections[0].zone}</p>
                  <p className="mt-0.5 text-xs text-ink-500">{formatDateTime(product.inspections[0].date)}</p>
                </Link>
              ) : (
                <EmptyState compact title="No inspections recorded" description="This product has not been inspected yet." />
              )}
            </section>
          </div>
        </div>
      )}

      {/* Inspection history */}
      {tab === 'inspections' && (
        <section className="panel-flush">
          <DataTable
            rows={product.inspections}
            onRowClick={(row) => navigate(`/inspections/${row.id}`)}
            emptyTitle="No inspections recorded"
            columns={[
              { key: 'id', header: 'Inspection ID', width: 148, render: (r) => <span className="mono-id">{r.id}</span> },
              { key: 'inspector', header: 'Inspector', render: (r) => <PrimaryCell title={r.inspector} subtitle={r.zone} /> },
              { key: 'status', header: 'Compliance', render: (r) => <StatusBadge status={r.status} /> },
              { key: 'risk', header: 'Risk score', width: 176, hideBelow: 'md', render: (r) => <RiskScore score={r.riskScore} /> },
              { key: 'date', header: 'Date', hideBelow: 'lg', render: (r) => <span className="text-ink-600">{formatDateTime(r.date)}</span> },
              {
                key: 'action',
                header: '',
                align: 'right',
                width: 84,
                render: (r) => (
                  <Link to={`/inspections/${r.id}`} onClick={(e) => e.stopPropagation()} className="inline-flex items-center gap-1 text-13 font-medium text-brand-600 hover:text-brand-700">
                    View <ArrowRight className="h-3.5 w-3.5" strokeWidth={2} />
                  </Link>
                ),
              },
            ]}
          />
        </section>
      )}

      {/* Violations */}
      {tab === 'violations' && (
        <section className="panel-flush">
          <DataTable
            rows={product.violations}
            onRowClick={(row) => navigate(`/violations/${row.id}`)}
            emptyTitle="No violations on record"
            emptyDescription="Every inspection of this product satisfied the active ruleset."
            columns={[
              {
                key: 'evidence',
                header: '',
                width: 52,
                render: (r) =>
                  r.evidenceImageUrl ? (
                    <img
                      src={r.evidenceImageUrl}
                      alt=""
                      className="h-9 w-9 shrink-0 rounded border border-line object-cover"
                      aria-hidden="true"
                    />
                  ) : (
                    <span
                      className="flex h-9 w-9 shrink-0 items-center justify-center rounded border border-line bg-canvas"
                      title="No image region - this is an absence finding, nothing to point a box at"
                      aria-hidden="true"
                    >
                      <Package className="h-3.5 w-3.5 text-ink-300" strokeWidth={1.5} />
                    </span>
                  ),
              },
              { key: 'id', header: 'Violation ID', width: 140, render: (r) => <span className="mono-id">{r.id}</span> },
              { key: 'title', header: 'Finding', render: (r) => <PrimaryCell title={r.title} subtitle={r.type} /> },
              { key: 'rule', header: 'Rule', hideBelow: 'md', width: 140, render: (r) => <span className="font-mono text-[12px] text-ink-800">{r.ruleId}</span> },
              { key: 'confidence', header: 'Confidence', hideBelow: 'lg', width: 110, render: (r) => <span className="tabular">{formatConfidence(r.confidence)}</span> },
              { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              { key: 'detected', header: 'Detected', hideBelow: 'xl', render: (r) => <span className="text-ink-600">{formatDate(r.detectedAt)}</span> },
            ]}
          />
        </section>
      )}

      {/* Package changes */}
      {tab === 'changes' && (
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
          <section className="panel px-5 py-5 xl:col-span-7">
            <h2 className="panel-title mb-1">Package change history</h2>
            <p className="panel-subtitle mb-6">Declared pack size and price recorded at each inspection</p>
            <ChangeTimeline
              entries={changeEntries}
              renderBadge={(entry) =>
                entry.flag === 'COMPLIANT' ? (
                  <StatusBadge status="COMPLIANT" />
                ) : entry.flag === 'VIOLATION' ? (
                  <StatusBadge status="VIOLATION" />
                ) : entry.flag === 'REVIEW' ? (
                  <StatusBadge status="REVIEW" />
                ) : (
                  <StatusBadge status="CHANGE" />
                )
              }
            />
          </section>

          <section className="panel-flush xl:col-span-5">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">What changed</h2>
                <p className="panel-subtitle">Differences between consecutive records</p>
              </div>
            </div>
            <ul className="divide-y divide-line-soft">
              {changeEntries
                .filter((e) => e.deltaNote)
                .map((e) => (
                  <li key={e.date} className="px-5 py-3.5">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-13 font-medium text-ink-900">{e.heading}</span>
                      <span className="tabular text-13 font-semibold text-warning-700">{e.deltaNote}</span>
                    </div>
                    <p className="mt-1 text-xs text-ink-500">{e.note}</p>
                  </li>
                ))}
              {changeEntries.filter((e) => e.deltaNote).length === 0 && (
                <li>
                  <EmptyState compact title="No pack or price changes" description="Declared quantity and price have remained stable." />
                </li>
              )}
            </ul>
          </section>
        </div>
      )}

      {/* Digital comparison */}
      {tab === 'digital' && !listing && (
        <section className="panel">
          <EmptyState
            icon={Globe}
            title="No online listing on record"
            description="A marketplace listing hasn't been captured for this product yet, so there is nothing to compare the package declaration against."
          />
        </section>
      )}
      {tab === 'digital' && listing && (
        <section className="panel-flush">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Physical–digital comparison</h2>
              <p className="panel-subtitle">
                Package declaration checked against the {listing.platform.toLowerCase()} · captured {formatDate(listing.checkedAt)}
              </p>
            </div>
            {listing.mismatch ? (
              <span className="badge border-danger-100 bg-danger-50 text-danger-700">Mismatch detected</span>
            ) : (
              <span className="badge border-success-100 bg-success-50 text-success-700">Consistent</span>
            )}
          </div>

          <div className="grid grid-cols-1 gap-px bg-line md:grid-cols-2">
            <ComparePanel
              title="Physical package"
              icon={Package}
              rows={[
                { label: 'Retail sale price', value: listing.packageMrp, mismatch: listing.mismatch && listing.packageMrp !== listing.listedMrp },
                { label: 'Net quantity', value: listing.packageQuantity, mismatch: listing.mismatch && listing.packageQuantity !== listing.listedQuantity },
              ]}
            />
            <ComparePanel
              title="Online listing"
              icon={Globe}
              rows={[
                { label: 'Retail sale price', value: listing.listedMrp, mismatch: listing.mismatch && listing.packageMrp !== listing.listedMrp },
                { label: 'Net quantity', value: listing.listedQuantity, mismatch: listing.mismatch && listing.packageQuantity !== listing.listedQuantity },
              ]}
            />
          </div>

          <div className="flex items-start gap-3 border-t border-line px-5 py-4">
            {listing.mismatch ? (
              <>
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={1.9} />
                <p className="text-13 text-ink-700">
                  Rule <span className="font-mono">LMPC-PRC-009</span> flags a discrepancy between the printed declaration
                  and the listing for the same pack size. A finding has been raised for inspector review.
                </p>
              </>
            ) : (
              <>
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success-600" strokeWidth={1.9} />
                <p className="text-13 text-ink-700">
                  Package and listing declarations agree for the same pack size. No finding raised.
                </p>
              </>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

function Fact({ label, value, mono = false }) {
  return (
    <div className="bg-surface px-5 py-3.5">
      <dt className="text-micro font-semibold uppercase text-ink-400">{label}</dt>
      <dd className={`mt-1 text-13 text-ink-900 ${mono ? 'font-mono' : 'font-medium'}`}>{value}</dd>
    </div>
  );
}

function ComparePanel({ title, icon: Icon, rows }) {
  return (
    <div className="bg-surface px-5 py-4">
      <p className="mb-3.5 flex items-center gap-2 text-13 font-semibold text-ink-900">
        <Icon className="h-4 w-4 text-ink-400" strokeWidth={1.9} /> {title}
      </p>
      <dl className="space-y-3">
        {rows.map((r) => (
          <div key={r.label} className="flex items-center justify-between gap-4">
            <dt className="text-13 text-ink-500">{r.label}</dt>
            <dd
              className={`tabular rounded px-1.5 py-0.5 text-13 font-semibold ${
                r.mismatch ? 'bg-danger-50 text-danger-700' : 'text-ink-900'
              }`}
            >
              {r.value}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
