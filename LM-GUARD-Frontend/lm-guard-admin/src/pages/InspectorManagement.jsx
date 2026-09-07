import React, { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  UserPlus,
  Download,
  Eye,
  Pencil,
  MapPin,
  PauseCircle,
  PlayCircle,
  Users,
  Mail,
  Phone,
  Loader2,
  AlertCircle,
  KeyRound,
} from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import DataTable, { PrimaryCell, Pagination } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import Modal from '../components/Modal';
import RowMenu from '../components/RowMenu';
import EmptyState from '../components/EmptyState';
import { CardSkeleton } from '../components/LoadingState';
import { useInspectors, useZoneSummary, usePagination, useDebounced } from '../hooks/useData';
import { inspectionService } from '../services/inspectionService';
import { INSPECTOR_RANKS } from '../data/inspectors';
import { formatDate, formatDateTime, formatNumber, initials, plural } from '../utils/format';

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];

const EMPTY_FORM = {
  name: '',
  fullName: '',
  rank: 'Inspecting Officer',
  email: '',
  phone: '',
  zone: '',
  status: 'ACTIVE',
};

export default function InspectorManagement() {
  const [search, setSearch] = useState('');
  const [zone, setZone] = useState('ALL');
  const [status, setStatus] = useState('ALL');

  const debouncedSearch = useDebounced(search);
  const { inspectors, loading, error, refetch } = useInspectors({ search: debouncedSearch, zone, status });
  const { zones, loading: zonesLoading, refetch: refetchZones } = useZoneSummary();
  const zoneNames = useMemo(() => zones.map((z) => z.name), [zones]);
  const pager = usePagination(inspectors, 8);

  /* Dialog state */
  const [detail, setDetail] = useState(null);
  const [editing, setEditing] = useState(null); // record being edited, or 'NEW'
  const [reassigning, setReassigning] = useState(null);
  const [deactivating, setDeactivating] = useState(null);
  const [busy, setBusy] = useState(false);
  const [credentials, setCredentials] = useState(null);

  const totals = useMemo(() => {
    const active = inspectors.filter((i) => i.status === 'ACTIVE');
    return {
      total: inspectors.length,
      active: active.length,
      inactive: inspectors.length - active.length,
      workload: active.reduce((s, i) => s + i.activeAssignments, 0),
    };
  }, [inspectors]);

  const maxWorkload = useMemo(
    () => Math.max(1, ...inspectors.map((i) => i.activeAssignments)),
    [inspectors],
  );

  const refreshAll = async () => {
    await Promise.all([refetch(), refetchZones()]);
  };

  const openDetail = async (row) => {
    setDetail({ loading: true, record: row });
    const full = await inspectionService.getInspectorById(row.id);
    setDetail({ loading: false, record: full });
  };

  const saveInspector = async (form) => {
    setBusy(true);
    try {
      if (editing === 'NEW') {
        const created = await inspectionService.createInspector(form);
        if (created?.temporaryPassword) {
          setCredentials({ id: created.id, email: created.email, password: created.temporaryPassword });
        }
      } else {
        await inspectionService.updateInspector(editing.id, form);
      }
      setEditing(null);
      await refreshAll();
    } finally {
      setBusy(false);
    }
  };

  const applyZone = async (nextZone) => {
    setBusy(true);
    try {
      await inspectionService.assignZone(reassigning.id, nextZone);
      setReassigning(null);
      await refreshAll();
    } finally {
      setBusy(false);
    }
  };

  const resetPassword = async (record) => {
    setBusy(true);
    try {
      const result = await inspectionService.resetInspectorPassword(record.id);
      setCredentials({ id: record.id, email: record.email, password: result.temporaryPassword, reset: true });
    } finally {
      setBusy(false);
    }
  };

  const applyStatus = async (record, nextStatus) => {
    setBusy(true);
    try {
      await inspectionService.setInspectorStatus(record.id, nextStatus);
      setDeactivating(null);
      await refreshAll();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inspector Management"
        subtitle="Directory of inspecting officers, the zone each is posted to, and their current workload."
        actions={
          <>
            <button type="button" className="btn-secondary btn-lg">
              <Download className="h-4 w-4" strokeWidth={1.9} />
              Export directory
            </button>
            <button type="button" onClick={() => setEditing('NEW')} className="btn-primary btn-lg">
              <UserPlus className="h-4 w-4" strokeWidth={2} />
              Add Inspector
            </button>
          </>
        }
      />

      {error && (
        <div className="flex items-center gap-3 rounded-lg border border-danger-200 bg-danger-50 px-4 py-3">
          <AlertCircle className="h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
          <p className="flex-1 text-13 text-danger-700">
            Couldn't load the inspector directory ({error}). The backend may still be starting up.
          </p>
          <button type="button" onClick={refreshAll} className="btn-secondary btn-sm">
            Retry
          </button>
        </div>
      )}

      {/* Roster summary */}
      <div className="grid grid-cols-2 divide-line rounded-xl border border-line bg-surface shadow-card sm:grid-cols-4 sm:divide-x">
        <SummaryCell label="Officers on roll" value={totals.total} tone="text-ink-900" />
        <SummaryCell label="Active" value={totals.active} tone="text-success-700" />
        <SummaryCell label="Inactive" value={totals.inactive} tone="text-ink-500" />
        <SummaryCell label="Open assignments" value={totals.workload} tone="text-brand-700" />
      </div>

      {/* Zone roster */}
      <section>
        <div className="mb-3 flex items-end justify-between gap-4">
          <div>
            <h2 className="text-section font-semibold text-ink-900">Zone roster</h2>
            <p className="mt-0.5 text-13 text-ink-500">
              Select a zone to filter the directory to the officers posted there.
            </p>
          </div>
          {zone !== 'ALL' && (
            <button type="button" onClick={() => setZone('ALL')} className="btn-ghost btn-sm">
              Show all zones
            </button>
          )}
        </div>

        {zonesLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <CardSkeleton key={i} height="h-[168px]" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
            {zones.map((z) => {
              const selected = zone === z.name;
              return (
                <button
                  key={z.id}
                  type="button"
                  onClick={() => setZone(selected ? 'ALL' : z.name)}
                  aria-pressed={selected}
                  className={`panel flex flex-col px-4 py-3.5 text-left transition-colors ${
                    selected ? 'border-brand-600 ring-1 ring-brand-600/20' : 'hover:border-line-strong'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-[11px] font-medium text-ink-400">{z.code}</span>
                    <span className="tabular text-[11px] text-ink-400">{plural(z.total, 'officer')}</span>
                  </div>

                  <p className="mt-1.5 text-13 font-semibold text-ink-900">{z.name}</p>

                  <div className="mt-2 flex items-center gap-3 text-[11px]">
                    <span className="flex items-center gap-1 text-success-700">
                      <span className="h-1.5 w-1.5 rounded-full bg-success-600" />
                      {z.active} active
                    </span>
                    {z.inactive > 0 && <span className="text-ink-400">{z.inactive} inactive</span>}
                  </div>

                  <ul className="mt-3 space-y-1 border-t border-line-soft pt-2.5">
                    {z.inspectors.slice(0, 4).map((i) => (
                      <li key={i.id} className="flex items-center justify-between gap-2">
                        <span
                          className={`truncate text-xs ${i.status === 'ACTIVE' ? 'text-ink-700' : 'text-ink-400 line-through'}`}
                        >
                          {i.name}
                        </span>
                        <span className="tabular shrink-0 text-[11px] text-ink-400">{i.activeAssignments}</span>
                      </li>
                    ))}
                    {z.inspectors.length > 4 && (
                      <li className="text-[11px] text-ink-400">+{z.inspectors.length - 4} more</li>
                    )}
                    {z.inspectors.length === 0 && <li className="text-[11px] text-ink-400">No officers posted</li>}
                  </ul>

                  <p className="mt-auto pt-3 text-[11px] text-ink-400">{z.workload} open assignments</p>
                </button>
              );
            })}
          </div>
        )}
      </section>

      {/* Directory */}
      <section className="panel-flush">
        <SearchFilterBar
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by name, officer ID, email or phone"
          onReset={() => {
            setSearch('');
            setZone('ALL');
            setStatus('ALL');
          }}
          filters={[
            {
              key: 'zone',
              label: 'Zone',
              value: zone,
              onChange: setZone,
              options: [{ value: 'ALL', label: 'All zones' }, ...zoneNames.map((z) => ({ value: z, label: z }))],
            },
            { key: 'status', label: 'Status', value: status, onChange: setStatus, options: STATUS_OPTIONS },
          ]}
          trailing={
            <span className="tabular text-13 text-ink-500">
              {zone === 'ALL' ? 'All zones' : zone} · {plural(inspectors.length, 'officer')}
            </span>
          }
        />

        <DataTable
          loading={loading}
          rows={pager.rows}
          onRowClick={openDetail}
          emptyTitle="No officers match these filters"
          emptyDescription="Clear the filters or select a different zone."
          columns={[
            {
              key: 'inspector',
              header: 'Inspector',
              render: (r) => (
                <span className="flex items-center gap-3">
                  <span
                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold ${
                      r.status === 'ACTIVE' ? 'bg-navy-900 text-white' : 'bg-line text-ink-500'
                    }`}
                  >
                    {initials(r.fullName || r.name)}
                  </span>
                  <PrimaryCell title={r.fullName || r.name} subtitle={r.rank} />
                </span>
              ),
            },
            { key: 'id', header: 'Officer ID', width: 118, render: (r) => <span className="mono-id">{r.id}</span> },
            {
              key: 'contact',
              header: 'Contact',
              width: 212,
              hideBelow: 'xl',
              render: (r) => (
                <span className="block min-w-0">
                  <span className="block truncate text-13 text-ink-700">{r.email}</span>
                  <span className="block font-mono text-[11px] text-ink-400">{r.phone}</span>
                </span>
              ),
            },
            {
              key: 'zone',
              header: 'Assigned zone',
              width: 158,
              hideBelow: 'md',
              render: (r) => (
                <span className="tag">
                  <MapPin className="h-3 w-3 text-ink-400" strokeWidth={2} />
                  {r.zone}
                </span>
              ),
            },
            { key: 'status', header: 'Status', width: 112, render: (r) => <StatusBadge status={r.status} /> },
            {
              key: 'workload',
              header: 'Workload',
              width: 112,
              hideBelow: 'lg',
              render: (r) => <Workload value={r.activeAssignments} max={maxWorkload} inactive={r.status !== 'ACTIVE'} />,
            },
            {
              key: 'lastActive',
              header: 'Last active',
              width: 120,
              hideBelow: 'xl',
              className: 'whitespace-nowrap',
              render: (r) => <span className="text-ink-600">{r.lastActive ? formatDate(r.lastActive) : 'Never'}</span>,
            },
            {
              key: 'actions',
              header: '',
              align: 'right',
              width: 56,
              render: (r) => (
                <RowMenu
                  label={`Actions for ${r.name}`}
                  items={[
                    { label: 'View details', icon: Eye, onSelect: () => openDetail(r) },
                    { label: 'Edit information', icon: Pencil, onSelect: () => setEditing(r) },
                    { label: 'Change zone', icon: MapPin, onSelect: () => setReassigning(r) },
                    { label: 'Reset password', icon: KeyRound, onSelect: () => resetPassword(r) },
                    r.status === 'ACTIVE'
                      ? { label: 'Deactivate', icon: PauseCircle, tone: 'danger', divider: true, onSelect: () => setDeactivating(r) }
                      : { label: 'Activate', icon: PlayCircle, tone: 'success', divider: true, onSelect: () => applyStatus(r, 'ACTIVE') },
                  ]}
                />
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
          label="officers"
        />
      </section>

      {/* Detail */}
      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title="Inspector details"
        subtitle={detail?.record?.id}
        width="max-w-2xl"
        footer={
          <>
            <button type="button" onClick={() => setDetail(null)} className="btn-secondary">
              Close
            </button>
            {detail?.record && (
              <button
                type="button"
                onClick={() => {
                  setEditing(detail.record);
                  setDetail(null);
                }}
                className="btn-primary"
              >
                <Pencil className="h-4 w-4" strokeWidth={1.9} /> Edit information
              </button>
            )}
          </>
        }
      >
        {detail?.record && <InspectorDetail record={detail.record} loading={detail.loading} />}
      </Modal>

      {/* Add / edit */}
      <InspectorForm
        open={!!editing}
        record={editing === 'NEW' ? null : editing}
        busy={busy}
        zoneNames={zoneNames}
        onClose={() => setEditing(null)}
        onSubmit={saveInspector}
      />

      {/* Change zone */}
      <ZoneDialog
        open={!!reassigning}
        record={reassigning}
        busy={busy}
        zoneNames={zoneNames}
        onClose={() => setReassigning(null)}
        onSubmit={applyZone}
      />

      {/* Generated credentials - for a newly added officer, or a password reset */}
      <Modal
        open={!!credentials}
        onClose={() => setCredentials(null)}
        title={credentials?.reset ? 'Password reset' : 'Inspector account created'}
        subtitle={credentials?.id}
        footer={
          <button type="button" onClick={() => setCredentials(null)} className="btn-primary">
            Done
          </button>
        }
      >
        <div className="space-y-4">
          <p className="text-13 leading-relaxed text-ink-600">
            {credentials?.reset ? (
              <>
                A new password was generated and the officer's old one no longer works. It is shown{' '}
                <span className="font-medium text-ink-900">once, here only</span> — hand it to the officer
                directly; it cannot be retrieved again from the console.
              </>
            ) : (
              <>
                A field-application account was created with a generated password. It is shown{' '}
                <span className="font-medium text-ink-900">once, here only</span> — hand it to the officer
                directly; it cannot be retrieved again from the console.
              </>
            )}
          </p>
          <dl className="grid grid-cols-1 gap-px overflow-hidden rounded-lg border border-line bg-line">
            <div className="bg-surface px-4 py-3">
              <dt className="text-micro font-semibold uppercase text-ink-400">Email</dt>
              <dd className="mt-1 text-13 font-medium text-ink-900">{credentials?.email}</dd>
            </div>
            <div className="bg-surface px-4 py-3">
              <dt className="text-micro font-semibold uppercase text-ink-400">Temporary password</dt>
              <dd className="mt-1 font-mono text-13 font-medium text-ink-900">{credentials?.password}</dd>
            </div>
          </dl>
        </div>
      </Modal>

      {/* Deactivate confirmation */}
      <Modal
        open={!!deactivating}
        onClose={() => setDeactivating(null)}
        title="Deactivate inspector"
        subtitle={deactivating ? `${deactivating.fullName || deactivating.name} · ${deactivating.id}` : ''}
        footer={
          <>
            <button type="button" onClick={() => setDeactivating(null)} className="btn-secondary">
              Cancel
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => applyStatus(deactivating, 'INACTIVE')}
              className="btn-danger"
            >
              {busy && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
              Deactivate
            </button>
          </>
        }
      >
        <div className="flex items-start gap-3">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-warning-600" strokeWidth={2} />
          <p className="text-13 leading-relaxed text-ink-600">
            The officer will lose access to the field application and their{' '}
            <span className="font-medium text-ink-900">
              {deactivating ? plural(deactivating.activeAssignments, 'open assignment') : 'open assignments'}
            </span>{' '}
            will be released for reallocation within {deactivating?.zone}. Inspection records they have already filed
            are retained.
          </p>
        </div>
      </Modal>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Sub-components                                                      */
/* ------------------------------------------------------------------ */

function SummaryCell({ label, value, tone }) {
  return (
    <div className="px-5 py-4">
      <p className="eyebrow">{label}</p>
      <p className={`tabular mt-1.5 text-[22px] font-bold leading-7 ${tone}`}>{formatNumber(value)}</p>
    </div>
  );
}

function Workload({ value, max, inactive }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className={`tabular w-[18px] shrink-0 text-right text-13 font-semibold ${inactive ? 'text-ink-400' : 'text-ink-900'}`}>
        {value}
      </span>
      <span className="h-1 w-[56px] shrink-0 overflow-hidden rounded-full bg-line" aria-hidden="true">
        <span
          className={`block h-full rounded-full ${inactive ? 'bg-line-strong' : 'bg-navy-700'}`}
          style={{ width: `${(value / max) * 100}%` }}
        />
      </span>
    </div>
  );
}

function InspectorDetail({ record, loading }) {
  return (
    <div className="space-y-5">
      <div className="flex items-start gap-4">
        <span
          className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${
            record.status === 'ACTIVE' ? 'bg-navy-900 text-white' : 'bg-line text-ink-500'
          }`}
        >
          {initials(record.fullName || record.name)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2.5">
            <p className="text-[17px] font-semibold text-ink-900">{record.fullName || record.name}</p>
            <StatusBadge status={record.status} />
          </div>
          <p className="text-13 text-ink-500">
            {record.rank} · {record.zone}
          </p>
        </div>
      </div>

      <dl className="grid grid-cols-1 gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-2">
        <Field label="Officer ID" value={record.id} mono />
        <Field label="Assigned zone" value={record.zone} />
        <Field
          label="Email"
          value={
            <span className="flex items-center gap-1.5">
              <Mail className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
              <span className="truncate">{record.email}</span>
            </span>
          }
        />
        <Field
          label="Phone"
          value={
            <span className="flex items-center gap-1.5">
              <Phone className="h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
              {record.phone}
            </span>
          }
        />
        <Field label="Joined" value={formatDate(record.joinedOn)} />
        <Field label="Last active" value={record.lastActive ? formatDateTime(record.lastActive) : 'Never'} />
      </dl>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Figure label="Open assignments" value={record.activeAssignments} />
        <Figure label="Completed this month" value={record.completedThisMonth} />
        <Figure label="Records filed" value={record.recordsFiled ?? '—'} />
        <Figure label="Awaiting review" value={record.openRecords ?? '—'} tone="text-warning-700" />
      </div>

      <div>
        <p className="eyebrow mb-2">Recent inspection records</p>
        {loading ? (
          <p className="text-13 text-ink-500">Loading records…</p>
        ) : record.inspections?.length ? (
          <ul className="divide-y divide-line-soft overflow-hidden rounded-lg border border-line">
            {record.inspections.slice(0, 5).map((i) => (
              <li key={i.id}>
                <Link to={`/inspections/${i.id}`} className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-canvas">
                  <span className="mono-id shrink-0">{i.id}</span>
                  <span className="min-w-0 flex-1 truncate text-13 text-ink-700">{i.productName}</span>
                  <StatusBadge status={i.status} />
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <div className="rounded-lg border border-line">
            <EmptyState
              compact
              icon={Users}
              title="No records filed"
              description="This officer has not submitted an inspection in the current period."
            />
          </div>
        )}
      </div>
    </div>
  );
}

function Field({ label, value, mono = false }) {
  return (
    <div className="bg-surface px-4 py-3">
      <dt className="text-micro font-semibold uppercase text-ink-400">{label}</dt>
      <dd className={`mt-1 min-w-0 truncate text-13 text-ink-900 ${mono ? 'font-mono' : 'font-medium'}`}>{value}</dd>
    </div>
  );
}

function Figure({ label, value, tone = 'text-ink-900' }) {
  return (
    <div className="rounded-lg border border-line px-4 py-3">
      <p className="text-micro font-semibold uppercase text-ink-400">{label}</p>
      <p className={`tabular mt-1 text-[20px] font-bold leading-6 ${tone}`}>{value}</p>
    </div>
  );
}

function InspectorForm({ open, record, busy, zoneNames, onClose, onSubmit }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [seeded, setSeeded] = useState(null);

  /* Seed the form when the dialog opens for a different record. */
  const key = record?.id || 'NEW';
  if (open && seeded !== key) {
    setSeeded(key);
    setForm(
      record
        ? {
            name: record.name,
            fullName: record.fullName || '',
            rank: record.rank,
            email: record.email,
            phone: record.phone,
            zone: record.zone,
            status: record.status,
          }
        : { ...EMPTY_FORM, zone: zoneNames[0] || '' },
    );
    setErrors({});
  }
  if (!open && seeded !== null) setSeeded(null);

  const set = (field) => (e) => {
    setForm((f) => ({ ...f, [field]: e.target.value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const submit = (e) => {
    e.preventDefault();
    const next = {};
    if (form.name.trim().length < 2) next.name = 'Enter the officer’s display name';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address';
    if (form.phone.trim().length < 8) next.phone = 'Enter a contact number';
    setErrors(next);
    if (Object.keys(next).length) return;
    onSubmit(form);
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={record ? 'Edit inspector' : 'Add inspector'}
      subtitle={record ? `${record.id} · ${record.zone}` : 'A new officer record is created in the directory'}
      footer={
        <>
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancel
          </button>
          <button type="submit" form="inspector-form" disabled={busy} className="btn-primary">
            {busy && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
            {record ? 'Save changes' : 'Add inspector'}
          </button>
        </>
      }
    >
      <form id="inspector-form" onSubmit={submit} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Display name" id="name" value={form.name} onChange={set('name')} error={errors.name} placeholder="S. Kumar" />
          <FormField
            label="Full name"
            id="fullName"
            value={form.fullName}
            onChange={set('fullName')}
            placeholder="Suresh Kumar"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="Email"
            id="email"
            type="email"
            value={form.email}
            onChange={set('email')}
            error={errors.email}
            placeholder="s.kumar@legalmetrology.example"
          />
          <FormField
            label="Phone number"
            id="phone"
            value={form.phone}
            onChange={set('phone')}
            error={errors.phone}
            placeholder="+91 98430 11204"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label className="field-label" htmlFor="rank">
              Rank
            </label>
            <select id="rank" value={form.rank} onChange={set('rank')} className="select input-lg">
              {INSPECTOR_RANKS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="field-label" htmlFor="zone">
              Assigned zone
            </label>
            <select id="zone" value={form.zone} onChange={set('zone')} className="select input-lg">
              {zoneNames.map((z) => (
                <option key={z} value={z}>
                  {z}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="field-label" htmlFor="status">
              Status
            </label>
            <select id="status" value={form.status} onChange={set('status')} className="select input-lg">
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>
      </form>
    </Modal>
  );
}

function ZoneDialog({ open, record, busy, zoneNames, onClose, onSubmit }) {
  const [zone, setZone] = useState('');
  const [seeded, setSeeded] = useState(null);

  if (open && record && seeded !== record.id) {
    setSeeded(record.id);
    setZone(record.zone);
  }
  if (!open && seeded !== null) setSeeded(null);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Change assigned zone"
      subtitle={record ? `${record.fullName || record.name} · ${record.id}` : ''}
      footer={
        <>
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancel
          </button>
          <button
            type="button"
            disabled={busy || zone === record?.zone}
            onClick={() => onSubmit(zone)}
            className="btn-primary"
          >
            {busy && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
            Reassign
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center gap-3 rounded-lg border border-line bg-canvas/60 px-4 py-3">
          <span className="eyebrow">Current</span>
          <span className="text-13 font-medium text-ink-900">{record?.zone}</span>
        </div>

        <div>
          <label className="field-label" htmlFor="new-zone">
            Reassign to
          </label>
          <select id="new-zone" value={zone} onChange={(e) => setZone(e.target.value)} className="select input-lg">
            {zoneNames.map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
          </select>
        </div>

        <p className="text-xs leading-relaxed text-ink-500">
          Open assignments stay with the officer and move to the new zone's queue. Inspection records already filed
          remain attributed to the zone they were carried out in.
        </p>
      </div>
    </Modal>
  );
}

function FormField({ label, id, value, onChange, error, type = 'text', placeholder }) {
  return (
    <div>
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        className={`input input-lg ${error ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-600/15' : ''}`}
      />
      {error && <p className="mt-1.5 text-xs text-danger-600">{error}</p>}
    </div>
  );
}
