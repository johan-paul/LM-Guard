import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { ShieldCheck, AlertCircle, Loader2, ArrowRight, Lock, Smartphone } from 'lucide-react';

import { useAuth } from '../context/AuthContext';
import { ADMIN_CREDENTIALS } from '../services/authData';
import AuthAside from '../components/AuthAside';

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, loading } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const from = location.state?.from?.pathname || '/dashboard';

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || 'Sign-in failed. Check the credentials and try again.');
    }
  };

  const useDemo = (cred) => {
    setEmail(cred.email);
    setPassword(cred.password);
    setError('');
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <AuthAside />

      <div className="flex items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-[380px]">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-navy-900">
              <ShieldCheck className="h-5 w-5 text-brand-400" strokeWidth={2} />
            </span>
            <div>
              <p className="text-[15px] font-semibold leading-tight text-ink-900">LM-GUARD</p>
              <p className="text-[11px] leading-tight text-ink-500">AI-Assisted Legal Metrology</p>
            </div>
          </div>

          <span className="badge mb-3 border-brand-100 bg-brand-50 text-brand-700">
            <Lock className="h-3 w-3" strokeWidth={2.5} />
            Administrator access
          </span>
          <h1 className="text-[24px] font-semibold tracking-tight text-ink-900">Administrator sign in</h1>
          <p className="mt-1.5 text-13 text-ink-500">
            The compliance console is restricted to authorised Legal Metrology administrators.
          </p>

          {error && (
            <div className="mt-5 flex items-start gap-2.5 rounded-lg border border-danger-100 bg-danger-50 px-3.5 py-3">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" strokeWidth={2} />
              <p className="text-13 text-danger-700">{error}</p>
            </div>
          )}

          <form onSubmit={submit} className="mt-6 space-y-4">
            <div>
              <label className="field-label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="administrator@lmguard.gov.in"
                className="input input-lg"
                required
              />
            </div>

            <div>
              <label className="field-label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="input input-lg"
                required
              />
            </div>

            <button type="submit" disabled={loading} className="btn-primary btn-lg w-full">
              {loading ? <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} /> : null}
              Sign in
              {!loading && <ArrowRight className="h-4 w-4" strokeWidth={2} />}
            </button>
          </form>

          <div className="mt-6 flex items-start gap-2.5 rounded-lg border border-line bg-canvas/70 px-4 py-3">
            <Smartphone className="mt-0.5 h-3.5 w-3.5 shrink-0 text-ink-400" strokeWidth={1.9} />
            <p className="text-xs leading-relaxed text-ink-500">
              Inspecting officers do not sign in here. Field inspections are carried out in the LM-GUARD field
              application and reach this console once submitted.
            </p>
          </div>

          <div className="mt-4 rounded-lg border border-line bg-canvas/70 px-4 py-3.5">
            <p className="eyebrow mb-2.5">Demonstration account</p>
            <ul className="space-y-2">
              {ADMIN_CREDENTIALS.map((c) => (
                <li key={c.email} className="flex items-center justify-between gap-3">
                  <span className="min-w-0">
                    <span className="block text-13 text-ink-800">{c.label}</span>
                    <span className="block font-mono text-[11px] text-ink-400">
                      {c.email} · {c.password}
                    </span>
                  </span>
                  <button type="button" onClick={() => useDemo(c)} className="btn-secondary btn-sm">
                    Use
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <p className="mt-6 text-center text-13 text-ink-500">
            Need console access?{' '}
            <Link to="/register" className="link-quiet">
              Request an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
