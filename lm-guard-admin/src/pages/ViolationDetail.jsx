import React, { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle2,
  RefreshCw,
  ShieldAlert,
  FileSearch,
  Scale,
  ExternalLink,
  Info,
  Loader2,
} from 'lucide-react';

import StatusBadge from '../components/StatusBadge';
import RiskBadge from '../components/RiskBadge';
import EvidenceViewer from '../components/EvidenceViewer';
import Timeline from '../components/Timeline';
import EmptyState from '../components/EmptyState';
import { DetailSkeleton } from '../components/LoadingState';
import { useViolation } from '../hooks/useData';
import { inspectionService } from '../services/inspectionService';
import { formatDateTime, formatConfidence } from '../utils/format';

const DECISIONS = [
  {
    key: 'COMPLIANT',
    label: 'Mark Compliant',
    icon: CheckCircle2,
    className: 'btn-success-quiet',
    confirm: 'Case dismissed — the declaration was found compliant on review.',
  },
  {
    key: 'RESCAN',
    label: 'Request Re-scan',
    icon: RefreshCw,
    className: 'btn-secondary',
    confirm: 'Re-scan requested — a fresh capture has been queued for this package.',
  },
  {
    key: 'ESCALATE',
    label: 'Escalate for Review',
    icon: ShieldAlert,
    className: 'btn-danger',
    confirm: 'Case escalated to the zonal controller for enforcement review.',
  },
];

