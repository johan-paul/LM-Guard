import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck, AlertCircle, Loader2, ArrowRight } from 'lucide-react';

import { useAuth } from '../context/AuthContext';
import AuthAside from '../components/AuthAside';
import { INSPECTORS } from '../data/inspections';

const ZONES = [...new Set(INSPECTORS.map((i) => i.zone))];

export default function Register() {
  const navigate = useNavigate();
  const { register, loading } = useAuth();

  const [form, setForm] = useState({
    name: '',
    email: '',
    badgeId: '',
    zone: ZONES[0],
    password: '',
    confirm: '',
  });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState(false);

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  const validate = () => {
    const next = {};
    if (form.name.trim().length < 3) next.name = 'Enter the officer’s full name';
    if (!EMAIL_RE.test(form.email.trim())) next.email = 'Enter a valid email address';
    if (form.password.length < 8) next.password = 'Use at least 8 characters';
    if (form.password !== form.confirm) next.confirm = 'Passwords do not match';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    setFormError('');
    if (!validate()) return;
    try {
      // Provisions an INSPECTOR account on the backend. Badge/zone are not
      // yet backend-persisted fields (no Zone/Inspector-profile entity
      // exists server-side today), so they aren't sent here. Registering
      // does not sign the caller into this console — that account belongs
      // to the LM-GUARD field application, not the admin console.
      await register(form);
      setSuccess(true);
    } catch (err) {
      if (err.field) setErrors((prev) => ({ ...prev, [err.field]: err.message }));
      else setFormError(err.message || 'Registration could not be completed.');
    }
  };

  if (success) {
    return (
      <div className="grid min-h-screen lg:grid-cols-2">
        <AuthAside />
        <div className="flex items-center justify-center px-5 py-10 sm:px-8">
          <div className="w-full max-w-[420px] text-center">
            <h1 className="text-[24px] font-semibold tracking-tight text-ink-900">Account created</h1>
            <p className="mt-2 text-13 text-ink-500">
              Sign in with these credentials in the LM-GUARD field application. This console does not accept
              inspecting-officer sign-ins.
            </p>
            <button type="button" className="btn-primary btn-lg mt-6 w-full" onClick={() => navigate('/login', { replace: true })}>
              Back to sign in
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <AuthAside />

      <div className="flex items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-[420px]">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-navy-900">
              <ShieldCheck className="h-5 w-5 text-brand-400" strokeWidth={2} />
            </span>
            <div>
              <p className="text-[15px] font-semibold leading-tight text-ink-900">LM-GUARD</p>
              <p className="text-[11px] leading-tight text-ink-500">AI-Assisted Legal Metrology</p>
            </div>
          </div>

          <h1 className="text-[24px] font-semibold tracking-tight text-ink-900">Request officer access</h1>
          <p className="mt-1.5 text-13 text-ink-500">
            Accounts are provisioned for departmental personnel and audited against badge records.
          </p>

          {formError && (
            <div className="mt-5 flex items-start gap-2.5 rounded-lg border border-danger-100 bg-danger-50 px-3.5 py-3">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
              <p className="text-13 text-danger-700">{formError}</p>
            </div>
          )}

          <form onSubmit={submit} className="mt-6 space-y-4">
            <Field label="Full name" id="name" value={form.name} onChange={set('name')} error={errors.name} placeholder="S. Kumar" />

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                label="Email"
                id="email"
                type="email"
                value={form.email}
                onChange={set('email')}
                error={errors.email}
                placeholder="s.kumar@lmguard.gov.in"
              />
              <Field
                label="Badge ID"
                id="badgeId"
                value={form.badgeId}
                onChange={set('badgeId')}
                error={errors.badgeId}
                placeholder="LM-INS-014"
              />
            </div>

            <div>
              <label className="field-label" htmlFor="zone">
                Assigned zone
              </label>
              <select id="zone" value={form.zone} onChange={set('zone')} className="select input-lg">
                {ZONES.map((z) => (
                  <option key={z} value={z}>
                    {z}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                label="Password"
                id="password"
                type="password"
                value={form.password}
                onChange={set('password')}
                error={errors.password}
                placeholder="••••••••"
              />
              <Field
                label="Confirm password"
                id="confirm"
                type="password"
                value={form.confirm}
                onChange={set('confirm')}
                error={errors.confirm}
                placeholder="••••••••"
              />
            </div>

            <button type="submit" disabled={loading} className="btn-primary btn-lg w-full">
              {loading ? <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} /> : null}
              Create account
              {!loading && <ArrowRight className="h-4 w-4" strokeWidth={2} />}
            </button>
          </form>

          <p className="mt-6 text-center text-13 text-ink-500">
            Already registered?{' '}
            <Link to="/login" className="link-quiet">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

function Field({ label, id, value, onChange, error, type = 'text', placeholder }) {
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
