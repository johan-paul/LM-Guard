import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';

import PageHeader from '../components/PageHeader';
import StatusBadge from '../components/StatusBadge';
import { useAuth } from '../context/AuthContext';
import { useRules } from '../hooks/useData';
import { initials } from '../utils/format';

export default function Settings() {
  const { user } = useAuth();
  const { ruleset } = useRules();
  // Notification preferences and review thresholds below are not yet backed by
  // anything real: there is no notification delivery system on the backend,
  // and the risk-scoring thresholds are static configuration, not a runtime
  // setting an API could change. They're shown for the officer to see the
  // current shape of the screen, not persisted - documented, not disguised
  // as saved settings.
  const [prefs, setPrefs] = useState({
    highRiskAlerts: true,
    dailyDigest: true,
    escalationCopies: false,
    lowConfidenceReview: true,
  });
  const [thresholds, setThresholds] = useState({ review: 70, highRisk: 70 });

  const toggle = (key) => setPrefs((p) => ({ ...p, [key]: !p[key] }));

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" subtitle="Officer profile, review thresholds and notification preferences." />

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
        <div className="space-y-5 xl:col-span-7">
          {/* Profile */}
          <section className="panel-flush">
            <div className="panel-header">
              <h2 className="panel-title">Officer profile</h2>
            </div>
            <div className="flex items-center gap-4 px-5 py-5">
              <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-navy-900 text-[16px] font-semibold text-white">
                {initials(user?.name || 'Officer')}
              </span>
              <div className="min-w-0">
                <p className="text-[17px] font-semibold text-ink-900">{user?.name || 'Officer'}</p>
                <p className="text-13 text-ink-500">{user?.role || 'Inspecting Officer'}</p>
              </div>
            </div>
            <dl className="grid grid-cols-1 gap-px border-t border-line bg-line sm:grid-cols-2">
              <Field label="Badge ID" value={user?.badgeId || 'LM-INS-014'} mono />
              <Field label="Assigned zone" value={user?.zone || 'Coimbatore North'} />
              <Field label="Username" value={user?.username || '—'} mono />
              <Field label="Account ID" value={user?.id || '—'} mono />
            </dl>
          </section>

          {/* Notifications */}
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Notifications</h2>
                <p className="panel-subtitle">
                  Which findings should reach you, and how quickly · not yet wired to a delivery
                  system, so these are not saved
                </p>
              </div>
            </div>
            <ul className="divide-y divide-line-soft">
              <ToggleRow
                label="High-risk findings"
                description="Notify immediately when a case scores 70 or above"
                checked={prefs.highRiskAlerts}
                onChange={() => toggle('highRiskAlerts')}
              />
              <ToggleRow
                label="Daily compliance digest"
                description="A morning summary of the previous day's inspections"
                checked={prefs.dailyDigest}
                onChange={() => toggle('dailyDigest')}
              />
              <ToggleRow
                label="Copies of escalations"
                description="Receive cases escalated by other officers in your zone"
                checked={prefs.escalationCopies}
                onChange={() => toggle('escalationCopies')}
              />
              <ToggleRow
                label="Low-confidence findings"
                description="Include advisory findings below the decision threshold"
                checked={prefs.lowConfidenceReview}
                onChange={() => toggle('lowConfidenceReview')}
              />
            </ul>
          </section>
        </div>

        <div className="space-y-5 xl:col-span-5">
          {/* Thresholds */}
          <section className="panel-flush">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Review thresholds</h2>
                <p className="panel-subtitle">
                  Where the engine stops and asks for a human decision · configured on the server
                  today, so changes here are not saved
                </p>
              </div>
            </div>
            <div className="space-y-6 px-5 py-5">
              <Slider
                label="Confidence floor for automatic flagging"
                hint="Findings below this confidence are shown as advisory only"
                value={thresholds.review}
                onChange={(v) => setThresholds((t) => ({ ...t, review: v }))}
                suffix="%"
              />
              <Slider
                label="High-risk score threshold"
                hint="Products at or above this score enter the priority queue"
                value={thresholds.highRisk}
                onChange={(v) => setThresholds((t) => ({ ...t, highRisk: v }))}
                min={40}
                max={95}
              />
            </div>
          </section>

          {/* Engine */}
          <section className="panel-flush">
            <div className="panel-header">
              <h2 className="panel-title">Rule engine</h2>
            </div>
            <dl className="grid grid-cols-2 gap-px bg-line">
              <Field label="Active ruleset" value={ruleset?.version || '—'} mono />
              <Field label="Status" value={<StatusBadge status={ruleset?.status} />} />
              <Field label="Active rules" value={String(ruleset?.activeRules ?? '—')} />
              <Field label="Total in registry" value={String(ruleset?.totalRules ?? '—')} />
            </dl>
            <div className="flex items-start gap-3 border-t border-line px-5 py-4">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-ink-400" strokeWidth={1.9} />
              <p className="text-xs leading-relaxed text-ink-500">
                Rule evaluation is deterministic and versioned. Every finding stores the ruleset version it was produced
                under, so historical decisions stay reproducible.
              </p>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

function Field({ label, value, mono = false }) {
  return (
    <div className="bg-surface px-5 py-3.5">
      <dt className="text-micro font-semibold uppercase text-ink-400">{label}</dt>
      <dd className={`mt-1 text-13 text-ink-900 ${mono ? 'font-mono' : 'font-medium'}`}>{value}</dd>
    </div>
  );
}

function ToggleRow({ label, description, checked, onChange }) {
  return (
    <li className="flex items-start justify-between gap-6 px-5 py-3.5">
      <div className="min-w-0">
        <p className="text-13 font-medium text-ink-900">{label}</p>
        <p className="mt-0.5 text-xs text-ink-500">{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={onChange}
        className={`relative mt-0.5 h-5 w-9 shrink-0 rounded-full transition-colors duration-150 ${
          checked ? 'bg-brand-600' : 'bg-line-strong'
        }`}
      >
        <span
          className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow-card transition-transform duration-150 ${
            checked ? 'translate-x-[18px]' : 'translate-x-0.5'
          }`}
        />
      </button>
    </li>
  );
}

function Slider({ label, hint, value, onChange, min = 50, max = 95, suffix = '' }) {
  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between gap-4">
        <label className="text-13 font-medium text-ink-900">{label}</label>
        <span className="tabular text-13 font-semibold text-ink-900">
          {value}
          {suffix}
        </span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-1 w-full cursor-pointer appearance-none rounded-full bg-line accent-brand-600"
      />
      <p className="mt-2 text-xs text-ink-500">{hint}</p>
    </div>
  );
}