export default function ViolationDetail() {
  const { id } = useParams();
  const { violation, loading, error, setViolation } = useViolation(id);
  const [pending, setPending] = useState(null);
  const [outcome, setOutcome] = useState(null);

  const decide = async (decision) => {
    setPending(decision.key);
    try {
      const updated = await inspectionService.decideViolation(id, decision.key);
      setViolation((prev) => ({ ...prev, ...updated }));
      setOutcome(decision.confirm);
    } finally {
      setPending(null);
    }
  };

  if (loading) return <DetailSkeleton />;

  if (error || !violation) {
    return (
      <div className="panel">
        <EmptyState
          icon={FileSearch}
          title="Case not available"
          description={`No violation case could be located for ${id}.`}
          action={
            <Link to="/violations" className="btn-secondary">
              <ArrowLeft className="h-4 w-4" /> Back to case queue
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <Link to="/violations" className="inline-flex items-center gap-1.5 text-13 text-ink-500 transition-colors hover:text-ink-900">
        <ArrowLeft className="h-3.5 w-3.5" strokeWidth={2} /> Violations
      </Link>

      {/* Case header */}
      <section className="panel px-5 py-5 sm:px-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="mb-2 flex flex-wrap items-center gap-2.5">
              <span className="mono-id text-ink-500">{violation.id}</span>
              <StatusBadge status={violation.status} size="lg" />
              <RiskBadge level={violation.riskLevel} />
            </div>
            <h1 className="text-[24px] font-semibold leading-8 tracking-tight text-ink-900">{violation.title}</h1>
            <p className="mt-1.5 text-sm text-ink-500">
              {violation.productName} · {violation.manufacturer}
            </p>
          </div>

          <div className="flex shrink-0 flex-wrap gap-x-8 gap-y-3 rounded-lg border border-line bg-canvas/60 px-5 py-4">
            <Figure label="Detected" value={formatDateTime(violation.detectedAt)} />
            <Figure label="Inspector" value={violation.inspector} />
            <Figure label="Zone" value={violation.zone} />
            <Figure label="Source record" value={<Link className="link-quiet" to={`/inspections/${violation.inspectionId}`}>{violation.inspectionId}</Link>} />
          </div>
        </div>
      </section>

      {outcome && (
        <div className="flex items-start gap-3 rounded-xl border border-brand-100 bg-brand-50 px-5 py-3.5">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" strokeWidth={2} />
          <p className="text-13 text-brand-800">{outcome}</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        {/* Evidence */}
        <section className="panel-flush xl:col-span-7">
          <EvidenceViewer
            image={violation.evidenceImage}
            boxes={violation.boxes}
            caption={violation.notes}
            reference={`${violation.inspectionId} · ruleset ${violation.rulesetVersion}`}
            minHeight="min-h-[420px]"
          />
        </section>

        {/* Finding summary */}
        <div className="space-y-5 xl:col-span-5">
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Finding summary</h2>
                <p className="panel-subtitle">What the engine observed and which rule it failed</p>
              </div>
            </div>

            <dl className="divide-y divide-line-soft">
              <Row label="Potential violation" value={violation.title} strong />
              <Row
                label="Confidence"
                value={
                  <span className="flex items-center gap-2.5">
                    <span className="tabular font-semibold text-ink-900">{formatConfidence(violation.confidence)}</span>
                    <span className="h-1 w-20 overflow-hidden rounded-full bg-line">
                      <span
                        className={`block h-full rounded-full ${violation.confidence < 0.7 ? 'bg-warning-600' : 'bg-navy-700'}`}
                        style={{ width: `${violation.confidence * 100}%` }}
                      />
                    </span>
                  </span>
                }
              />
              <Row label="Rule reference" value={<span className="font-mono text-13 text-ink-900">{violation.ruleId}</span>} />
              <Row label="Ruleset version" value={<span className="font-mono text-13 text-ink-900">{violation.rulesetVersion}</span>} />
              <Row label="Declaration" value={violation.ruleName} />
              <Row label="Detected value" value={<span className="text-danger-600">{violation.detectedValue}</span>} />
              <Row label="Expected" value={violation.expectedValue} />
              <Row
                label="Decision"
                value={
                  <span className="inline-flex items-center gap-2 text-ink-900">
                    <span className="h-1.5 w-1.5 rounded-full bg-warning-600" />
                    {violation.decision}
                  </span>
                }
                strong
              />
            </dl>

            {/* Decision actions */}
            <div className="border-t border-line px-5 py-4">
              <p className="eyebrow mb-3">Inspector decision</p>
              <div className="flex flex-wrap gap-2">
                {DECISIONS.map((d) => {
                  const Icon = d.icon;
                  const busy = pending === d.key;
                  return (
                    <button
                      key={d.key}
                      type="button"
                      disabled={!!pending}
                      onClick={() => decide(d)}
                      className={`${d.className} btn-lg`}
                    >
                      {busy ? (
                        <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />
                      ) : (
                        <Icon className="h-4 w-4" strokeWidth={1.9} />
                      )}
                      {d.label}
                    </button>
                  );
                })}
              </div>
              <p className="mt-3 text-xs text-ink-400">
                The engine does not close cases. Recording a decision here attributes it to your officer account.
              </p>
            </div>
          </section>
        </div>
      </div>

      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-12">
        {/* Rule applied */}
        <section className="panel-flush xl:col-span-7">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Rule applied</h2>
              <p className="panel-subtitle">{violation.ruleCategory}</p>
            </div>
            <Link to={`/rules?rule=${violation.ruleId}`} className="btn-secondary btn-sm">
              <Scale className="h-3.5 w-3.5" strokeWidth={1.9} /> Rule registry
            </Link>
          </div>

          <div className="px-5 py-4">
            <div className="flex flex-wrap items-center gap-2.5">
              <span className="font-mono text-13 font-medium text-ink-900">{violation.ruleId}</span>
              <span className="text-13 text-ink-700">{violation.ruleName}</span>
            </div>
            <p className="mt-2.5 text-13 leading-relaxed text-ink-600">{violation.ruleSummary}</p>

            <div className="mt-4 rounded-lg border border-line bg-navy-950 px-4 py-3">
              <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.1em] text-white/35">Evaluation logic</p>
              <code className="block whitespace-pre-wrap break-words font-mono text-[12px] leading-relaxed text-brand-200">
                {violation.ruleLogic}
              </code>
            </div>
          </div>

          {violation.relatedCases?.length > 0 && (
            <>
              <div className="border-t border-line px-5 py-2.5">
                <p className="eyebrow">Other cases on this product</p>
              </div>
              <ul className="divide-y divide-line-soft">
                {violation.relatedCases.map((c) => (
                  <li key={c.id}>
                    <Link to={`/violations/${c.id}`} className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-canvas">
                      <span className="mono-id shrink-0">{c.id}</span>
                      <span className="min-w-0 flex-1 truncate text-13 text-ink-700">{c.title}</span>
                      <StatusBadge status={c.status} />
                      <ExternalLink className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        {/* Case timeline */}
        <section className="panel px-5 py-5 xl:col-span-5">
          <h2 className="panel-title mb-1">Case timeline</h2>
          <p className="panel-subtitle mb-5">Package → analysis → facts → rules → evidence → review</p>
          <Timeline steps={violation.inspection?.timeline || []} showDate />
        </section>
      </div>
    </div>
  );
}

function Row({ label, value, strong = false }) {
  return (
    <div className="grid grid-cols-[132px_1fr] items-start gap-4 px-5 py-3">
      <dt className="text-micro font-semibold uppercase text-ink-400">{label}</dt>
      <dd className={`min-w-0 text-13 ${strong ? 'font-medium text-ink-900' : 'text-ink-700'}`}>{value}</dd>
    </div>
  );
}

function Figure({ label, value }) {
  return (
    <div className="min-w-0">
      <p className="text-micro font-semibold uppercase text-ink-400">{label}</p>
      <p className="mt-1 text-13 text-ink-800">{value}</p>
    </div>
  );
}
