import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowRight, Loader2, MapPin, UserCheck } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import EmptyState from '../components/EmptyState';
import { inspectionService } from '../services/inspectionService';
import { plural } from '../utils/format';

const INSPECTION_TYPES = [
  { value: 'ROUTINE', label: 'Routine market inspection' },
  { value: 'COMPLAINT', label: 'Consumer complaint' },
  { value: 'FOLLOW_UP', label: 'Follow-up verification' },
  { value: 'DRIVE', label: 'Special enforcement drive' },
];

const PRIORITIES = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
];

/**
 * Admin "New Inspection": open a case and assign it to an inspector. The
 * admin never captures a product image or runs AI analysis here - that
 * belongs to the inspector's own six-step workflow in the field application,
 * once they open the inspection assigned to them.
 */
export default function NewInspection() {
  const navigate = useNavigate();

  // Inspection details
  const [establishment, setEstablishment] = useState('');
  const [address, setAddress] = useState('');
  const [inspectionType, setInspectionType] = useState('ROUTINE');
  const [priority, setPriority] = useState('MEDIUM');
  const [dueDate, setDueDate] = useState('');
  const [notes, setNotes] = useState('');

  // Assignment
  const [zones, setZones] = useState([]);
  const [zonesLoading, setZonesLoading] = useState(true);
  const [zonesError, setZonesError] = useState('');
  const [selectedZoneId, setSelectedZoneId] = useState('');

  const [inspectors, setInspectors] = useState([]);
  const [inspectorsLoading, setInspectorsLoading] = useState(false);
  const [inspectorsError, setInspectorsError] = useState('');
  const [selectedInspectorId, setSelectedInspectorId] = useState('');

  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setZonesLoading(true);
    setZonesError('');
    inspectionService
      .getZones()
      .then((rows) => {
        if (cancelled) return;
        setZones(rows);
      })
      .catch(() => {
        if (!cancelled) setZonesError('Zones could not be loaded. Check your connection and try again.');
      })
      .finally(() => {
        if (!cancelled) setZonesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedZoneId) {
      setInspectors([]);
      setSelectedInspectorId('');
      return undefined;
    }
    let cancelled = false;
    const zoneName = zones.find((z) => z.id === selectedZoneId)?.name;
    setInspectorsLoading(true);
    setInspectorsError('');
    setSelectedInspectorId('');
    inspectionService
      .getInspectors({ zone: zoneName, status: 'ACTIVE' })
      .then((rows) => {
        if (cancelled) return;
        setInspectors(rows);
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
  }, [selectedZoneId, zones]);

  const selectedInspector = useMemo(
    () => inspectors.find((i) => i.userId === selectedInspectorId) || null,
    [inspectors, selectedInspectorId],
  );

  const validate = () => {
    const next = {};
    if (!establishment.trim()) next.establishment = 'Enter the establishment or premises name';
    if (!selectedZoneId) next.zone = 'Select a zone';
    if (!selectedInspectorId) next.inspector = 'Select an inspector';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    setSubmitError('');
    if (!validate()) return;

    setSubmitting(true);
    try {
      const created = await inspectionService.createAssignedInspection({
        establishment: establishment.trim(),
        address: address.trim() || undefined,
        inspectionType,
        priority,
        dueDate: dueDate ? new Date(dueDate).toISOString() : undefined,
        notes: notes.trim() || undefined,
        zoneId: selectedZoneId,
        inspectorId: selectedInspectorId,
      });
      navigate(`/inspections/${created.id}`, { replace: true });
    } catch (err) {
      setSubmitError(err.message || 'The inspection could not be created. Check the details and try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="New Inspection"
        subtitle="Open a case and assign it to an inspector. Product identification, image capture and AI analysis happen in the field application once the inspector opens their assignment."
      />

      {submitError && (
        <div className="flex items-start gap-3 rounded-xl border border-danger-100 bg-danger-50 px-5 py-3.5">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
          <p className="text-13 text-danger-700">{submitError}</p>
        </div>
      )}

      <form onSubmit={submit} className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        {/* Inspection details */}
        <section className="panel-flush xl:col-span-7">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Inspection details</h2>
              <p className="panel-subtitle">What is being inspected, and why</p>
            </div>
          </div>

          <div className="space-y-4 px-5 py-5">
            <div>
              <label className="field-label" htmlFor="establishment">
                Establishment <span className="text-danger-600">*</span>
              </label>
              <input
                id="establishment"
                type="text"
                value={establishment}
                onChange={(e) => {
                  setEstablishment(e.target.value);
                  setErrors((prev) => ({ ...prev, establishment: undefined }));
                }}
                placeholder="e.g. SunFresh Retail Outlet"
                className={`input input-lg ${errors.establishment ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-600/15' : ''}`}
              />
              {errors.establishment && <p className="mt-1.5 text-xs text-danger-600">{errors.establishment}</p>}
            </div>

            <div>
              <label className="field-label" htmlFor="address">
                Address
              </label>
              <input
                id="address"
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="Premises address"
                className="input input-lg"
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="field-label" htmlFor="inspection-type">
                  Inspection type
                </label>
                <select
                  id="inspection-type"
                  value={inspectionType}
                  onChange={(e) => setInspectionType(e.target.value)}
                  className="select input-lg"
                >
                  {INSPECTION_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="field-label" htmlFor="priority">
                  Priority
                </label>
                <select
                  id="priority"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                  className="select input-lg"
                >
                  {PRIORITIES.map((p) => (
                    <option key={p.value} value={p.value}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="field-label" htmlFor="due-date">
                Due date
              </label>
              <input
                id="due-date"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="input input-lg"
              />
            </div>

            <div>
              <label className="field-label" htmlFor="notes">
                Instructions / notes
              </label>
              <textarea
                id="notes"
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Anything the inspector should know before starting"
                className="input h-auto py-2.5 text-13"
              />
            </div>
          </div>
        </section>

        {/* Assignment */}
        <section className="panel-flush xl:col-span-5">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Assignment</h2>
              <p className="panel-subtitle">Select a zone, then an inspector posted there</p>
            </div>
          </div>

          <div className="space-y-4 px-5 py-5">
            <div>
              <label className="field-label" htmlFor="zone">
                Zone <span className="text-danger-600">*</span>
              </label>
              {zonesError ? (
                <p className="text-13 text-danger-600">{zonesError}</p>
              ) : (
                <select
                  id="zone"
                  value={selectedZoneId}
                  disabled={zonesLoading}
                  onChange={(e) => {
                    setSelectedZoneId(e.target.value);
                    setErrors((prev) => ({ ...prev, zone: undefined }));
                  }}
                  className={`select input-lg ${errors.zone ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-600/15' : ''}`}
                >
                  <option value="">{zonesLoading ? 'Loading zones…' : 'Select a zone'}</option>
                  {zones.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name}
                    </option>
                  ))}
                </select>
              )}
              {errors.zone && <p className="mt-1.5 text-xs text-danger-600">{errors.zone}</p>}
            </div>

            <div>
              <label className="field-label" htmlFor="inspector">
                Inspector <span className="text-danger-600">*</span>
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
                  id="inspector"
                  value={selectedInspectorId}
                  onChange={(e) => {
                    setSelectedInspectorId(e.target.value);
                    setErrors((prev) => ({ ...prev, inspector: undefined }));
                  }}
                  className={`select input-lg ${errors.inspector ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-600/15' : ''}`}
                >
                  <option value="">Select an inspector</option>
                  {inspectors.map((i) => (
                    <option key={i.userId} value={i.userId}>
                      {i.fullName || i.name} ({i.id})
                    </option>
                  ))}
                </select>
              )}
              {errors.inspector && <p className="mt-1.5 text-xs text-danger-600">{errors.inspector}</p>}
            </div>

            {selectedInspector && (
              <div className="rounded-lg border border-line bg-canvas/60 px-4 py-3.5">
                <p className="eyebrow mb-2">Officer workload</p>
                <div className="flex items-center gap-2 text-13 text-ink-700">
                  <MapPin className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
                  {selectedInspector.zone} · {selectedInspector.rank}
                </div>
                <div className="mt-2 grid grid-cols-2 gap-3">
                  <div>
                    <p className="text-micro font-semibold uppercase text-ink-400">Open assignments</p>
                    <p className="tabular text-sm font-semibold text-ink-900">
                      {plural(selectedInspector.activeAssignments, 'case')}
                    </p>
                  </div>
                  <div>
                    <p className="text-micro font-semibold uppercase text-ink-400">Completed this month</p>
                    <p className="tabular text-sm font-semibold text-ink-900">
                      {selectedInspector.completedThisMonth}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="border-t border-line px-5 py-4">
            <button type="submit" disabled={submitting} className="btn-primary btn-lg w-full">
              {submitting && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
              Assign inspection
              {!submitting && <ArrowRight className="h-4 w-4" strokeWidth={2} />}
            </button>
          </div>
        </section>
      </form>
    </div>
  );
}
