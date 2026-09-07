import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Download,
  History,
  ScanSearch,
  AlertOctagon,
  Building2,
  MapPin,
  Clock,
  FileText,
  UserCog,
  UserCheck,
  Loader2,
  AlertCircle,
} from 'lucide-react';

import StatusBadge from '../components/StatusBadge';
import RiskBadge, { RiskScore } from '../components/RiskBadge';
import ComplianceItem from '../components/ComplianceItem';
import Timeline from '../components/Timeline';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import { DetailSkeleton } from '../components/LoadingState';
import { useInspection } from '../hooks/useData';
import { formatDateTime, formatConfidence, complianceScore } from '../utils/format';
import { generateInspectionPDF } from '../services/pdfExport';
import { inspectionService } from '../services/inspectionService';
import { useAuth } from '../context/AuthContext';

export default function InspectionDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { inspection, loading, error, refetch } = useInspection(id);
  const [reassignOpen, setReassignOpen] = useState(false);

  if (loading) return <DetailSkeleton />;

  if (error || !inspection) {
    return (
      <div className="panel">
        <EmptyState
          icon={FileText}
          title="Inspection record not available"
          description={`No record could be located for ${id}.`}
          action={
            <Link to="/inspections" className="btn-secondary">
              <ArrowLeft className="h-4 w-4" /> Back to inspections
            </Link>
          }
        />
      </div>
    );
  }

  const score = complianceScore(inspection.declarations);
  const failing = inspection.declarations.filter((d) => d.status !== 'COMPLIANT');

  return (
    <div className="space-y-6">
      <Link to="/inspections" className="inline-flex items-center gap-1.5 text-13 text-ink-500 transition-colors hover:text-ink-900">
        <ArrowLeft className="h-3.5 w-3.5" strokeWidth={2} /> Inspections
      </Link>

      {/* Record header */}
      <section className="panel px-5 py-5 sm:px-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="mb-2 flex flex-wrap items-center gap-2.5">
              <span className="mono-id text-ink-500">{inspection.id}</span>
              <StatusBadge status={inspection.status} size="lg" />
            </div>
            <h1 className="text-[24px] font-semibold leading-8 tracking-tight text-ink-900">{inspection.productName}</h1>

            <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-13 text-ink-500">
              <span className="inline-flex items-center gap-1.5">
                <Building2 className="h-3.5 w-3.5 text-ink-400" strokeWidth={1.9} /> {inspection.manufacturer}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="h-3.5 w-3.5 text-ink-400" strokeWidth={1.9} /> {inspection.zone}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-ink-400" strokeWidth={1.9} /> {formatDateTime(inspection.date)}
              </span>
            </div>
          </div>

          <div className="grid shrink-0 grid-cols-2 gap-x-8 gap-y-3 rounded-lg border border-line bg-canvas/60 px-5 py-4 sm:grid-cols-3">
            <Figure label="Risk score" value={<RiskScore score={inspection.riskScore} showTrack={false} />} />
            <Figure label="Compliance" value={<span className="tabular text-13 font-semibold text-ink-900">{score}%</span>} />
            <Figure label="Risk level" value={<RiskBadge score={inspection.riskScore} />} />
            <Figure label="Inspector" value={<span className="text-13 text-ink-800">{inspection.inspector}</span>} />
            <Figure label="Ruleset" value={<span className="font-mono text-13 text-ink-800">{inspection.rulesetVersion}</span>} />
            <Figure
              label="Processing"
              value={<span className="tabular text-13 text-ink-800">{(inspection.processingMs / 1000).toFixed(1)}s</span>}
            />
          </div>
        </div>
      </section>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        {/* Compliance summary */}
        <div className="space-y-5 xl:col-span-8">
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Compliance summary</h2>
                <p className="panel-subtitle">
                  Structured facts extracted from the package, validated against ruleset {inspection.rulesetVersion}
                </p>
              </div>
              <span className="tabular hidden shrink-0 text-13 text-ink-500 sm:block">
                {inspection.declarations.length - failing.length} of {inspection.declarations.length} verified
              </span>
            </div>

            <div className="hidden grid-cols-[1fr_1fr_auto] gap-x-6 border-b border-line bg-canvas/70 px-5 py-2 pl-[54px] sm:grid">
              <span className="eyebrow">Declaration</span>
              <span className="eyebrow">Detected value</span>
              <span className="eyebrow">Confidence · Rule</span>
            </div>

            <div>
              {inspection.declarations.map((d) => (
                <ComplianceItem key={d.field} declaration={d} onRuleClick={(ruleId) => navigate(`/rules?rule=${ruleId}`)} />
              ))}
            </div>

            <div className="flex flex-wrap items-center gap-2 border-t border-line px-5 py-3.5">
              <Link to={`/evidence/${inspection.id}`} className="btn-primary">
                <ScanSearch className="h-4 w-4" strokeWidth={1.9} /> View Evidence
              </Link>
              <Link to={`/products/${inspection.productId}`} className="btn-secondary">
                <History className="h-4 w-4" strokeWidth={1.9} /> View Product History
              </Link>
              <button
                type="button"
                onClick={() => generateInspectionPDF(inspection, user?.name)}
                className="btn-secondary"
              >
                <Download className="h-4 w-4" strokeWidth={1.9} /> Download Report
              </button>
              <button type="button" onClick={() => setReassignOpen(true)} className="btn-secondary ml-auto">
                <UserCog className="h-4 w-4" strokeWidth={1.9} /> Reassign
              </button>
            </div>
          </section>

          {/* Findings */}
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Findings raised</h2>
                <p className="panel-subtitle">Each finding links to the evidence and the rule that produced it</p>
              </div>
              <span className="tabular text-13 text-ink-500">{inspection.violations?.length || 0}</span>
            </div>

            {inspection.violations?.length ? (
              <ul className="divide-y divide-line-soft">
                {inspection.violations.map((v) => (
                  <li key={v.id}>
                    <Link to={`/violations/${v.id}`} className="flex items-start gap-3.5 px-5 py-4 transition-colors hover:bg-canvas">
                      <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-danger-50 text-danger-600">
                        <AlertOctagon className="h-4 w-4" strokeWidth={1.9} />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                          <p className="text-13 font-medium text-ink-900">{v.title}</p>
                          <StatusBadge status={v.status} />
                        </div>
                        <p className="mt-1 text-xs text-ink-500">{v.expectedValue}</p>
                        <p className="mt-1.5 font-mono text-[11px] text-ink-400">
                          {v.id} · {v.ruleId} · confidence {formatConfidence(v.confidence)}
                        </p>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                compact
                title="No violations raised"
                description="All mandatory declarations satisfied the active ruleset for this capture."
              />
            )}
          </section>
        </div>

        {/* Evidence + timeline */}
        <div className="space-y-5 xl:col-span-4">
          <section className="panel-flush">
            <div className="panel-header">
              <h2 className="panel-title">Captured evidence</h2>
            </div>
            <Link to={`/evidence/${inspection.id}`} className="group block bg-navy-950 p-4">
              <img
                src={inspection.evidenceImage}
                alt="Package principal display panel"
                className="block w-full rounded-sm shadow-modal transition-opacity group-hover:opacity-90"
              />
            </Link>
            <div className="flex items-center justify-between border-t border-line px-5 py-3">
              <span className="text-xs text-ink-500">Principal display panel</span>
              <Link to={`/evidence/${inspection.id}`} className="text-13 font-medium text-brand-600 hover:text-brand-700">
                Open viewer
              </Link>
            </div>
          </section>

          <section className="panel px-5 py-5">
            <h2 className="panel-title mb-4">Processing timeline</h2>
            <Timeline steps={inspection.timeline} />
          </section>
        </div>
      </div>

      <ReassignDialog
        open={reassignOpen}
        inspection={inspection}
        onClose={() => setReassignOpen(false)}
        onReassigned={() => {
          setReassignOpen(false);
          refetch();
        }}
      />
    </div>
  );
}

function Figure({ label, value }) {
  return (
    <div className="min-w-0">
      <p className="text-micro font-semibold uppercase text-ink-400">{label}</p>
      <div className="mt-1">{value}</div>
    </div>
  );
}

/**
 * Admin-only move of an inspection to a different inspector and/or zone.
 * Mirrors the zone-then-inspector cascade from New Inspection; defaults to
 * the inspection's current zone so reassigning within the same zone is a
 * single dropdown change.
 */
function ReassignDialog({ open, inspection, onClose, onReassigned }) {
  const [zones, setZones] = useState([]);
  const [zonesLoading, setZonesLoading] = useState(false);
  const [zonesError, setZonesError] = useState('');
  const [selectedZoneId, setSelectedZoneId] = useState('');

  const [inspectors, setInspectors] = useState([]);
  const [inspectorsLoading, setInspectorsLoading] = useState(false);
  const [inspectorsError, setInspectorsError] = useState('');
  const [selectedInspectorId, setSelectedInspectorId] = useState('');

  const [reason, setReason] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [seededFor, setSeededFor] = useState(null);

  useEffect(() => {
    if (!open) return undefined;
    let cancelled = false;
    setZonesLoading(true);
    setZonesError('');
    inspectionService
      .getZones()
      .then((rows) => {
        if (!cancelled) setZones(rows);
      })
      .catch(() => {
        if (!cancelled) setZonesError('Zones could not be loaded.');
      })
      .finally(() => {
        if (!cancelled) setZonesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open]);

  // Seed the current zone/inspector once per time the dialog opens for this inspection.
  useEffect(() => {
    if (open && inspection && seededFor !== inspection.id) {
      setSeededFor(inspection.id);
      setSelectedZoneId(inspection.zoneId || '');
      setSelectedInspectorId(inspection.inspectorId || '');
      setReason('');
      setSubmitError('');
    }
    if (!open && seededFor !== null) setSeededFor(null);
  }, [open, inspection, seededFor]);

  useEffect(() => {
    if (!open || !selectedZoneId) {
      setInspectors([]);
      return undefined;
    }
    let cancelled = false;
    const zoneName = zones.find((z) => z.id === selectedZoneId)?.name;
    setInspectorsLoading(true);
    setInspectorsError('');
    inspectionService
      .getInspectors({ zone: zoneName, status: 'ACTIVE' })
      .then((rows) => {
        if (!cancelled) setInspectors(rows);
      })
      .catch(() => {
        if (!cancelled) setInspectorsError('Inspectors could not be loaded for this zone.');
      })
      .finally(() => {
        if (!cancelled) setInspectorsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, selectedZoneId, zones]);

  const selectedInspector = useMemo(
    () => inspectors.find((i) => i.userId === selectedInspectorId) || null,
    [inspectors, selectedInspectorId],
  );

  const unchanged =
    selectedInspectorId === (inspection?.inspectorId || '') && selectedZoneId === (inspection?.zoneId || '');

  const submit = async () => {
    if (!selectedInspectorId) {
      setSubmitError('Select an inspector to assign this inspection to.');
      return;
    }
    setSubmitError('');
    setSubmitting(true);
    try {
      await inspectionService.reassignInspection(inspection.id, {
        inspectorId: selectedInspectorId,
        zoneId: selectedZoneId || undefined,
        reason,
      });
      onReassigned();
    } catch (err) {
      setSubmitError(err.message || 'The inspection could not be reassigned. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Reassign inspection"
      subtitle={inspection ? `${inspection.id} · ${inspection.establishment || inspection.productName}` : ''}
      footer={
        <>
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancel
          </button>
          <button type="button" disabled={submitting || unchanged} onClick={submit} className="btn-primary">
            {submitting && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
            Reassign
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {submitError && (
          <div className="flex items-start gap-3 rounded-lg border border-danger-100 bg-danger-50 px-4 py-3">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
            <p className="text-13 text-danger-700">{submitError}</p>
          </div>
        )}

        <div className="flex items-center gap-3 rounded-lg border border-line bg-canvas/60 px-4 py-3">
          <span className="eyebrow">Currently</span>
          <span className="text-13 font-medium text-ink-900">
            {inspection?.inspector} · {inspection?.zone}
          </span>
        </div>

        <div>
          <label className="field-label" htmlFor="reassign-zone">
            Zone
          </label>
          {zonesError ? (
            <p className="text-13 text-danger-600">{zonesError}</p>
          ) : (
            <select
              id="reassign-zone"
              value={selectedZoneId}
              disabled={zonesLoading}
              onChange={(e) => {
                setSelectedZoneId(e.target.value);
                setSelectedInspectorId('');
              }}
              className="select input-lg"
            >
              <option value="">{zonesLoading ? 'Loading zones…' : 'Select a zone'}</option>
              {zones.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name}
                </option>
              ))}
            </select>
          )}
        </div>

        <div>
          <label className="field-label" htmlFor="reassign-inspector">
            Inspector
          </label>
          {!selectedZoneId ? (
            <div className="rounded-lg border border-line bg-canvas/60 px-4 py-3">
              <p className="text-13 text-ink-500">Select a zone first to load the inspectors posted there.</p>
            </div>
          ) : inspectorsLoading ? (
            <div className="flex items-center gap-2 rounded-lg border border-line px-4 py-3">
              <Loader2 className="h-4 w-4 animate-spin text-ink-400" strokeWidth={2} />
              <p className="text-13 text-ink-500">Loading inspectors…</p>
            </div>
          ) : inspectorsError ? (
            <p className="text-13 text-danger-600">{inspectorsError}</p>
          ) : inspectors.length === 0 ? (
            <div className="rounded-lg border border-line">
              <EmptyState
                compact
                icon={UserCheck}
                title="No active inspectors in this zone"
                description="Post an inspector to this zone from Inspector Management, or choose a different zone."
              />
            </div>
          ) : (
            <select
              id="reassign-inspector"
              value={selectedInspectorId}
              onChange={(e) => setSelectedInspectorId(e.target.value)}
              className="select input-lg"
            >
              <option value="">Select an inspector</option>
              {inspectors.map((i) => (
                <option key={i.userId} value={i.userId}>
                  {i.fullName || i.name} ({i.id})
                </option>
              ))}
            </select>
          )}
        </div>

        {selectedInspector && (
          <div className="rounded-lg border border-line bg-canvas/60 px-4 py-3.5">
            <p className="eyebrow mb-2">Officer workload</p>
            <div className="flex items-center gap-2 text-13 text-ink-700">
              <MapPin className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
              {selectedInspector.zone} · {selectedInspector.rank}
            </div>
            <p className="mt-2 tabular text-13 text-ink-800">
              {selectedInspector.activeAssignments} open assignment
              {selectedInspector.activeAssignments === 1 ? '' : 's'}
            </p>
          </div>
        )}

        <div>
          <label className="field-label" htmlFor="reassign-reason">
            Reason <span className="text-ink-400">(kept in the audit trail)</span>
          </label>
          <textarea
            id="reassign-reason"
            rows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Original inspector on leave"
            className="input h-auto py-2.5 text-13"
          />
        </div>
      </div>
    </Modal>
  );
}
