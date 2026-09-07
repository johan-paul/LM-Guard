import React, { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, FileSearch, ArrowRight } from 'lucide-react';

import EvidenceViewer from '../components/EvidenceViewer';
import StatusBadge from '../components/StatusBadge';
import { RiskScore, ConfidenceMeter } from '../components/RiskBadge';
import EmptyState from '../components/EmptyState';
import { DetailSkeleton } from '../components/LoadingState';
import { useInspection } from '../hooks/useData';
import { LABEL_REGIONS } from '../data/assets';
import { formatDateTime } from '../utils/format';

/**
 * Full-surface evidence review for an inspection: every region the engine
 * examined, with the findings raised against them.
 */
export default function Evidence() {
  const { id } = useParams();
  const { inspection, loading, error } = useInspection(id);

  const boxes = useMemo(() => {
    if (!inspection) return [];
    const fromViolations = (inspection.violations || []).flatMap((v) =>
      (v.boxes || []).map((b) => ({ ...b, label: b.label || v.title, note: `${v.id} · ${v.notes || v.expectedValue}` })),
    );
    if (fromViolations.length) return fromViolations;

    // Compliant record: show the declaration regions that were verified.
    return inspection.declarations
      .filter((d) => LABEL_REGIONS[d.field])
      .map((d) => ({ ...LABEL_REGIONS[d.field], label: d.label, note: `${d.label} · ${d.detected}` }));
  }, [inspection]);

  if (loading) return <DetailSkeleton />;

  if (error || !inspection) {
    return (
      <div className="panel">
        <EmptyState
          icon={FileSearch}
          title="Evidence not available"
          description={`No evidence records could be located for ${id}.`}
          action={
            <Link to="/inspections" className="btn-secondary">
              <ArrowLeft className="h-4 w-4" /> Back to inspections
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <Link
        to={`/inspections/${inspection.id}`}
        className="inline-flex items-center gap-1.5 text-13 text-ink-500 transition-colors hover:text-ink-900"
      >
        <ArrowLeft className="h-3.5 w-3.5" strokeWidth={2} /> {inspection.id}
      </Link>

      <section className="panel px-5 py-5 sm:px-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <div className="mb-2 flex flex-wrap items-center gap-2.5">
              <span className="mono-id text-ink-500">{inspection.id}</span>
              <StatusBadge status={inspection.status} size="lg" />
            </div>
            <h1 className="text-[22px] font-semibold leading-7 tracking-tight text-ink-900">
              Evidence · {inspection.productName}
            </h1>
            <p className="mt-1 text-13 text-ink-500">
              {inspection.inspector} · {inspection.zone} · {formatDateTime(inspection.date)}
            </p>
          </div>
          <div className="shrink-0">
            <p className="text-micro font-semibold uppercase text-ink-400">Risk score</p>
            <div className="mt-1.5">
              <RiskScore score={inspection.riskScore} width="w-[120px]" />
            </div>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        <section className="panel-flush xl:col-span-8">
          <EvidenceViewer
            image={inspection.evidenceImage}
            boxes={boxes}
            caption="Principal display panel as captured, with the regions the engine examined."
            reference={`Ruleset ${inspection.rulesetVersion}`}
            minHeight="min-h-[460px]"
          />
        </section>

        <div className="space-y-5 xl:col-span-4">
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Findings on this capture</h2>
                <p className="panel-subtitle">Each links to its case file</p>
              </div>
            </div>
            {inspection.violations?.length ? (
              <ul className="divide-y divide-line-soft">
                {inspection.violations.map((v) => (
                  <li key={v.id}>
                    <Link to={`/violations/${v.id}`} className="block px-5 py-3.5 transition-colors hover:bg-canvas">
                      <div className="flex items-start justify-between gap-3">
                        <p className="text-13 font-medium text-ink-900">{v.title}</p>
                        <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={2} />
                      </div>
                      <p className="mt-1 font-mono text-[11px] text-ink-400">
                        {v.id} · {v.ruleId}
                      </p>
                      <div className="mt-2">
                        <ConfidenceMeter value={v.confidence} />
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                compact
                title="No findings raised"
                description="Every declaration on this panel satisfied the active ruleset."
              />
            )}
          </section>

          <section className="panel-flush">
            <div className="panel-header">
              <h2 className="panel-title">Extracted declarations</h2>
            </div>
            <ul className="divide-y divide-line-soft">
              {inspection.declarations.map((d) => (
                <li key={d.field} className="flex items-start justify-between gap-4 px-5 py-3">
                  <div className="min-w-0">
                    <p className="text-13 text-ink-900">{d.label}</p>
                    <p className="truncate text-xs text-ink-500">{d.detected}</p>
                  </div>
                  <StatusBadge status={d.status} showIcon={false} />
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}
