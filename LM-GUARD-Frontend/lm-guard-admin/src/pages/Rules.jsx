import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
  Scale,
  Info,
  ArrowRight,
  CircleDot,
  Lock,
  LockOpen,
  Plus,
  Pencil,
  Loader2,
  AlertCircle,
  GitBranch,
} from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import DataTable, { PrimaryCell } from '../components/DataTable';
import SearchFilterBar from '../components/SearchFilterBar';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import { useRules, useViolations, useDebounced } from '../hooks/useData';
import { inspectionService } from '../services/inspectionService';
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
  const { ruleset, categories, rules, loading, refetch } = useRules({ search: debouncedSearch, category, status });

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

      <VersionManager currentVersion={ruleset?.version} onChanged={refetch} />

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

/* ------------------------------------------------------------------ */
/* Version management (admin: view/edit/publish ruleset versions)      */
/* ------------------------------------------------------------------ */

const RULE_TYPES = ['REQUIRED_FIELD', 'PATTERN_MATCH', 'NUMERIC_RANGE', 'MIN_LENGTH'];
const SEVERITIES = ['MINOR', 'MAJOR', 'CRITICAL'];

const EMPTY_RULE_FORM = {
  ruleCode: '',
  ruleName: '',
  description: '',
  fieldName: '',
  ruleType: 'REQUIRED_FIELD',
  ruleDefinition: '{\n  "required": true,\n  "minConfidence": 0.70,\n  "finding": ""\n}',
  severity: 'MAJOR',
  active: true,
};

/**
 * Admin-only ruleset version management: view any version's rules, publish a new draft version
 * by cloning an existing one, and edit/activate individual rules - but only while that version
 * is still unlocked (no inspection has been judged under it yet). Once locked, the backend
 * refuses every mutation and this panel switches to a read-only + "publish a new version" view.
 */
function VersionManager({ currentVersion, onChanged }) {
  const [versionInput, setVersionInput] = useState('');
  const [loadedVersion, setLoadedVersion] = useState(null);
  const [ruleSet, setRuleSet] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null); // null | 'NEW' | a rule object
  const [publishing, setPublishing] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = async (version) => {
    setLoading(true);
    setError(null);
    try {
      const data = await inspectionService.getRuleSet(version);
      setRuleSet(data);
      setLoadedVersion(data.version);
    } catch (err) {
      setError(err.message);
      setRuleSet(null);
    } finally {
      setLoading(false);
    }
  };

  // Follow the page's active version until the admin explicitly loads a different one.
  useEffect(() => {
    if (currentVersion && loadedVersion === null) {
      setVersionInput(currentVersion);
      load(currentVersion);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentVersion]);

  const saveRule = async (form) => {
    setBusy(true);
    setError(null);
    try {
      await inspectionService.upsertRule({ ...form, version: loadedVersion });
      setEditing(null);
      await load(loadedVersion);
      onChanged?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (rule) => {
    setBusy(true);
    setError(null);
    try {
      await inspectionService.setRuleActive(rule.id, !rule.active);
      await load(loadedVersion);
      onChanged?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const publish = async (newVersion) => {
    setBusy(true);
    setError(null);
    try {
      const data = await inspectionService.publishRuleset(loadedVersion, newVersion);
      setPublishing(false);
      setRuleSet(data);
      setLoadedVersion(data.version);
      setVersionInput(data.version);
      onChanged?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel-flush">
      <div className="panel-header flex-wrap gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <GitBranch className="h-4 w-4 shrink-0 text-ink-400" strokeWidth={2} />
          <h2 className="panel-title">Version management</h2>
        </div>
        <form
          className="flex items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (versionInput.trim()) load(versionInput.trim());
          }}
        >
          <input
            value={versionInput}
            onChange={(e) => setVersionInput(e.target.value)}
            placeholder="Version identifier"
            className="input h-8 w-48 font-mono text-[12px]"
          />
          <button type="submit" className="btn-secondary h-8 px-3 text-[12px]">
            Load
          </button>
        </form>
      </div>

      {error && (
        <div className="flex items-start gap-2.5 border-b border-line bg-danger-50 px-5 py-3">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
          <p className="text-13 text-danger-700">{error}</p>
        </div>
      )}

      {loading ? (
        <div className="px-5 py-8 text-center text-13 text-ink-400">Loading ruleset…</div>
      ) : !ruleSet ? (
        <div className="px-5 py-8 text-center text-13 text-ink-400">Enter a version and load it to manage rules.</div>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-3.5">
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-13 font-semibold text-ink-900">{ruleSet.version}</span>
              {ruleSet.locked ? (
                <span className="badge border-line bg-canvas text-ink-500">
                  <Lock className="h-3 w-3" strokeWidth={2} /> Locked
                </span>
              ) : (
                <span className="badge border-success-100 bg-success-50 text-success-700">
                  <LockOpen className="h-3 w-3" strokeWidth={2} /> Draft — editable
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {!ruleSet.locked && (
                <button type="button" onClick={() => setEditing('NEW')} className="btn-secondary h-8 px-3 text-[12px]">
                  <Plus className="h-3.5 w-3.5" strokeWidth={2} /> Add rule
                </button>
              )}
              <button type="button" onClick={() => setPublishing(true)} className="btn-primary h-8 px-3 text-[12px]">
                <GitBranch className="h-3.5 w-3.5" strokeWidth={2} /> Publish new version from this one
              </button>
            </div>
          </div>

          {ruleSet.locked && (
            <div className="flex items-start gap-2.5 border-b border-line bg-canvas px-5 py-3">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-ink-400" strokeWidth={2} />
              <p className="text-13 text-ink-600">
                An inspection has already been judged under this version, so its rules are locked and cannot be
                edited — that keeps the recorded verdict reproducible. Publish a new version to make changes.
              </p>
            </div>
          )}

          <ul className="divide-y divide-line-soft">
            {ruleSet.rules.map((rule) => (
              <li key={rule.id || rule.ruleCode} className="flex items-center gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-13 font-medium text-ink-900">{rule.ruleName}</p>
                  <p className="font-mono text-[12px] text-ink-500">
                    {rule.ruleCode} · {rule.fieldName} · {rule.ruleType}
                  </p>
                </div>
                <span
                  className={`badge ${
                    rule.active ? 'border-success-100 bg-success-50 text-success-700' : 'border-line bg-canvas text-ink-500'
                  }`}
                >
                  {rule.active ? 'Active' : 'Inactive'}
                </span>
                {!ruleSet.locked && (
                  <div className="flex shrink-0 items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => setEditing(rule)}
                      className="rounded-md p-1.5 text-ink-400 transition-colors hover:bg-canvas hover:text-ink-900"
                      aria-label="Edit rule"
                    >
                      <Pencil className="h-3.5 w-3.5" strokeWidth={2} />
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => toggleActive(rule)}
                      className="btn-secondary h-7 px-2.5 text-[11px]"
                    >
                      {rule.active ? 'Deactivate' : 'Activate'}
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      <RuleEditForm open={!!editing} rule={editing === 'NEW' ? null : editing} busy={busy} onClose={() => setEditing(null)} onSubmit={saveRule} />

      <PublishForm open={publishing} sourceVersion={loadedVersion} busy={busy} onClose={() => setPublishing(false)} onSubmit={publish} />
    </section>
  );
}

function RuleEditForm({ open, rule, busy, onClose, onSubmit }) {
  const [form, setForm] = useState(EMPTY_RULE_FORM);
  const [errors, setErrors] = useState({});
  const [seeded, setSeeded] = useState(null);

  const key = rule?.id || 'NEW';
  if (open && seeded !== key) {
    setSeeded(key);
    setForm(
      rule
        ? {
            ruleCode: rule.ruleCode,
            ruleName: rule.ruleName,
            description: rule.description || '',
            fieldName: rule.fieldName,
            ruleType: rule.ruleType,
            ruleDefinition: rule.ruleDefinition,
            severity: rule.severity,
            active: rule.active,
          }
        : EMPTY_RULE_FORM,
    );
    setErrors({});
  }
  if (!open && seeded !== null) setSeeded(null);

  const set = (field) => (e) => {
    const value = field === 'active' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const submit = (e) => {
    e.preventDefault();
    const next = {};
    if (!form.ruleCode.trim()) next.ruleCode = 'Required';
    if (!form.ruleName.trim()) next.ruleName = 'Required';
    if (!form.fieldName.trim()) next.fieldName = 'Required';
    try {
      JSON.parse(form.ruleDefinition);
    } catch {
      next.ruleDefinition = 'Must be valid JSON';
    }
    setErrors(next);
    if (Object.keys(next).length) return;
    onSubmit(form);
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={rule ? 'Edit rule' : 'Add rule'}
      subtitle={rule ? rule.ruleCode : 'Added to the version currently loaded above'}
      width="max-w-xl"
      footer={
        <>
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancel
          </button>
          <button type="submit" form="rule-edit-form" disabled={busy} className="btn-primary">
            {busy && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
            {rule ? 'Save changes' : 'Add rule'}
          </button>
        </>
      }
    >
      <form id="rule-edit-form" onSubmit={submit} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <RuleFormField
            label="Rule code"
            id="ruleCode"
            value={form.ruleCode}
            onChange={set('ruleCode')}
            error={errors.ruleCode}
            placeholder="LM-PC-6-1-a-MANUFACTURER"
            mono
          />
          <RuleFormField
            label="Field"
            id="fieldName"
            value={form.fieldName}
            onChange={set('fieldName')}
            error={errors.fieldName}
            placeholder="MANUFACTURER"
            mono
          />
        </div>
        <RuleFormField
          label="Rule name"
          id="ruleName"
          value={form.ruleName}
          onChange={set('ruleName')}
          error={errors.ruleName}
          placeholder="Manufacturer name and address declared"
        />
        <div>
          <label className="field-label" htmlFor="description">
            Description
          </label>
          <textarea id="description" value={form.description} onChange={set('description')} rows={2} className="input" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="field-label" htmlFor="ruleType">
              Check type
            </label>
            <select id="ruleType" value={form.ruleType} onChange={set('ruleType')} className="select input-lg">
              {RULE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="field-label" htmlFor="severity">
              Severity
            </label>
            <select id="severity" value={form.severity} onChange={set('severity')} className="select input-lg">
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className="field-label" htmlFor="ruleDefinition">
            Rule definition (JSON)
          </label>
          <textarea
            id="ruleDefinition"
            value={form.ruleDefinition}
            onChange={set('ruleDefinition')}
            rows={5}
            className={`input font-mono text-[12px] ${errors.ruleDefinition ? 'border-danger-300' : ''}`}
          />
          {errors.ruleDefinition && <p className="mt-1 text-[12px] text-danger-600">{errors.ruleDefinition}</p>}
          <p className="mt-1 text-[11px] text-ink-400">
            pattern, minConfidence, min, max, minLength, finding, remediation — as applicable to the check type.
          </p>
        </div>
        <label className="flex items-center gap-2 text-13 text-ink-700">
          <input type="checkbox" checked={form.active} onChange={set('active')} />
          Active (participates in evaluation)
        </label>
      </form>
    </Modal>
  );
}

function PublishForm({ open, sourceVersion, busy, onClose, onSubmit }) {
  const [newVersion, setNewVersion] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setNewVersion('');
      setError('');
    }
  }, [open]);

  const submit = (e) => {
    e.preventDefault();
    if (!newVersion.trim()) {
      setError('Enter a version identifier');
      return;
    }
    onSubmit(newVersion.trim());
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Publish new version"
      subtitle={`Clones every rule from ${sourceVersion || '—'}`}
      footer={
        <>
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancel
          </button>
          <button type="submit" form="publish-form" disabled={busy} className="btn-primary">
            {busy && <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} />}
            Publish
          </button>
        </>
      }
    >
      <form id="publish-form" onSubmit={submit} className="space-y-3">
        <RuleFormField
          label="New version identifier"
          id="newVersion"
          value={newVersion}
          onChange={(e) => {
            setNewVersion(e.target.value);
            setError('');
          }}
          error={error}
          placeholder="LM-PC-2011-v2"
          mono
        />
        <p className="text-[12px] text-ink-500">
          The new version starts fully editable. Amend whichever rules actually changed and leave the rest as
          cloned — it becomes locked itself only once an inspection is judged under it.
        </p>
      </form>
    </Modal>
  );
}

function RuleFormField({ label, id, value, onChange, error, type = 'text', placeholder, mono = false }) {
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
        className={`input input-lg ${mono ? 'font-mono' : ''} ${error ? 'border-danger-300' : ''}`}
      />
      {error && <p className="mt-1 text-[12px] text-danger-600">{error}</p>}
    </div>
  );
}
